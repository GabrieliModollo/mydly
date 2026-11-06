# Frontend

App web e mobile do MyDly em **Angular 22 + Ionic 9 + Capacitor 8** ([ADR 0004](../docs/adr/0004-stack-tecnologica.md)).

Por enquanto o app tem uma tela, a **Hoje**. Ela tem os cartões "Gasto hoje" e "Hábitos cumpridos", um campo de lançamento no estilo de chat e a lista do que foi registrado no dia, com chips que mostram o que o MyDly entendeu de cada frase. O visual segue o [`docs/design.md`](../docs/design.md).

## Rodando

Requisitos: Node 24 e o backend rodando em `localhost:8080`, que pode ser o `docker compose up -d` na raiz.

```bash
npm ci
npm start          # http://localhost:4200, com /api encaminhado ao backend (proxy.conf.json)
```

Para rodar tudo em containers, use `docker compose up --build` na raiz e abra <http://localhost:8100>. O nginx serve o app e encaminha `/api` ao backend, então a API fica na mesma origem e não precisa de CORS.

## Comandos

| Comando | O que faz |
| --- | --- |
| `npm start` | Servidor de desenvolvimento com proxy para a API |
| `npm test -- --watch=false` | Testes unitários (Vitest) |
| `npm run lint` | ESLint, com as regras de acessibilidade dos templates |
| `npm run build` | Build de produção em `dist/mydly/browser` |

## Organização

```
src/app/
├── entries/                # tudo sobre lançamentos, sem componentes visuais
│   ├── entry.models.ts     # tipos que espelham a API (EntryResponse, EntryItemResponse)
│   ├── entries-api.ts      # chamadas HTTP
│   ├── entries-store.ts    # estado com signals: envio otimista, falha e reenvio
│   ├── entry-format.ts     # texto dos chips e resumo do dia (funções puras)
│   └── random-id.ts        # UUID do lançamento, com alternativa fora de HTTPS
└── today/                  # tela "Hoje" e o formato da data do cabeçalho
src/theme/variables.css     # tokens do design.md: cores, fontes, tema escuro
```

## Decisões

- **Id e data gerados no app** ([ADR 0006](../docs/adr/0006-id-do-cliente-e-occurred-at.md)). O lançamento aparece na lista assim que é enviado, como "Enviando…". Se a rede falhar, o botão "Tentar de novo" reenvia com o mesmo id, e a API não duplica. Recusas da API (4xx) não oferecem reenvio, porque o resultado seria o mesmo.
- **Sem zone.js.** O Angular 22 detecta mudanças por signals. Por isso o envio lê o texto direto do campo, sem depender do estado do botão. Um Enter logo depois da digitação seria ignorado se dependesse de o `[disabled]` do botão já ter sido atualizado.
- **Fontes servidas pelo próprio app** (Space Grotesk nos títulos e números, Inter no texto, via `@fontsource-variable`). Elas funcionam offline e não enviam o IP de quem usa o app ao Google Fonts (LGPD).
- **Visual do [`design.md`](../docs/design.md), no modo `md` em todas as plataformas.** Saída é laranja e entrada é azul, nunca vermelho e verde, o par mais difícil para quem tem daltonismo. O rótulo sempre diz o tipo por escrito. O tema escuro deriva do claro com a mesma lógica de cor. Todo texto passa o contraste AA do WCAG nos dois temas, e a borda do campo passa os 3:1 exigidos para controles.
- **Lançamento que precisa de confirmação mostra só esse chip.** "Saída · valor a definir" só repetiria a dúvida. O link "Quanto foi?" entra com a confirmação (etapa 3 do [backlog](../docs/backlog.md)).
- **Os cartões de resumo são calculados da lista do dia**, só com o que a API já interpretou. "Gasto hoje" soma as saídas com valor, e "Hábitos cumpridos" conta as rotinas.
- **Capacitor configurado, mas só para web.** Android e iOS entram numa etapa futura (`npx cap add android`).

## Próximos passos

- Fila offline: guardar os lançamentos no aparelho e sincronizar quando a rede voltar.
- Confirmação dos lançamentos "Precisa confirmar" ("quanto foi?").
