# ADR 0009 — Mensagens da semana por regras locais, com limiar contra o acaso

- **Status:** Aceita. Complementada pelo [ADR 0010](0010-metas-e-orcamentos-versionados.md)
- **Data:** 2026-11-06

## Contexto

A etapa 6 traz o resumo da semana, o diferencial do MyDly: hábitos dia a dia, gastos por categoria e mensagens no tom que o usuário escolhe (Gentil, Direto ou Bronca), inclusive o cruzamento entre rotina e dinheiro ("nos dias em que você dormiu mal, gastou mais"). Era preciso decidir:

- quem escreve as mensagens: regras locais ou um LLM;
- quando um cruzamento merece aparecer, já que com poucos dias uma diferença pode ser puro acaso;
- o que as mensagens podem dizer, já que o app registra sono, dieta e peso;
- onde guardar o tom, se ainda não há usuário (etapa 9 do backlog);
- como comparar com a semana anterior no meio da semana.

## Decisão

**Regras locais, sem LLM.** As mensagens saem de regras com limiar fixo e de textos prontos nos três tons (`InsightTemplates`). O app funciona sem chave de LLM, como pede o ADR 0003, e cada mensagem traz os números que a sustentam, para o app mostrar e para os testes conferirem.

| Mensagem | Quando aparece |
| --- | --- |
| Deslize recorrente | Um hábito ruim consumido em 3 dias ou mais |
| Gasto acima da semana anterior | Pelo menos 20% acima **nos mesmos dias decorridos** (segunda a quarta contra segunda a quarta), e só se a semana anterior teve gasto nesses dias |
| Sono e gastos | Gasto médio nos dias de sono ruim pelo menos 20% maior que nos outros dias, com **3 dias ou mais em cada grupo** |
| Sequência | Um hábito bom cumprido 3 dias seguidos ou mais |
| Resumo simples | A semana tem dados, mas nenhuma regra disparou |
| Boas-vindas | A semana não tem nenhum dado |

A prioridade segue a ordem da tabela. São no máximo três mensagens: uma principal (cartão escuro) e duas secundárias. O cruzamento de sono e gastos, quando não é a principal, tem cartão próprio no acento.

**Limiar contra o acaso.** Com menos de 3 dias em cada grupo, o cruzamento não aparece. É melhor nenhuma mensagem do que uma falsa. O limiar é simples de explicar e de testar. Um teste estatístico de verdade fica para quando houver semanas suficientes de dados.

**Regra fixa de conteúdo.** Em nenhum tom as mensagens falam de corpo, peso, aparência ou quantidade de comida. Falam só de comportamento (o que a pessoa fez ou deixou de fazer). A semana sem dados recebe boas-vindas, nunca bronca. Um teste varre todos os textos atrás de termos proibidos.

**Tom no aparelho.** Até existir usuário, o tom fica no `localStorage` do aparelho (padrão "Direto") e vai como parâmetro (`?tone=gentle|direct|scold`). Quando a autenticação chegar, ele passa para o perfil.

**Comparação justa no meio da semana.** Numa quarta-feira, comparar três dias com a semana anterior inteira faria a regra quase nunca disparar, e disparar errado depois de uma compra grande no começo da semana. Por isso a semana anterior entra só com os mesmos dias decorridos, e o texto diz até quando ("Gastos até quarta: R$ 300,00. Na semana passada até quarta: R$ 100,00"). A linha "Semana anterior" do app segue a mesma regra.

**Semana de 7 dias que começa num dia configurável** (`WeekDefinition.firstDayOfWeek`, `mydly.week.first-day-of-week`, segunda por padrão), no fuso `America/Sao_Paulo`. Nenhum código supõe a segunda: a API valida a data de início contra o dia configurado (`400` se não bater), e o app tira as iniciais e os nomes dos dias da própria data de início. Na etapa "Metas e preferências", o dia vira preferência do usuário. O placar de cada hábito já vem como `{ achieved, elapsedDays, target }`, com `target` nulo até as metas existirem. A semana atual é parcial (os dias que não chegaram aparecem como `FUTURE`), e o app navega para as anteriores. O módulo `insights` só consulta as APIs públicas de `finance` e `routines`.

## Consequências

- **Positivas:** as mensagens são previsíveis, testáveis e não dependem de rede nem de custo de LLM. Os números citados são sempre os reais.
- **Negativas:** os textos são fixos e podem soar repetitivos com o tempo. Cada regra nova precisa de três textos e de testes dentro e fora do limiar.
- **Próximos passos:** o LLM pode reescrever os textos no tom (etapa 10), desde que receba só os números, nunca dados que identifiquem o usuário, e passe pela mesma varredura de conteúdo.
