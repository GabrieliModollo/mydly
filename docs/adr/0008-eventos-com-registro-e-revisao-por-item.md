# ADR 0008 — Eventos entre módulos com registro de publicação, revisão por item e reabertura

- **Status:** Aceita
- **Data:** 2026-11-06
- **Complementa:** [ADR 0007](0007-parse-e-resolucao-separados.md)

## Contexto

Na etapa 5, os itens de lançamento passam a alimentar os módulos `finance` (transações) e `routines` (hábitos). Três decisões de produto, tomadas pela autora, definem o fluxo:

1. Um item com confiança de 0,50 para cima vale sem perguntar. Um item pendente vale quando o usuário confirma.
2. Como o parser pode errar com confiança alta, todo item aceito ganha um **"Não foi isso"** no app. O item sai dos números e o painel de confirmação abre para corrigir ou descartar. Um item descartado ganha **"Desfazer"**.
3. O catálogo de hábitos tem polaridade: em hábito ruim, a ação acontecer é deslize e não acontecer é conquista.

Até a etapa 4, `entries` não falava com ninguém. Agora é preciso:

- avisar `finance` e `routines` sem que eles leiam a tabela `entry` (ADR 0002);
- não perder o aviso se a aplicação cair entre gravar o lançamento e registrar a transação;
- desfazer um aviso (o "Não foi isso") sem que um evento antigo, entregue de novo ou fora de ordem, ressuscite o dado.

## Decisão

**Estado e revisão por item.** Cada item ganha, na coluna `item_state` (alinhada com o `parsed_json`, que continua intacto), um estado e uma revisão:

| Estado | Quando |
| --- | --- |
| `PENDING` | Confiança abaixo de 0,50, ou item reaberto |
| `ACCEPTED_AUTO` | O parser teve confiança suficiente |
| `ACCEPTED_BY_USER` | O usuário confirmou no painel |
| `DISMISSED` | O usuário disse que não era nada |

A revisão começa em 1 e cresce a cada mudança de estado. A reabertura de um item aceito pelo parser marca `parserRejected`, que fica como base para medir e melhorar o parser. O lançamento fica `NEEDS_INPUT` enquanto algum item estiver pendente e `CONFIRMED` quando nenhum estiver. O status `PENDING` deixou de existir; a migração V4 converteu os registros antigos.

**Reabertura.** A ação `REOPEN`, no mesmo endpoint de resolução e com o mesmo `If-Match`, vale para itens aceitos ou descartados. O item volta a `PENDING` e a resolução anterior é apagada. `CONFIRM_*` e `DISMISS` continuam exigindo item pendente (409). Reabrir um item pendente também é 409.

**Dois eventos**, publicados pela API pública de `entries`:

- `EntryItemAccepted(entryId, itemIndex, revision, occurredAt, item)`, quando o item passa a valer, já com a confirmação do usuário;
- `EntryItemRetracted(entryId, itemIndex, revision)`, quando um item que valia é reaberto. Reabrir um descartado não publica nada.

**Registro de publicação do Spring Modulith** (`spring-modulith-starter-jpa`, tabela `event_publication` criada pela migração V5). O evento é gravado na mesma transação do lançamento e marcado como concluído quando o consumidor termina. O que ficar incompleto é reenviado quando a aplicação sobe (`republish-outstanding-events-on-restart`). O evento concluído é apagado (`completion-mode: delete`), porque traz o texto e o valor do lançamento, que já estão em `entry` (LGPD).

**Consumidores síncronos depois do commit.** Os consumidores são `@TransactionalEventListener` com transação própria e rodam logo após o commit, ainda antes de a API responder. Assim, o app pede o resumo do dia logo depois e já vê o número novo. Se um consumidor falhar (banco ocupado, um deadlock), o lançamento continua gravado e o evento fica pendente no registro.

**Nova tentativa agendada e saúde.** Para um evento pendente não esperar o próximo reinício, uma tarefa agendada (`IncompleteEventsRetry`) reenvia a cada minuto os eventos incompletos com mais de um minuto (`mydly.events.retry-interval` e `retry-older-than`). O indicador `incompleteEvents` do `/actuator/health` fica `OUT_OF_SERVICE` quando algum evento está incompleto há mais de dez minutos (`mydly.events.stale-after`). Isso quer dizer que algum total pode estar errado e a nova tentativa não resolveu. O indicador só conta os eventos e nunca mostra o conteúdo.

**Consumidores idempotentes pela revisão, com lápides.** `finance_transaction` e `habit_log` têm uma linha por `(entry_id, item_index)` com `revision` e `active`. A gravação é um único `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE revision < EXCLUDED.revision`:

- uma aceitação com revisão maior grava os dados e ativa a linha;
- uma retirada com revisão maior desativa a linha, e sem linha cria uma lápide;
- revisão menor ou igual não muda nada;
- nunca há `DELETE`. Assim, uma aceitação antiga que chega depois da retirada não ressuscita o item.

Todas as consultas filtram `active`. Um item aceito que deixou de ser do tipo do módulo (era gasto e foi confirmado como rotina) também vira lápide naquele módulo.

**Hábitos e polaridade em `routines`.** O catálogo fixo (`habit`, com palavras-chave e polaridade `GOOD`/`BAD`) e a busca do hábito ficam no módulo `routines`. O parser só diz se a ação aconteceu (`DONE`/`MISSED`), e `routines` traduz: bom e `DONE` é cumprido, bom e `MISSED` é não cumprido, ruim e `DONE` é deslize, ruim e `MISSED` é conquista. Rotina fora do catálogo fica registrada como "outros", fora dos indicadores.

**Backfill.** Na primeira subida depois da migração, uma tarefa publica os itens aceitos de todos os lançamentos existentes e se marca como feita em `entry_one_time_task`, na mesma transação. Repetir é seguro pela regra da revisão.

## Consequências

- **Positivas:** nenhum aviso se perde, os módulos não se conhecem por tabela, e o "Não foi isso" desfaz o dado sem risco de ressurreição. Os itens aceitos pelo parser e depois reabertos ficam marcados, e servem para medir os erros do parser.
- **Negativas:** a tabela `event_publication` e o JSON `item_state` são mais coisa para entender. Os consumidores precisam seguir a regra da revisão. Um consumidor novo que a esqueça pode aplicar eventos fora de ordem.
- **Riscos:** com consumidores síncronos, uma falha num deles deixa o resumo atrasado até a nova tentativa, em geral um ou dois minutos. Uma falha que se repete aparece na saúde da aplicação, em vez de passar em silêncio.
- **Reprocessamento:** `Entry.reinterpret` só troca os itens de lançamentos em que o usuário não mexeu. Quando troca, retira os itens antigos e aceita os novos com revisões maiores, para os consumidores aplicarem na ordem certa.
