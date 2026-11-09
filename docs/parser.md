# Parser de lançamentos

Como o MyDly interpreta um lançamento em texto livre. As regras locais deste documento resolvem a maioria das frases sem rede; o que elas não entendem vai para um LLM, quando há chave, e depois para o usuário (veja [LLM de fallback](#llm-de-fallback)). A decisão de arquitetura está no [ADR 0003](adr/0003-parser-hibrido.md). As regras ficam em `backend/src/main/java/com/mydly/entries/internal/RuleBasedEntryParser.java`, e cada uma tem caso de teste em `RuleBasedEntryParserTests`.

## Contrato

`EntryParser.parse(String text)` devolve uma `List<ParsedEntry>`, com um item por coisa que aconteceu. Uma frase composta como "fui à academia e gastei 30 no açaí" gera dois itens (veja [Frases compostas](#frases-compostas)). A lista nunca é vazia e tem no máximo 5 itens.

| Campo | Significado |
| --- | --- |
| `type` | `FINANCE`, `ROUTINE` ou `UNKNOWN` |
| `direction` | `EXPENSE` (padrão) ou `INCOME`. Só existe em `FINANCE` |
| `outcome` | `DONE` (padrão) ou `MISSED`, se o hábito foi cumprido. Só existe em `ROUTINE`. Itens gravados antes do campo existir são lidos como `DONE` |
| `category` | Categoria sugerida para a finança (veja abaixo). Só existe em `FINANCE`, e pode faltar |
| `amount` | Valor em reais (`BigDecimal`). Só em `FINANCE`, e pode faltar |
| `quantity` / `unit` | Quantidade em `ROUTINE`. A unidade é normalizada (`L`, `mL`, `h`, `min`, `km`, `kg`, `copo`) ou fica nula quando é uma contagem ("30 flexões") |
| `description` | Trecho do texto original que gerou o item, sem espaços extras: a frase inteira quando há um item só, e a parte dele numa frase composta ("20 no uber"). Fica nulo quando a frase é só o valor ("50 reais") |
| `confidence` | De 0 a 1 |

## Limiar de confirmação

**Abaixo de 0,50** (`ParsedEntry.ASK_THRESHOLD`), o lançamento não é aceito direto. Ele vai para o fallback: o LLM, quando estiver configurado, e depois a pergunta ao usuário ("Isso foi um gasto? Quanto foi?"). Use `ParsedEntry.needsConfirmation()` para checar.

## Regras

Antes das regras, o texto é normalizado: os espaços extras somem e tudo fica em minúsculas e sem acentos. Horários do relógio ("às 6h", "das 7 horas", "pelas 22h30") também são removidos, porque dizem quando algo aconteceu e não quanto durou.

As regras são avaliadas em ordem, e a primeira que casa decide:

| # | Regra | Exemplo | Resultado | Confiança |
| --- | --- | --- | --- | --- |
| 1 | Texto vazio, sem letras nem números ou só com horário | `""`, `?!`, `às 6h` | `UNKNOWN` | 0,00 |
| 1b | Verbo de finança negado (`não`, `nem` ou `nunca` + gastei, paguei, comprei, recebi...) | `Não gastei nada hoje`, `Não gastei 50 reais` | `UNKNOWN` | 0,30 |
| 2 | Número com marcador monetário (`R$`, `reais`, `real`, `conto(s)`) | `R$50`, `1 real`, `Recebi 100 reais` | `FINANCE` com valor | 0,95 |
| 3 | Número com unidade de medida (vence os sinais de finança: `ganhei 2 kg` é peso) | `2 litros`, `500ml`, `2h`, `30min`, `5,5km`, `72,5 kg`, `3 copos` | `ROUTINE` com quantidade e unidade | 0,90 |
| 4 | Número sozinho | `50`, `1.250,90` | `FINANCE` com valor | 0,80 |
| 5a | Número com sinal de finança, seguido de preposição, pontuação ou fim da frase | `Paguei 12,50 no estacionamento`, `Caiu o salário de 3.000` | `FINANCE` com valor | 0,70 |
| 5b | Número com sinal de finança, seguido de substantivo (é quantidade, não valor) | `Comprei 2 pães`, `Paguei 3 boletos` | `FINANCE` sem valor | 0,40 |
| 6 | Número seguido de substantivo, sem sinal de finança | `Fiz 30 flexões` | `ROUTINE` com contagem (sem unidade) | 0,50 |
| 7 | Outro número sem contexto | `Acordei as 6` | `UNKNOWN` | 0,30 |
| 8 | Sinal de finança ou palavra de dinheiro, sem número | `Comprei pão`, `cinquenta reais` | `FINANCE` sem valor | 0,40 |
| 9 | Frase sem número | `Dormi bem`, `Fui à academia` | `ROUTINE` | 0,60 |

**Sinais de finança.** Os de saída são gastei, gastou, gasto, paguei, pagou, comprei, comprou, custou e torrei. Os de entrada são recebi, recebeu, recebido, recebida, ganhei, salário, reembolso e reembolsado. A palavra "caiu" só conta como entrada quando a frase tem número, para "o celular caiu" não virar finança.

**Direção.** Vale o primeiro sinal de finança da frase, que é o da oração principal: é `INCOME` quando ele é de entrada e `EXPENSE` quando é de saída ou quando não há nenhum. Em "Paguei o salário da diarista: 200 reais", "Gastei 30 reais no presente que ganhei" e "Paguei 50 reais de reembolso" o verbo de gasto vem antes, e é gasto; em "Recebi 50 reais de reembolso do que paguei", é entrada.

**Números.** O parser aceita o formato brasileiro (`1.250,90` vira 1250,90), o ponto decimal (`2.5`) e o formato americano com vírgula de milhar e ponto decimal (`1,000.50` vira 1000,50). Um ponto seguido de exatamente três dígitos é separador de milhar (`1.250` vira 1250).

**Valores que não podem ser gravados.** Um valor zero, com mais de duas casas decimais (`10,555 reais`) ou acima de R$ 9.999.999.999,99 (o que cabe na coluna do banco) é tratado como não dito: a finança fica sem valor, com o texto inteiro e confiança 0,40, e o app pergunta. O mesmo vale para quantidades acima de 999.999.999 ou com mais de três casas, que ficam como rotina sem quantidade. Um valor que não cabe faria o evento para `finance` falhar para sempre. A API recusa os mesmos valores numa confirmação (`CONFIRM_FINANCE` e `CONFIRM_ROUTINE`).

**Horários e intervalos.** "às 6h" e "pelas 22h30" são horários e somem antes das regras. Um intervalo vira a duração: "de 14h às 16h" é 2 h, "de 14h30 às 16h" é 1,5 h e "das 23h às 7h" passa da meia-noite e dá 8 h. Horas com minutos colados são horas decimais: "7h30" é 7,5 h, e não 7 h.

**Faixa de valor.** "entre 40 e 50" não diz quanto foi: a faixa some, a finança fica sem valor e o app pergunta. O "e" da faixa não divide a frase.

**Finança negada (regra 1b).** "Não gastei nada hoje" nunca vira um gasto nem uma entrada, com ou sem valor. O parser não modela um "dia sem gastos", então a frase vai para o fallback com confiança baixa. É melhor perguntar do que registrar um gasto que não existiu.

## Frases compostas

Uma frase pode contar mais de uma coisa: "Fui à academia e gastei 30 no açaí" vira uma rotina e um gasto. A regra de ouro é **na dúvida, não dividir**. Um gasto fantasma é pior do que uma pergunta a mais, então a frase só é dividida quando cada parte se sustenta sozinha.

**Divisão.** A frase é cortada em partes por vírgula, ponto e vírgula, "e", "depois", "também" e "aí". A vírgula entre dígitos é decimal e não corta ("12,50"). O corte é feito no texto com acentos, então "é" não é lido como "e" e o "aí" de "açaí" não é separador.

**Números compostos.** O "e" dentro de um número também não corta. Antes das regras, essas formas viram um número só (a descrição continua com o texto original):

| Forma | Exemplo | Vira |
| --- | --- | --- |
| Reais e centavos com a palavra "centavos" | `Gastei 10 reais e 50 centavos`, `2 reais e 5 centavos` | R$ 10,50, R$ 2,05 |
| Número, "e" e dois dígitos, sem "reais" no primeiro e sem substantivo depois | `Gastei 30 e 50`, `Gastei 30 e 50 no mercado` | R$ 30,50 |
| Horas e minutos | `Estudei 1 hora e 30 minutos`, `Corri 1h e 15min` | 1,5 h, 1,25 h |
| Unidade e "meia" ou "meio" | `Estudei 1 hora e meia`, `Bebi 2 litros e meio` | 1,5 h, 2,5 L |

"Fiz 30 e 20 abdominais" não é dinheiro, porque vem um substantivo depois. "Recebi 100 reais e 50 do freela" são duas entradas, porque o primeiro número já disse "reais" e o segundo não diz "centavos".

**Âncora.** Uma parte só vira item novo se tiver uma âncora própria. Sem âncora, ela volta a se juntar à parte anterior.

| A parte tem | Vira item novo quando | Exemplo | Itens |
| --- | --- | --- | --- |
| Verbo de ação ou sinal de finança | o item atual já tem alguma âncora | `Fui à academia e gastei 30 no açaí` | rotina + gasto |
| Número, sem verbo | o item atual já tem o seu valor ou a sua quantidade; aí a parte **herda o verbo** dele | `Gastei 30 no açaí e 20 no uber` | dois gastos (Alimentação e Transporte) |
| Nenhum dos dois | nunca | `Arroz e feijão`, `Bebi água, não refrigerante` | um item |

- **Verbo de ação** é um verbo no passado em primeira pessoa: termina em "-ei" (estudei, meditei, tomei) ou é um irregular comum (fui, fiz, bebi, comi, dormi, li...). Os sinais de finança são os da regra 2 em diante (gastei, paguei, recebi, salário...).
- **Verbo de falta sozinho não é âncora.** Em "Fui à academia, não faltei", o "não faltei" fala da academia, e a frase fica com um item.
- **O valor completa a parte anterior quando ela ainda não tem valor.** "Comprei pão e leite por 12 reais" é um gasto só, de R$ 12. Já "Comprei 2 pães e 1 leite" também é um item só, porque o gasto não tem valor e o "1 leite" é parte da mesma compra.
- **"mas" e "porém" só dividem quando o que vem depois é dinheiro.** "Não fui à academia mas gastei 30 no açaí" vira uma rotina não cumprida e um gasto em Alimentação. Já "Fui à academia mas não treinei perna" é um item só: o "mas" só separa a oração principal para a negação (veja abaixo).

**Herança.** A parte sem verbo é interpretada com o verbo do item anterior. Depois de um gasto ela é outro gasto, e depois de uma entrada é outra entrada ("Recebi 100 reais e 50 do freela"). Depois de uma rotina com quantidade, ela é outra rotina: com a própria unidade ("e 1 litro de suco"), com a unidade da anterior quando não diz a sua ("Bebi 2 litros de água e 1 de suco" dá 1 L, confiança 0,70), ou como contagem ("Fiz 30 flexões e 20 abdominais"). O cumprido ou não vem junto com o verbo herdado. Se o item anterior não tem valor nem quantidade (um gasto negado, por exemplo), não há o que herdar, e a parte se junta a ele.

**Cada item** é interpretado pelas regras acima só com o seu trecho: tipo, valor, categoria, direção, cumprido ou não e confiança são calculados por parte. Assim, em "Fui à academia e comprei pão", só o pão pede confirmação.

**Limite de 5 itens.** Uma frase com mais de 5 itens vira um único item `UNKNOWN` com confiança 0,30, que o usuário confirma ou descarta. Isso evita que um texto colado por engano vire dezenas de lançamentos.

**Reprocessamento.** Lançamento com resolução do usuário não é reprocessado (ADR 0007): as respostas são alinhadas aos itens pela posição, e uma interpretação nova pode ter outro número de itens. `Entry.reinterpret` só troca os itens de lançamentos sem resolução.

## Hábito cumprido ou não

Toda rotina tem `outcome`. Quem decide é a **oração principal** do item, o trecho antes do primeiro separador. Valem os separadores de item (vírgula, ponto e vírgula, "e", "depois", "também", "aí"), mais "mas" e "porém":

| Na oração principal | Resultado | Exemplos |
| --- | --- | --- |
| Negação: `não`, `nem`, `nunca` | `MISSED` | `Não fui à academia`, `Hoje não fui à academia`, `Nem estudei hoje`, `Não dormi bem` |
| Verbo de falta: `faltei`, `pulei`, `esqueci de`, `deixei de`, `dormi mal` | `MISSED` | `Faltei na academia`, `Pulei o treino`, `Esqueci de beber água`, `Dormi mal` |
| Negação **e** verbo de falta (um anula o outro) | `DONE` | `Não faltei na academia`, `Não dormi mal` |
| Nenhum dos dois | `DONE` | `Fui à academia`, `Bebi 2 litros de água` |

- A negação numa oração seguinte fala de outra coisa: `Bebi água, não refrigerante` e `Fui à academia, não faltei` são `DONE`.
- "Pulei corda" é exercício, não falta: `Pulei corda 10 minutos` é `DONE`.
- A quantidade é mantida: `Não bebi 2 litros de água` é `MISSED` com 2 L.
- O parser só diz se a ação aconteceu. Se isso é bom ou ruim depende do hábito, e quem decide é o módulo `routines`, pela polaridade do catálogo ([ADR 0008](adr/0008-eventos-com-registro-e-revisao-por-item.md)). "Não comi besteira" e "Deixei de fumar" saem do parser como `MISSED`, e `routines` os conta como hábito ruim evitado, ou seja, cumprido.

## Categoria sugerida

Toda finança ganha uma categoria sugerida pelas palavras da frase, que o usuário pode trocar ao confirmar ([ADR 0007](adr/0007-parse-e-resolucao-separados.md)). A lista é fixa por enquanto, e categorias personalizadas entram com o módulo `finance`. A busca segue a ordem da tabela: **o lugar vem antes do produto**, então "energético na farmácia" fica em Farmácia.

| Categoria | Palavras |
| --- | --- |
| Farmácia (`PHARMACY`) | farmácia, drogaria, remédio(s), drogasil |
| Mercado (`GROCERIES`) | mercado, supermercado, feira, hortifrúti, atacadão, sacolão |
| Transporte (`TRANSPORT`) | uber, ônibus, metrô, gasolina, combustível, estacionamento, pedágio, táxi, passagem |
| Moradia (`HOUSING`) | aluguel, condomínio, luz, internet, IPTU, gás |
| Saúde (`HEALTH`) | médico, consulta, dentista, exame(s), academia, terapia, plano de saúde |
| Trabalho (`WORK`) | salário, freela, freelance, curso, coworking |
| Lazer (`LEISURE`) | cinema, show, bar, cerveja, netflix, spotify, viagem, ingresso, jogo |
| Alimentação (`FOOD`) | pão, padaria, açaí, lanche(s), lanchonete, ifood, restaurante, almoço, jantar, café, pizza, delivery, hambúrguer, energético, sorvete, comida |

Sem palavra conhecida, a categoria fica vazia, e quem escolhe é o usuário. Outros (`OTHER`) só aparece por escolha dele.

## LLM de fallback

O `HybridEntryParser` roda as regras e manda para o LLM só os itens abaixo do limiar, todos numa chamada, cada um com o seu trecho ([ADR 0013](adr/0013-llm-de-fallback-com-claude.md)). Os itens que as regras entenderam ficam como estão.

| O LLM devolve | Vira | Confiança |
| --- | --- | --- |
| Leitura clara e completa (finança com valor, ou rotina) | o item do LLM, com o trecho original como descrição | 0,75 (vale sem perguntar) |
| Leitura incerta, ou finança sem valor | o item do LLM, com a sugestão de categoria e direção | 0,45 (o app pergunta) |
| `UNKNOWN`, campo fora do formato ou índice inválido | o item das regras | a das regras |
| Nada (sem chave, falha, recusa, demora acima de 15 s) | o item das regras | a das regras |

Exemplos com o LLM ligado: "Gastei cinquenta reais na padaria" vira R$ 50,00 em Alimentação; "Fui à academia e comprei pão por cinco reais" manda para o LLM só "comprei pão por cinco reais". Valor negativo, com mais de duas casas ou grande demais é tratado como não dito. Só o texto do lançamento vai para o LLM, e nada é registrado em log.

## Limitações conhecidas

São casos que ficam para o LLM, quando há chave, ou para próximas etapas:

- **Verbos fora do passado em primeira pessoa** ("vou à academia", "a gente comeu pizza") não são âncora, então uma frase composta com eles pode ficar com um item só. Hábitos conhecidos também viram âncora quando existirem, com o módulo `routines`.
- **Valores por extenso** ("cinquenta reais") viram finança sem valor e com confiança baixa nas regras; com o LLM ligado, ele lê o valor.
- **Categorias de rotina** (academia, hidratação, sono) não são inferidas pelo parser; só as de finança. O hábito de cada rotina é decidido no módulo `routines`, pelas palavras-chave do catálogo, com uma regra a mais para a água: "bebi 1 litro", sem dizer o que foi bebido, é Hidratação; "bebi 1 litro de cerveja" não é ([arquitetura](arquitetura.md#eventos-entre-módulos)).

## Privacidade

O parser não registra em log o texto nem os valores (LGPD).
