# ADR 0006 — Id gerado pelo cliente e data do lançamento (`occurredAt`)

- **Status:** Aceita
- **Data:** 2026-11-05

## Contexto

O app é offline-first: o lançamento é criado no celular, guardado localmente e sincronizado quando houver rede. Isso traz dois problemas para a API:

1. **Reenvio.** Se a rede cair depois de o servidor gravar o lançamento, mas antes de a resposta chegar, o app não sabe se o envio funcionou e tenta de novo. Se o servidor gerar o id, o reenvio cria um lançamento duplicado.
2. **Data.** O que importa para estatísticas e insights é quando a pessoa escreveu ("bebi 2 litros de água" na segunda), não quando o celular sincronizou (na quarta).

Mudar o formato do id ou o significado da data depois de existir dados é caro, por isso a decisão foi tomada antes do primeiro endpoint.

## Decisão

- O cliente **pode enviar o id** (UUID) do lançamento. O `POST /api/v1/entries` é **idempotente** por esse id. Lançamentos novos devolvem `201` com `Location`. Sem id, o servidor gera um.
- Quando o id já existe, a API compara o conteúdo:
  - **mesmo texto** (ignorando espaços extras) e, se o cliente informou, **mesma data**: é um reenvio, e a API devolve `200` com o registro original, sem reprocessar;
  - **qualquer diferença**: devolve `409 Conflict` (em ProblemDetail) e não altera nada. Isso indica um erro no app ou uma tentativa de edição, e a edição, quando existir, será um `PUT` próprio.
- O cliente **pode enviar `occurredAt`**, o momento em que o usuário escreveu. Sem ele, vale o momento do recebimento. A coluna `occurred_at` entra na migração `V2`, separada de `created_at`, que continua registrando quando o servidor gravou.
- Dois envios simultâneos com o mesmo id são resolvidos pela chave primária: o segundo é comparado com o registro gravado pelo primeiro, pelas mesmas regras.
- As datas são guardadas com precisão de microssegundos, que é a do PostgreSQL, para que a mesma data enviada de novo não pareça diferente.

## Consequências

- O app pode reenviar sem medo de duplicar, o que simplifica a fila de sincronização.
- As estatísticas usam `occurred_at`. O `created_at` serve para auditoria e depuração da sincronização.
- O servidor confia no relógio do celular para `occurredAt`. Datas absurdas (no futuro distante, por exemplo) podem ser validadas numa etapa posterior.
- Um bug no app que reaproveite ids aparece como `409` em vez de sumir com o lançamento em silêncio.
