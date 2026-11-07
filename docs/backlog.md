# Backlog do MyDly

Lista priorizada do que falta construir. A ordem é a prioridade: cada etapa é pegada de cima para baixo, e só uma fica em andamento por vez.

Ao concluir uma etapa, mova-a para **Concluído** com o commit correspondente e atualize este arquivo no mesmo PR.

## Em andamento

| # | Etapa | Escopo | Notas |
| --- | --- | --- | --- |
| — | Nenhuma | A próxima é a etapa 7 | |

## Próximas

| # | Prioridade | Etapa | Escopo | Por que agora |
| --- | --- | --- | --- | --- |
| 7 | Média | Metas e preferências | Pedido da autora (2026-11-06). Dia de início da semana configurável (módulo `preferences`); metas por hábito do catálogo (quantidade por dia, como 2 L de água, ou vezes por semana, como academia 4x; em hábito ruim, um limite), versionadas com `valid_from`; limite de gasto por categoria (semanal ou mensal) com avisos em 80% e 100%; resumo e cartões comparando com a meta; aba "Metas" no app. Preferências globais até a etapa de usuário, depois migram para o perfil | Completa o resumo da semana, que já nasceu preparado (`WeekDefinition`, `score.target`) |
| 8 | Média | Offline-first | Fila local no app para lançamentos sem internet, sincronização com o id já gerado | Uso no celular, na rua |
| 9 | Média | Usuário e autenticação | Spring Security com JWT, coluna de usuário nos dados, usuário "demo" com seed | Necessário antes de publicar uma demo |
| 10 | Baixa | LLM como fallback | Lançamentos de confiança baixa interpretados pelo LLM; modo sem LLM continua funcionando | Valor por extenso, frases fora das regras |
| 11 | Baixa | Importação de extrato | OFX/CSV atrás da porta `TransactionSource`, reconciliação com lançamentos manuais | Primeira fonte externa |
| 12 | Baixa | Open Finance (sandbox) | Adaptador do agregador, só com dados fictícios | Demonstração da integração |
| 13 | Baixa | Apps Android e iOS | Build com Capacitor, notificações push | Depois que o fluxo web estiver redondo |

## Ideias soltas (não priorizadas)

- Horário do lançamento entendido no texto ("acordei às 6h" como horário de acordar).
- Gráfico de peso a partir de lançamentos com kg.

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
