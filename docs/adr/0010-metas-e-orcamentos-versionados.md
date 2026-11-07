# ADR 0010 — Metas e orçamentos versionados com `valid_from`, avaliados no dia de referência

- **Status:** Aceita
- **Data:** 2026-11-07
- **Complementa:** [ADR 0009](0009-mensagens-da-semana-por-regras.md)

## Contexto

A etapa 7 traz as metas e as preferências, pedidas pela autora em 2026-11-06:

- o dia em que a semana começa passa a ser escolhido pelo usuário, em vez da configuração fixa `mydly.week.first-day-of-week` da etapa 6;
- cada hábito do catálogo pode ter uma meta: quantidade por dia (2 L de água) ou dias por semana (academia 4 vezes). Num hábito ruim, a meta é um limite (delivery no máximo 2 dias);
- cada categoria de gasto pode ter um limite por semana ou por mês, com aviso em 80% e estouro acima de 100%;
- o resumo da semana e os cartões do dia passam a comparar com a meta.

Uma meta muda com o tempo. A pessoa começa com 2 L de água e passa para 3 L numa quinta-feira. Era preciso decidir:

- onde ficam as preferências, se ainda não há usuário (etapa 9);
- o que acontece com os dias e as semanas que já passaram quando a meta muda;
- qual meta vale numa semana em que ela mudou no meio;
- como somar quantidades em unidades diferentes ("1 litro" e "500 ml");
- quando uma meta da semana "não dá mais", e como as mensagens novas entram na prioridade.

## Decisão

**Módulo `preferences`, folha.** Um módulo novo guarda as preferências, globais até existir usuário: uma tabela de uma linha (`id = 1`, migração V8, semeada com a segunda), pronta para ganhar `user_id`. A API pública tem `WeekPreferences` (`firstDayOfWeek()`, `weekContaining(dia)`, `currentWeek()`) e `UserCalendar` (o fuso `America/Sao_Paulo` e o "hoje", sempre a partir do `Clock` injetável). O módulo não depende de nenhum outro; `finance`, `routines` e `insights` usam a API dele. A classe `WeekDefinition` e a propriedade `mydly.week.first-day-of-week` deixam de existir. A troca é feita por `PUT /api/v1/preferences`.

**A semana é sempre calculada na hora.** Nada guarda "a semana de um registro". Ao trocar o dia de início, as semanas passadas também passam a ser vistas na definição nova: um gasto de segunda que estava na semana de domingo passa para a semana que começa nessa segunda. O `GET /api/v1/weeks/{inicio}` continua validando a data contra o dia atual (400 se não bater), então um início guardado antes da troca deixa de valer. O app navega só pelas datas que a API devolve e, depois da troca, volta para `/weeks/current`.

**Versão em vez de sobrescrever.** Metas (`habit_goal`, migração V9) e orçamentos (`budget`, migração V10) são versões com `valid_from`. A versão vigente num dia `d` é a de maior `valid_from <= d`.

- Mudar a meta grava uma versão nova a partir de hoje, no fuso do usuário. O passado continua avaliado pela meta que valia nele.
- Remover grava uma versão "sem meta" (`kind = NONE` na meta; período e limite nulos no orçamento). Nada é apagado.
- Duas gravações no mesmo dia ficam numa versão só, a última: o `PUT` e o `DELETE` fazem upsert pela chave `(hábito ou categoria, valid_from)`. Isso também resolve duas gravações concorrentes sem bloqueio.
- `DELETE` sem meta vigente devolve 204 e não grava nada. Um `PUT` igual à meta vigente também não grava versão nova.

Sobrescrever seria mais simples, mas mudaria o passado: trocar a meta de água de 2 L para 3 L faria as semanas antigas parecerem piores do que foram, e tirar a meta apagaria o placar delas. Uma tabela de histórico à parte duplicaria a regra de "qual meta vale"; com a versão, a regra é uma só, para hoje e para o passado.

**Dia de referência.** A meta de uma semana é a versão vigente no **dia de referência**: o último dia da semana, ou hoje se a semana está em curso (numa semana que ainda não começou, o primeiro dia). O mesmo vale para o limite dos orçamentos.

- **Meta de dias por semana trocada no meio da semana** (4 dias até quarta, 2 dias a partir de quinta): na quarta, a semana mostra "x de 4"; da quinta em diante, e depois que ela termina, mostra "x de 2". Os dias cumpridos são os mesmos; só o alvo muda.
- **Meta diária trocada no meio da semana** (2 L até quarta, 3 L a partir de quinta): cada dia é avaliado pela meta daquele dia. Quem bebeu 2 L todos os dias cumpriu de segunda a quarta e não cumpriu de quinta a domingo: "3 de 7". O alvo são os dias da semana com meta diária vigente; uma meta criada na quarta dá 5 dias.
- **O tipo da meta da semana** também vem do dia de referência. Se ele tem meta diária, o placar é o da meta diária, e só os dias com meta diária entram no alvo. Se a meta foi removida antes dele, a semana fica sem meta.

