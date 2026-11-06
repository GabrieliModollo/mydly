# ADR 0007 — Interpretação do parser e resolução do usuário guardadas separadamente

- **Status:** Aceita. Complementada pelo [ADR 0008](0008-eventos-com-registro-e-revisao-por-item.md)
- **Data:** 2026-11-05

## Contexto

Lançamentos com confiança baixa ("Comprei pão", sem valor; "Acordei as 6", não entendido) ficam como `NEEDS_INPUT` até o usuário responder "Quanto foi?". A resposta precisa ser guardada, e havia duas opções:

1. **Sobrescrever** o `parsed_json` com a versão corrigida pelo usuário.
2. **Guardar à parte**, numa coluna própria, mantendo o que o parser entendeu.

O ADR 0003 prevê aprendizado por usuário (correções viram apelidos) e reprocessamento dos lançamentos quando o parser melhorar. As duas coisas dependem de saber **onde o parser errou**.

## Decisão

- O `parsed_json` guarda só o que o parser entendeu e **nunca é sobrescrito** pela resposta do usuário.
- A resposta vai na coluna `resolution` (JSONB, migração `V3`): uma lista alinhada com os itens, com a ação (`CONFIRM_FINANCE`, `CONFIRM_ROUTINE` ou `DISMISS`) e o item como o usuário confirmou. Posição vazia significa item ainda não resolvido.
- O **valor efetivo** de cada item, o que a API devolve e o que as estatísticas usam, é a resolução quando ela existe, senão o que o parser entendeu. A API marca cada item com `resolvedByUser` e `dismissed`.
- "Não foi um gasto" **descarta** o item (`DISMISS`). O texto original continua guardado, mas o item não conta em nada. Transformar em rotina geraria hábitos estranhos, como "comprei pão".
- Categorias começam como **lista fixa** (Alimentação, Mercado, Farmácia, Transporte, Moradia, Saúde, Lazer, Trabalho, Outros). O parser sugere uma pelas palavras da frase, e o usuário pode trocar. Categorias personalizadas entram com o módulo `finance`.
- A resolução usa **controle de concorrência otimista**: o lançamento tem uma `version`, exposta como `ETag`, e o `POST .../items/{index}/resolution` exige `If-Match`. Assim, o índice nunca aponta para um item que mudou desde a leitura (`412`). Um item já resolvido devolve `409`.
- Quando não sobra item pendente, o lançamento passa de `NEEDS_INPUT` para `PENDING`. O status `CONFIRMED` fica reservado para quando `finance` e `routines` consumirem o lançamento.

## Consequências

- Dá para medir a taxa de acerto do parser e listar as frases em que ele mais erra, comparando `parsed_json` com `resolution`.
- Reprocessar lançamentos com um parser novo não apaga o que o usuário confirmou.
- A leitura do lançamento fica um pouco mais complexa: o valor efetivo é calculado combinando as duas colunas, e quem lê o banco direto precisa saber disso.
- O app precisa guardar a versão de cada lançamento e tratar o `412`, recarregando o lançamento e pedindo a confirmação de novo.
