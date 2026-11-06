# Parser de lançamentos

Como o MyDly interpreta um lançamento em texto livre usando só regras locais, sem LLM. A decisão de arquitetura está no [ADR 0003](adr/0003-parser-hibrido.md). A implementação fica em `backend/src/main/java/com/mydly/entries/internal/RuleBasedEntryParser.java`, e cada regra tem caso de teste em `RuleBasedEntryParserTests`.

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

**Direção.** É `INCOME` quando aparece um sinal de entrada e `EXPENSE` em qualquer outro caso.

**Números.** O parser aceita o formato brasileiro (`1.250,90` vira 1250,90) e o ponto decimal (`2.5`). Um ponto seguido de exatamente três dígitos é separador de milhar (`1.250` vira 1250).

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
- **"mas" e "porém" não dividem.** Eles separam a oração principal para a negação (veja abaixo), mas "Fui à academia mas não treinei perna" é um item só.

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
- No app, o hábito não cumprido aparece como "Rotina · não cumprido" (cinza e riscado) e não conta em "Hábitos cumpridos".

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

## Limitações conhecidas

São casos que ficam para o LLM ou para próximas etapas:

- **Verbos fora do passado em primeira pessoa** ("vou à academia", "a gente comeu pizza") não são âncora, então uma frase composta com eles pode ficar com um item só. Hábitos conhecidos também viram âncora quando existirem, com o módulo `routines`.
- **Valores por extenso** ("cinquenta reais") viram finança sem valor e com confiança baixa.
- **Categorias de rotina** (academia, hidratação, sono) ainda não são inferidas; só as de finança.
- **Hábitos ruins evitados.** "Não comi besteira" vira `MISSED`, mas evitar um hábito ruim é cumprir a meta. Para acertar, o parser precisa da polaridade do hábito, que vem com o módulo `routines` (etapa 5 do [backlog](backlog.md)). O teste está escrito e desativado.
- **"Deixei de"** vira `MISSED` mesmo em "deixei de fumar", que é uma conquista. Isso também depende da polaridade.

## Privacidade

O parser não registra em log o texto nem os valores (LGPD).
