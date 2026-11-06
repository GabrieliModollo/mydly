# Parser de lançamentos

Como o MyDly interpreta um lançamento em texto livre usando só regras locais, sem LLM. A decisão de arquitetura está no [ADR 0003](adr/0003-parser-hibrido.md). A implementação fica em `backend/src/main/java/com/mydly/entries/internal/RuleBasedEntryParser.java`, e cada regra tem caso de teste em `RuleBasedEntryParserTests`.

## Contrato

`EntryParser.parse(String text)` devolve uma `List<ParsedEntry>`. Hoje a lista sempre tem um elemento. Ela já é lista porque frases compostas ("fui à academia e gastei 30 no açaí") vão gerar vários lançamentos numa próxima etapa.

| Campo | Significado |
| --- | --- |
| `type` | `FINANCE`, `ROUTINE` ou `UNKNOWN` |
| `direction` | `EXPENSE` (padrão) ou `INCOME`. Só existe em `FINANCE` |
| `amount` | Valor em reais (`BigDecimal`). Só em `FINANCE`, e pode faltar |
| `quantity` / `unit` | Quantidade em `ROUTINE`. A unidade é normalizada (`L`, `mL`, `h`, `min`, `km`, `copo`) ou fica nula quando é uma contagem ("30 flexões") |
| `description` | Texto original sem espaços extras, ou nulo quando a frase é só o valor ("50 reais") |
| `confidence` | De 0 a 1 |

## Limiar de confirmação

**Abaixo de 0,50** (`ParsedEntry.ASK_THRESHOLD`), o lançamento não é aceito direto. Ele vai para o fallback: o LLM, quando estiver configurado, e depois a pergunta ao usuário ("Isso foi um gasto? Quanto foi?"). Use `ParsedEntry.needsConfirmation()` para checar.

## Regras

Antes das regras, o texto é normalizado: os espaços extras somem e tudo fica em minúsculas e sem acentos. Horários do relógio ("às 6h", "das 7 horas", "pelas 22h30") também são removidos, porque dizem quando algo aconteceu e não quanto durou.

As regras são avaliadas em ordem, e a primeira que casa decide:

| # | Regra | Exemplo | Resultado | Confiança |
| --- | --- | --- | --- | --- |
| 1 | Texto vazio, sem letras nem números ou só com horário | `""`, `?!`, `às 6h` | `UNKNOWN` | 0,00 |
| 2 | Número com marcador monetário (`R$`, `reais`, `real`, `conto(s)`) | `R$50`, `1 real`, `Recebi 100 reais` | `FINANCE` com valor | 0,95 |
| 3 | Número com unidade de medida | `2 litros`, `500ml`, `2h`, `30min`, `5,5km`, `3 copos` | `ROUTINE` com quantidade e unidade | 0,90 |
| 4 | Número sozinho | `50`, `1.250,90` | `FINANCE` com valor | 0,80 |
| 5a | Número com sinal de finança, seguido de preposição, pontuação ou fim da frase | `Paguei 12,50 no estacionamento`, `Caiu o salário de 3.000` | `FINANCE` com valor | 0,70 |
| 5b | Número com sinal de finança, seguido de substantivo (é quantidade, não valor) | `Comprei 2 pães`, `Paguei 3 boletos` | `FINANCE` sem valor | 0,40 |
| 6 | Número seguido de substantivo, sem sinal de finança | `Fiz 30 flexões` | `ROUTINE` com contagem (sem unidade) | 0,50 |
| 7 | Outro número sem contexto | `Acordei as 6` | `UNKNOWN` | 0,30 |
| 8 | Sinal de finança ou palavra de dinheiro, sem número | `Comprei pão`, `cinquenta reais` | `FINANCE` sem valor | 0,40 |
| 9 | Frase sem número | `Dormi bem`, `Fui à academia` | `ROUTINE` | 0,60 |

**Sinais de finança.** Os de saída são gastei, gastou, gasto, paguei, pagou, comprei, comprou, custou e torrei. Os de entrada são recebi, recebeu, recebido, recebida, ganhei, salário, reembolso e reembolsado. A palavra "caiu" só conta como entrada quando a frase tem número, para "o celular caiu" não virar finança.

**Direção.** É `INCOME` quando aparece um sinal de entrada e `EXPENSE` em qualquer outro caso.

**Números.** O parser aceita o formato brasileiro (`1.250,90` vira 1250,90) e o ponto decimal (`2.5`). Um ponto seguido de exatamente três dígitos é separador de milhar (`1.250` vira 1250).

## Limitações conhecidas

São casos que ficam para o LLM ou para próximas etapas:

- **Frases compostas** ("fui à academia e gastei 30 no açaí") ainda viram um lançamento só. O teste está escrito e desativado.
- **Valores por extenso** ("cinquenta reais") viram finança sem valor e com confiança baixa.
- **Categorias** (farmácia, academia, hidratação) ainda não são inferidas.

## Privacidade

O parser não registra em log o texto nem os valores (LGPD).
