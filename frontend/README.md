# Frontend

App web e mobile do MyDly em **Angular 22 + Ionic 9 + Capacitor 8** ([ADR 0004](../docs/adr/0004-stack-tecnologica.md)).

O app tem três abas. A **Hoje** tem os cartões "Gasto hoje" e "Hábitos cumpridos" (com o progresso das metas diárias, como "Hidratação 1,5 / 2 L"), o alerta das categorias que chegaram a 80% do limite, um campo de lançamento no estilo de chat e a lista do que foi registrado no dia, com chips que mostram o que o MyDly entendeu de cada frase. A **Resumo** mostra a semana, com o placar contra a meta e a marca do limite nas barras de gasto. A **Metas** define as metas dos hábitos, os limites por categoria e o dia em que a semana começa. O visual segue o [`docs/design.md`](../docs/design.md).

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
├── entries/                # acesso à API e tipos, sem componentes visuais
│   ├── entry.models.ts     # tipos que espelham a API (EntryResponse, EntryItemResponse, resumo do dia)
│   ├── week.models.ts      # resumo da semana
│   ├── goal.models.ts      # metas, orçamentos e preferências
│   ├── entries-api.ts      # chamadas HTTP dos lançamentos e do resumo do dia
│   ├── weeks-api.ts        # resumo da semana
│   ├── goals-api.ts        # metas de hábito, orçamentos e preferências
│   ├── auth-api.ts         # login na API
│   ├── session.ts          # quem está logado e o token, guardados no aparelho
│   ├── auth.interceptor.ts # token em todo pedido à API; 401 leva ao login
│   ├── auth.guard.ts       # telas do app só com sessão; o login só sem ela
│   ├── entries-store.ts    # estado com signals: envio otimista, fila offline e reenvio
│   ├── outbox-storage.ts   # leitura e gravação da fila no localStorage
│   ├── connectivity.ts     # rede do aparelho (eventos online/offline e volta para a frente)
│   ├── imports-api.ts      # importação de extrato e as transações importadas
│   ├── import.models.ts    # tipos da importação
│   ├── entry-format.ts     # texto dos chips e resumo do dia (funções puras)
│   └── random-id.ts        # UUID do lançamento, com alternativa fora de HTTPS
├── login/                  # tela de login e a mensagem de erro (função pura)
├── today/                  # tela "Hoje" e o formato da data do cabeçalho
├── summary/                # tela "Resumo" e a formatação da semana
├── goals/                  # aba "Metas", os painéis de meta e de limite e a formatação (funções puras)
├── imports/                # tela "Extrato": importação de OFX ou CSV e a formatação (funções puras)
└── nav/                    # barra de navegação inferior (Hoje, Resumo, Metas)
src/theme/variables.css     # tokens do design.md: cores, fontes, tema escuro
```

## Decisões

- **Id e data gerados no app** ([ADR 0006](../docs/adr/0006-id-do-cliente-e-occurred-at.md)). O lançamento aparece na lista assim que é enviado, como "Enviando…". Reenviar usa o mesmo id, e a API não duplica. Recusas da API (4xx) não oferecem reenvio, porque o resultado seria o mesmo.
- **Fila offline no aparelho** ([ADR 0011](../docs/adr/0011-fila-offline-no-aparelho.md)). Sem rede, ou se o envio falhar por rede ou erro do servidor, o lançamento fica no `localStorage` como "Guardado no aparelho." e é enviado sozinho: quando a rede volta, quando o app volta para a frente, ao abrir o app e 30 segundos depois de uma falha. Vai um por vez, do mais antigo ao mais novo, e "Enviar agora" força a tentativa. Duas abas abertas não apagam a fila uma da outra. Só lançamentos novos entram na fila; confirmar um item ainda precisa de conexão.
- **Login com o token guardado no aparelho** ([ADR 0012](../docs/adr/0012-login-com-jwt-e-dados-por-usuario.md)). A sessão (token, validade e usuário) fica no `localStorage` para o app abrir logado e lançar offline. Um 401 da API encerra a sessão e leva ao login; o lançamento que recebeu o 401 continua guardado e sai depois do login. A fila offline é de um usuário por vez: entrar com outra pessoa descarta a fila anterior, e "Sair" (aba Metas) apaga a fila de quem sai, com confirmação se há algo não enviado.
- **Extrato pelo servidor** ([ADR 0014](../docs/adr/0014-importacao-de-extrato-e-reconciliacao.md)). O app só envia o arquivo (`multipart/form-data`) e mostra o resumo; a leitura do OFX ou CSV e a reconciliação acontecem na API. A importação precisa de internet: ela não entra na fila offline.
- **Sem zone.js.** O Angular 22 detecta mudanças por signals. Por isso o envio lê o texto direto do campo, sem depender do estado do botão. Um Enter logo depois da digitação seria ignorado se dependesse de o `[disabled]` do botão já ter sido atualizado.
- **Fontes servidas pelo próprio app** (Space Grotesk nos títulos e números, Inter no texto, via `@fontsource-variable`). Elas funcionam offline e não enviam o IP de quem usa o app ao Google Fonts (LGPD).
- **Visual do [`design.md`](../docs/design.md), no modo `md` em todas as plataformas.** Saída é laranja e entrada é azul, nunca vermelho e verde, o par mais difícil para quem tem daltonismo. O rótulo sempre diz o tipo por escrito. O tema escuro deriva do claro com a mesma lógica de cor. Todo texto passa o contraste AA do WCAG nos dois temas, e a borda do campo passa os 3:1 exigidos para controles.
- **Lançamento que precisa de confirmação mostra só esse chip.** "Saída · valor a definir" só repetiria a dúvida. O link "Quanto foi?" abre o painel de confirmação, onde o usuário informa o valor e a categoria ou descarta o item ([ADR 0007](../docs/adr/0007-parse-e-resolucao-separados.md)).
- **Os cartões de resumo vêm do resumo do dia da API** (`GET /api/v1/days/{data}/summary`), que lê os módulos de finanças e de hábitos. Assim, um item retirado com "Não foi isso" sai dos números, "Hábitos cumpridos" já aplica a polaridade (hábito ruim evitado conta) e o mesmo resumo traz o progresso das metas diárias e os alertas de orçamento. O app pede o resumo de novo depois de cada lançamento ou resolução, e ao voltar para a aba.
- **Metas e limites valem de hoje em diante, decididos no servidor** ([ADR 0010](../docs/adr/0010-metas-e-orcamentos-versionados.md)). O app não manda data ao salvar uma meta. Os tipos de meta e as unidades que cada hábito aceita vêm da API (`allowedKinds`, `allowedUnits`), e a recusa (400) aparece no painel com a mensagem dela. Depois de trocar o dia de início da semana, o Resumo volta a navegar a partir de `/weeks/current`, porque um início de semana antigo deixa de valer.
- **Progresso nunca só por cor.** Toda barra tem o valor por escrito e `role="progressbar"` com `aria-valuetext`; a marca do limite no Resumo tem a linha "Limite da semana: ..." logo abaixo.
- **Capacitor configurado, mas só para web.** Android e iOS entram numa etapa futura (`npx cap add android`).

