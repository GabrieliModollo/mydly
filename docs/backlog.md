# Backlog do MyDly

Lista priorizada do que falta construir. A ordem é a prioridade: cada etapa é pegada de cima para baixo, e só uma fica em andamento por vez.

Ao concluir uma etapa, mova-a para **Concluído** com o commit correspondente e atualize este arquivo no mesmo PR.

## Em andamento

| # | Etapa | Escopo | Notas |
| --- | --- | --- | --- |
| — | Nenhuma | A próxima é a etapa 14 | |

## Próximas

| # | Prioridade | Etapa | Escopo | Por que agora |
| --- | --- | --- | --- | --- |
| 14 | Alta | Demonstração segura | Dados fictícios da demo restaurados todo dia (com uma semana de exemplo, para o Resumo e as metas aparecerem cheios), limite de tentativas de login e limite de chamadas ao LLM por usuário | Pré-requisito para publicar: visitantes compartilham o usuário demo, e o login e o LLM ficam expostos |
| 15 | Alta | Publicar a demo | Backend, PostgreSQL e app web num provedor com HTTPS, segredos fora do repositório (JWT, LLM, Pluggy, Firebase), deploy pelo GitHub Actions e o endereço no README. O provedor é decisão da autora | O motivo das etapas 9 e 14; a etapa ainda não existia, só as ideias que dependem dela |
| 16 | Média | Integrações conferidas de verdade | Com as contas criadas pela autora: Open Finance contra o sandbox do Pluggy e push de ponta a ponta com um projeto Firebase (`google-services.json` no app, conta de serviço no backend); corrigir o que vier diferente da documentação | O código das etapas 12 e 13 só foi testado com o Pluggy e o Firebase simulados |
| 17 | Média | Qualidade do parser medida | Origem de cada item (regras ou LLM) no `parsed_json` e um conjunto de avaliação com frases reais anonimizadas, rodado no CI sem rede para as regras e sob demanda para o LLM | Medir acertos antes de trocar modelo, esforço ou prompt (ADR 0013) |
| 18 | Média | Extrato mais completo | Categoria das transações importadas pela descrição do banco, sincronização automática do Open Finance (agendada ou pelo webhook do Pluggy) com aviso de "reconecte o banco" fora da tela Extrato, e a revisão do item guardada na ligação da reconciliação | Transação importada sem categoria não aparece nos orçamentos dela; e a limitação conhecida do ADR 0014 |
| 19 | Média | Contas de verdade | Cadastro pela API, troca e recuperação de senha, refresh token curto com revogação e fuso horário por usuário | Sair da demo compartilhada para contas próprias (ADR 0012 deixou isso de fora) |
| 20 | Baixa | Offline completo | Service worker para abrir o app web sem internet e as confirmações ("Quanto foi?", "Não foi isso") também na fila offline, com tratamento de conflito | Fecha o que o ADR 0011 deixou de fora |
| 21 | Baixa | Apps prontos para a loja | Ícone e tela de abertura do MyDly, builds de release assinados e, num Mac, o push no iOS (SDK do Firebase para trocar o token do APNs, capacidade Push Notifications) | O que o ADR 0016 deixou para depois |
| 22 | Baixa | Rotina mais rica | Horário entendido no texto ("acordei às 6h" como horário de acordar) e gráfico de peso a partir de lançamentos com kg, mostrado só para quem pedir e fora das mensagens, que não falam de corpo nem de peso (ADR 0009) | Ideias de produto sem dependência das outras etapas |

## Ideias soltas (não priorizadas)

- Nenhuma no momento: as ideias anteriores viraram as etapas 14 a 22.

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
| Open Finance no sandbox (Pluggy, connect token ligado ao usuário, widget Pluggy Connect, só itens de sandbox, `/v2/transactions` pelo cursor, mesma importação e reconciliação do extrato, sincronizar e desconectar, ADR 0015) | `b92901c`, `2bc09c5` e o commit de documentação seguinte |
| Apps Android e iOS (projetos do Capacitor 8, API do build com `MYDLY_API_URL`, CORS para o app, HTTP só no debug, APK de debug compilado, push pelo FCM HTTP v1 com alerta de orçamento, lembrete do dia e resumo da semana, cartão Notificações, ADR 0016) | `2d7d613`, `85c3525`, `190ba8d`, `01df3c5` e `33ad162` |
