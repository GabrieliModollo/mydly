# ADR 0013 — LLM de fallback com o Claude, só para o que as regras não entenderam

- **Status:** Aceita
- **Data:** 2026-11-07
- **Complementa:** [ADR 0003](0003-parser-hibrido.md)

## Contexto

O ADR 0003 decidiu um parser híbrido: regras locais primeiro, LLM quando a confiança for baixa e pergunta ao usuário quando ainda houver dúvida, com um modo sem LLM para quem clonar o projeto. Até a etapa 9, só as regras existiam. Frases como "gastei cinquenta reais na padaria" (valor por extenso) ou "caiu o pix do freela" ficavam abaixo do limiar de 0,50 e sempre viravam uma pergunta.

A etapa 10 liga o LLM. Era preciso decidir:

- o que vai para o LLM, e com que dados;
- que modelo e que API;
- como confiar na resposta;
- o que acontece quando o LLM falha, demora ou não existe.

## Decisão

### O que vai e quando

- **Só os itens que as regras não entenderam.** As regras continuam dividindo a frase ([frases compostas](../parser.md#frases-compostas)) e interpretando cada parte. Os itens abaixo do limiar vão para o LLM, todos numa chamada só, cada um com o seu trecho. Os itens que as regras entenderam não mudam, e lançamentos claros ("gastei 50 reais na farmácia") nem chegam ao LLM: a maioria continua sem rede e sem custo.
- **Só o texto do lançamento** sai da API: a frase inteira, como contexto, e os trechos. Nenhum id, nome de usuário ou outro dado que identifique a pessoa (LGPD). Nada é registrado em log: nem o texto, nem os valores, nem a resposta do modelo, só o tipo da falha.
- **Na hora do lançamento**, antes de gravar, fora da transação. O app já mostra "Enviando…" e, com a fila offline (ADR 0011), não perde nada se a API demorar.

### Modelo e API

- **Claude Opus 5.5 (`claude-opus-5-5`), pelo SDK oficial da Anthropic para Java** (`com.anthropic:anthropic-java`). O modelo fica em `mydly.llm.model` e pode ser trocado sem mudar código.
- **Esforço baixo** (`output_config.effort: low`): é uma classificação curta, e o lançamento espera por ela.
- **Saída estruturada com esquema JSON** (`output_config.format`): uma lista de leituras com `index`, `type`, `direction`, `amount`, `category`, `outcome`, `quantity`, `unit` e `clear`. Os tipos, as categorias e as unidades são enumerações, e os campos que não se aplicam vêm nulos. A frase e os trechos vão como JSON, separados da instrução, e o prompt diz que o texto da pessoa é dado, não instrução.
- **Tempo máximo de 15 segundos e uma nova tentativa.** Recusa (`stop_reason: refusal`), resposta cortada (`max_tokens`), resposta fora do esquema, erro de rede ou limite de uso: em todos os casos o parser fica com o que as regras entenderam, e o app pergunta ao usuário, como antes.
- **Sem o parâmetro `fallbacks`** (outro modelo quando este recusa): ele exige o endpoint beta, e aqui uma recusa já tem um caminho seguro, que é perguntar ao usuário.

### Confiança

- **A resposta do LLM não é confiável antes de validada.** Cada leitura passa pelas mesmas regras do `ParsedEntry` e por mais algumas: valor positivo, com até duas casas e que caiba na coluna; categoria, direção, resultado e unidade dentro das enumerações; unidade só com quantidade. Um valor inválido vira "valor a definir"; um campo fora do formato, ou o tipo `UNKNOWN`, mantém o item das regras. Índice fora da lista ou repetido é ignorado.
- **A descrição é sempre o trecho original**, nunca um texto escrito pelo LLM.
- **A confiança é decidida aqui, não pelo modelo.** Uma leitura que o modelo marcou como clara e completa (finança com valor, ou rotina) entra com 0,75: vale sem perguntar, abaixo das regras fortes (0,90 e 0,95). Uma leitura incerta ou sem valor entra com 0,45: o app continua perguntando, já com a sugestão do LLM (categoria, direção) no painel.

### Sem chave

- **`LLM_API_KEY` vazia desliga o LLM.** O parser fica só com as regras, e o app funciona igual à etapa 9. É o padrão no `.env.example` e em todos os testes.

## Consequências

- Valor por extenso, gírias e frases fora das regras passam a virar lançamentos sem pergunta, quando há chave. Sem chave, nada muda.
- O texto de um lançamento duvidoso vai para a API da Anthropic. Isso precisa constar na política de privacidade quando houver uma, e a chave é opcional justamente para quem não quiser.
- Cada lançamento duvidoso custa uma chamada, e o tempo de resposta do lançamento passa a depender da API nesses casos (até 15 segundos, depois as regras). Não há limite de chamadas por usuário; um teto entra junto com as contas de verdade.
- Os testes não usam rede: o cliente da Anthropic é simulado, e o `HybridEntryParser` é testado com um LLM de mentira. A qualidade das leituras do modelo em frases reais ainda não tem um conjunto de avaliação; ele fica como ideia no backlog.
- O item não guarda se veio das regras ou do LLM. Para reprocessar ou medir isso depois, um campo de origem no `parsed_json` fica como ideia no backlog.
