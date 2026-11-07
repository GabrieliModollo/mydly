# ADR 0011 — Fila offline no aparelho, com envio em ordem e sem interpretação local

- **Status:** Aceita
- **Data:** 2026-11-07
- **Complementa:** [ADR 0006](0006-id-do-cliente-e-occurred-at.md)

## Contexto

A etapa 8 torna o app offline-first: a pessoa lança "gastei 30 no açaí" na rua, sem internet, e o lançamento não pode se perder. O ADR 0006 já preparou a API para isso: o id é gerado no app, o `POST /api/v1/entries` é idempotente por ele, e o `occurredAt` guarda quando a pessoa escreveu.

Até aqui, um envio que falhava ficava só na memória da tela, como "Não foi enviado" com "Tentar de novo". Fechar o app ou recarregar a página perdia o lançamento. Faltava decidir:

- onde guardar a fila no aparelho;
- quando tentar de novo e em que ordem;
- o que a tela mostra de um lançamento que ainda não foi interpretado;
- o que fazer com um lançamento guardado ontem e enviado hoje;
- como duas abas abertas convivem com a mesma fila.

## Decisão

- **Armazenamento: `localStorage`**, na chave `mydly.outbox.v1`, como uma lista de `{ id, text, occurredAt, rejected }`. Os lançamentos são pequenos (até 1000 caracteres) e poucos, então a API síncrona e simples basta. O IndexedDB e o plugin de armazenamento do Capacitor ficam para quando houver um motivo (volume, app nativo da etapa 13). Nenhuma dependência nova.
- **O que fica guardado:** todo lançamento que ainda não chegou ao servidor, inclusive o que estava sendo enviado quando o app fechou (ele volta como guardado) e o recusado pela API (4xx), que fica até o usuário tocar em "Remover", para não sumir em silêncio. Quando a fila esvazia, a chave é apagada.
- **Estados no app:** `sending`, `synced`, `queued` (guardado no aparelho) e `rejected`. O antigo `failed` virou `queued`: falha de rede ou do servidor (5xx) deixa de ser um erro para o usuário resolver e passa a ser uma espera.
- **Sem rede, nem tenta.** Se o navegador diz que está offline (`navigator.onLine` falso, que é confiável), o lançamento vai direto para a fila. Se diz que está online, o app tenta, e a falha decide: `navigator.onLine` verdadeiro não garante internet (Wi-Fi sem conexão, servidor fora do ar).
- **Quando a fila é enviada:** ao abrir o app (depois de listar o dia), no evento `online`, quando o app volta para a frente com rede (`visibilitychange`), depois de cada envio que deu certo e 30 segundos depois de uma falha. O botão "Enviar agora" força a tentativa de um lançamento.
- **Ordem e ritmo:** um lançamento por vez, do mais antigo ao mais novo. A primeira falha de rede ou do servidor para a fila, porque os outros falhariam igual. Uma recusa (4xx) não para a fila: ela é do lançamento, não da conexão.
- **Sem interpretação local.** O parser roda no servidor (ADR 0003). Um lançamento guardado aparece com o texto, o horário e "Guardado no aparelho.", sem chips, e não entra nos cartões do dia até ser enviado. Repetir as regras do parser no app criaria duas versões delas para manter.
- **Dia do lançamento:** vale o `occurredAt`, o momento em que a pessoa escreveu. Um lançamento guardado ontem aparece na lista de hoje enquanto espera, com a data junto do horário ("04/11, 17:00"), e sai da lista assim que é enviado, porque passa a pertencer a ontem no servidor.
- **Resposta perdida:** se o servidor gravou o lançamento mas a resposta não chegou, a listagem do dia o traz de volta, e ele sai da fila sem reenvio. Se ele for reenviado mesmo assim, a API devolve o registro existente (ADR 0006).
- **Duas abas:** cada aba lembra os ids que já teve. Ao gravar a fila, uma aba mantém os lançamentos guardados que não conhece, que são de outra aba, e o evento `storage` traz esses lançamentos para a lista dela. As duas podem enviar o mesmo lançamento, e a idempotência da API resolve.
- **Só lançamentos novos entram na fila.** Confirmar um item ("Quanto foi?") e "Não foi isso" continuam exigindo conexão: eles dependem da versão atual do lançamento no servidor (If-Match, ADR 0007), e enfileirá-los traria conflitos de edição que ainda não valem a complexidade.

## Consequências

- Um lançamento escrito sem internet sobrevive a fechar o app, a recarregar a página e a reiniciar o celular, e chega ao servidor com a data em que foi escrito.
- A tela mostra o estado de cada lançamento por escrito e com uma borda tracejada neutra, e um aviso "Sem conexão" com quantos lançamentos esperam no aparelho. Nada disso depende só de cor.
- Na web, a fila funciona com o app já aberto. Abrir o app do zero sem internet ainda exige um service worker, que fica como ideia no backlog; no app nativo (etapa 13), os arquivos do app já vêm no pacote.
- **LGPD:** o texto do lançamento fica no aparelho só até ser enviado, sem nada que identifique a pessoa além do próprio texto. Quando existir usuário (etapa 9), sair da conta precisa apagar a fila, e a fila precisa saber de qual usuário é cada lançamento.
- O `localStorage` pode ser apagado pelo navegador (aba anônima, limpeza de dados do site). Nesse caso, o que não foi enviado se perde, como em qualquer app web sem conta. Se o navegador bloquear o armazenamento, a fila vale enquanto o app está aberto.
