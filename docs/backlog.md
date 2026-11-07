# Backlog do MyDly

Lista priorizada do que falta construir. A ordem é a prioridade: cada etapa é pegada de cima para baixo, e só uma fica em andamento por vez.

Ao concluir uma etapa, mova-a para **Concluído** com o commit correspondente e atualize este arquivo no mesmo PR.

## Em andamento

| # | Etapa | Escopo | Notas |
| --- | --- | --- | --- |
| — | Nenhuma | A próxima é a etapa 12 | |

## Próximas

| # | Prioridade | Etapa | Escopo | Por que agora |
| --- | --- | --- | --- | --- |
| 12 | Baixa | Open Finance (sandbox) | Adaptador do agregador, só com dados fictícios | Demonstração da integração |
| 13 | Baixa | Apps Android e iOS | Build com Capacitor, notificações push | Depois que o fluxo web estiver redondo |

## Ideias soltas (não priorizadas)

- Horário do lançamento entendido no texto ("acordei às 6h" como horário de acordar).
- Gráfico de peso a partir de lançamentos com kg.
- Service worker para abrir o app web do zero sem internet (hoje a fila funciona com o app já aberto; no app nativo os arquivos já vêm no pacote).
- Confirmações ("Quanto foi?", "Não foi isso") também na fila offline, com tratamento de conflito.
- Cadastro de usuário pela API, troca e recuperação de senha.
- Refresh token curto com revogação, e limite de tentativas de login.
- Restaurar os dados da demonstração todo dia, para visitantes não herdarem o que outros escreveram.
- Fuso horário por usuário (hoje todo mundo usa America/Sao_Paulo).
- Conjunto de avaliação do LLM do parser com frases reais anonimizadas, para medir acertos antes de trocar modelo, esforço ou prompt.
- Origem de cada item (regras ou LLM) no `parsed_json`, para reprocessar e medir.
- Limite de chamadas ao LLM por usuário.
- Categoria das transações importadas pela descrição do banco (hoje ficam sem categoria).
- Guardar a revisão do item na ligação da reconciliação, para ignorar eventos antigos reenviados fora de ordem.

## Concluído

| Etapa | Commits |
| --- | --- |
| Esqueleto do monorepo, arquitetura e ADRs | `722cae7` |
| Backend Spring Boot modular, CI e Dependabot | `c6f4979`, `4d6e81a` |
| `CLAUDE.md` | `9e7a3f2` |
| Parser de lançamentos baseado em regras | `fba5964` |
| API de lançamentos com persistência e Swagger | `fa7ac85`, `0329479` |
| App v1: tela "Hoje" (listagem por dia, app Angular + Ionic conforme `design.md`, Docker, CI e Dependabot do front) | `ec31989`, `34f1b25`, `d810da4`, `ab4e69d` |
| Hábitos não cumpridos (`outcome` DONE/MISSED, finança negada, chip "não cumprido") | `9042a9c`, `489f7ee`, `c013cf3` |
| Confirmação de lançamentos (resolução à parte do parser, ETag/If-Match, categorias fixas, painel "Quanto foi?", ADR 0007) | `675dbf2`, `38fcaaf` e o commit de documentação seguinte |
| Frases compostas (divisão por âncora, herança do verbo, números compostos com "e", até 5 itens, sem reprocessar lançamentos com resolução) | `b04d996`, `f58dd2e` e o commit de documentação seguinte |
| Módulos `finance` e `routines` (estado e revisão por item, "Não foi isso", eventos com registro de publicação, catálogo de hábitos com polaridade, resumo do dia, backfill, nova tentativa e saúde, ADR 0008) | `9359a98`, `4a4ef3c`, `76c206b`, `fe91cb9`, `30a64f1`, `50710e3` e o commit de documentação seguinte |
| Resumo da semana (mensagens por regras nos três tons, cruzamento de sono e gastos com limiar, comparação nos mesmos dias, semana com início configurável, tela Resumo, ADR 0009) | `5ccbb38`, `79bc890` e o commit de documentação seguinte |
| Metas e preferências (módulo `preferences` com o dia de início da semana, metas de hábito por dia ou por semana e limite de hábito ruim, orçamentos semanais ou mensais com aviso em 80%, versões com `valid_from` avaliadas no dia de referência, conversão de unidades e "bebi 1 litro" como água, mensagens de meta e de orçamento, aba Metas, ADR 0010) | `3d0b932`, `094ba24`, `7a20d24`, `cd949b7`, `9be1161`, `06d3d81` e o commit de documentação seguinte |
| Offline-first (fila no `localStorage` com envio em ordem quando a rede volta, ao abrir o app e depois de 30 s, estado "Guardado no aparelho", aviso "Sem conexão", lançamento de outro dia com data, convivência entre abas, ADR 0011) | `07d6d63` e o commit de documentação seguinte |
| Usuário e autenticação (módulo `accounts`, login com JWT HS256 emitido pela API, usuário demo, `user_id` em todos os dados e eventos, isolamento entre usuários, tela de login, token no aparelho, fila offline por usuário e "Sair", ADR 0012) | `41b36b8`, `750ac35`, `71b5fb6` e o commit de documentação seguinte |
| LLM como fallback (`HybridEntryParser`, Claude pelo SDK oficial só para os itens abaixo do limiar, saída estruturada validada campo a campo, só o texto do lançamento, regras de volta em qualquer falha, desligado sem `LLM_API_KEY`, ADR 0013) | `7b79f3d` e o commit de documentação seguinte |
| Importação de extrato (porta `TransactionSource` com OFX 1.x e 2.x e CSV, id externo único por usuário, reconciliação com lançamentos manuais na importação e depois deles, "Não contar", números pela API `ExternalTransactions` de finance, tela Extrato, ADR 0014) | `a18b93b`, `03f3293` e o commit de documentação seguinte |
