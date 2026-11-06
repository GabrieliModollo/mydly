# Backlog do MyDly

Lista priorizada do que falta construir. A ordem é a prioridade: cada etapa é pegada de cima para baixo, e só uma fica em andamento por vez.

Ao concluir uma etapa, mova-a para **Concluído** com o commit correspondente e atualize este arquivo no mesmo PR.

## Em andamento

| # | Etapa | Escopo | Notas |
| --- | --- | --- | --- |
| 2 | Hábitos não cumpridos (**alta, bug**) | Campo `outcome` (DONE / MISSED) no `ParsedEntry`; negação antes do hábito vira MISSED ("Não fui à academia", "Esqueci de beber água", "Dormi mal"); "Não gastei nada" nunca vira gasto; chip "não cumprido" no app | Hoje uma negação conta como hábito cumprido e infla a sequência de dias |

## Próximas

| # | Prioridade | Etapa | Escopo | Por que agora |
| --- | --- | --- | --- | --- |
| 3 | Alta | Confirmação de lançamentos | Endpoint para completar um lançamento `NEEDS_INPUT` (valor e categoria) e tela "Quanto foi?" | Fecha o fluxo que o app já sinaliza |
| 4 | Alta | Frases compostas | "Fui à academia e gastei 30 no açaí" vira dois itens (o contrato já é uma lista) | Frase comum no uso real |
| 5 | Média | Módulos `finance` e `routines` | Consomem os lançamentos confirmados por eventos (registro de eventos do Spring Modulith); hábitos com polaridade, resolvendo "Não comi besteira" | Base para estatísticas |
| 6 | Média | Resumo da semana | Módulo `insights`: hábitos por dia, gastos por categoria, mensagem da semana com tom gentil / direto / bronca, cruzamento rotina × dinheiro | O diferencial do produto |
| 7 | Média | Offline-first | Fila local no app para lançamentos sem internet, sincronização com o id já gerado | Uso no celular, na rua |
| 8 | Média | Usuário e autenticação | Spring Security com JWT, coluna de usuário nos dados, usuário "demo" com seed | Necessário antes de publicar uma demo |
| 9 | Baixa | LLM como fallback | Lançamentos de confiança baixa interpretados pelo LLM; modo sem LLM continua funcionando | Valor por extenso, frases fora das regras |
| 10 | Baixa | Importação de extrato | OFX/CSV atrás da porta `TransactionSource`, reconciliação com lançamentos manuais | Primeira fonte externa |
| 11 | Baixa | Open Finance (sandbox) | Adaptador do agregador, só com dados fictícios | Demonstração da integração |
| 12 | Baixa | Apps Android e iOS | Build com Capacitor, notificações push | Depois que o fluxo web estiver redondo |

## Ideias soltas (não priorizadas)

- Horário do lançamento entendido no texto ("acordei às 6h" como horário de acordar).
- Metas configuráveis por hábito ("2 L de água por dia").
- Gráfico de peso a partir de lançamentos com kg.

## Concluído

| Etapa | Commits |
| --- | --- |
| Esqueleto do monorepo, arquitetura e ADRs | `722cae7` |
| Backend Spring Boot modular, CI e Dependabot | `c6f4979`, `4d6e81a` |
| `CLAUDE.md` | `9e7a3f2` |
| Parser de lançamentos baseado em regras | `fba5964` |
| API de lançamentos com persistência e Swagger | `fa7ac85`, `0329479` |
| App v1: tela "Hoje" (listagem por dia, app Angular + Ionic conforme `design.md`, Docker, CI e Dependabot do front) | `ec31989`, `34f1b25`, `d810da4` e o commit de documentação seguinte |