**Medida dos hábitos e unidades.** O catálogo ganha a dimensão de medida (`habit.measure`): Hidratação é volume (`L`, `mL`); Estudo, Sono, Leitura e Academia são duração (`h`, `min`; na academia, o tempo de treino ou de corrida). Os outros não têm medida e só aceitam dias por semana. A meta diária só vale em hábito bom com medida e com unidade da mesma dimensão; o resto é 400. A soma do dia usa `BigDecimal` na unidade base da dimensão (mL ou min), em que toda conversão é exata. Só registros cumpridos com quantidade na mesma dimensão somam: "3 copos", "30 flexões" ou um registro sem número aparecem como registrados, mas não somam. O dia está cumprido quando a soma chega ao alvo, em comparação exata; a soma exibida é arredondada para baixo em duas casas, para nunca mostrar "2 / 2 L" com 1,999 L.

**Limite de hábito ruim.** Num hábito ruim, a meta de dias por semana é um limite de 0 a 7. O placar traz `limit` e `slips` (os dias de deslize) de forma explícita, e o app mostra "1 de no máximo 2"; `achieved` continua sendo os dias evitados, sem ganhar significado invertido.

**Orçamentos.** O limite é por categoria fixa, semanal (a semana das preferências) ou mensal (o mês civil em São Paulo: um gasto em 30/11 às 23:30 conta em novembro). O gasto é o das saídas ativas da categoria no período inteiro, mesmo antes de o limite ser criado. Os limiares são comparações exatas em `BigDecimal`: aviso quando `gasto * 100 >= 80 * limite`, estouro quando `gasto > limite`; exatamente 100% ainda é aviso. O percentual exibido é arredondado para baixo, para nunca mostrar 80% num gasto de 79,9%. No resumo de uma semana, o orçamento mensal vai do dia 1 do mês do dia de referência até esse dia.

**APIs públicas com o período explícito.** `HabitGoals` (`routines`) e `Budgets` (`finance`) recebem o dia ou o início e o fim da semana e do mês (`BudgetWindow`). Quem resolve "qual semana" é a camada web ou `insights`, pelas preferências. As regras ficam em funções puras (`GoalRules`, `BudgetProgress`), testadas sem banco.

**Mensagens novas, com a mesma regra de conteúdo do ADR 0009.**

| Mensagem | Quando aparece |
| --- | --- |
| Orçamento estourado (`BUDGET_OVER`) | Uma categoria acima de 100% do limite; entre várias, a de maior percentual |
| Meta em risco (`GOAL_AT_RISK`) | Só na semana em curso e só em meta de dias por semana. Hábito bom: os dias cumpridos mais os que restam (hoje, se ainda não foi cumprido, e os dias futuros) não chegam à meta; no último dia possível ainda não dispara. Hábito ruim: os deslizes já passaram do limite |
| Orçamento perto do limite (`BUDGET_NEAR`) | Uma categoria de 80% a 100% do limite |
| Meta cumprida (`GOAL_MET`) | Hábito bom: os dias cumpridos chegaram à meta, mesmo com a semana em curso. Hábito ruim: a semana encerrou com os deslizes dentro do limite |

A prioridade completa passa a ser: orçamento estourado, deslize recorrente, meta em risco, gasto acima da semana anterior, orçamento perto do limite, sono e gastos, meta cumprida, sequência, resumo simples e boas-vindas. Continuam no máximo três mensagens, cada tipo no máximo uma vez. Meta diária não gera mensagem de meta: o placar dela já aparece na linha do hábito. A varredura de termos proibidos cobre os textos novos e as variantes de limite.

**Onde aparece o quê.** O resumo do dia (`/days/{data}/summary`) traz o progresso das metas diárias e é o único lugar da API com o alerta de orçamento: a tela Hoje já pede esse resumo depois de cada lançamento, então o alerta aparece logo depois do gasto que o provocou, sem outra chamada. A aba Metas usa `GET /api/v1/goals/habits` e `GET /api/v1/budgets`, que trazem todos os hábitos e categorias com a versão vigente hoje. No resumo da semana, um hábito com meta aparece mesmo sem nenhum registro, para o placar "0 de 4" e a meta em risco ficarem visíveis.

## Consequências

- **Positivas:** editar a meta nunca reescreve o passado, e a mesma regra ("a versão vigente no dia") serve para hoje, para o histórico e para os testes. O upsert do mesmo dia resolve a gravação dupla sem bloqueio. As regras de conta e de limiar são puras e exatas.
- **Negativas:** as tabelas crescem com uma linha por mudança de meta, e quem lê o banco direto precisa aplicar a regra do `valid_from`. A regra do dia de referência precisa ser explicada no app: depois de uma troca no meio da semana, o alvo da semana muda de uma vez.
- **Recálculo das semanas passadas:** trocar o dia de início muda como as semanas antigas são agrupadas (gastos, placares e mensagens). É a decisão da autora, mais simples que guardar a definição de cada semana. Um link antigo para um início de semana passa a devolver 400.
- **Próximos passos:** na etapa de usuário, a tabela `preferences` ganha `user_id`, e `habit_goal` e `budget` ganham o usuário na chave. O fuso também pode virar preferência, sem mudar quem usa `UserCalendar`.
