package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.RoutineOutcome;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regras locais do parser (ADR 0003 e {@code docs/parser.md}).
 * Cada linha é: frase | tipo | direção | valor | quantidade | unidade | confiança.
 *
 * <p>A confiança é verificada por faixa, para os testes não quebrarem a cada ajuste fino dos pesos:
 * ALTA (≥ 0,85), MEDIA (0,5 a 0,85) e BAIXA (&lt; 0,5, o app pergunta ao usuário).
 */
class RuleBasedEntryParserTests {

    private final RuleBasedEntryParser parser = new RuleBasedEntryParser();

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Gastei 50 reais comprando energético na farmácia | FINANCE | EXPENSE | 50      |   |   | ALTA
            50 reais                                         | FINANCE | EXPENSE | 50      |   |   | ALTA
            50                                               | FINANCE | EXPENSE | 50      |   |   | MEDIA
            R$ 1.250,90                                      | FINANCE | EXPENSE | 1250.90 |   |   | ALTA
            Fui à academia                                   | ROUTINE |         |         |   |   | MEDIA
            Estudei durante 2 horas                          | ROUTINE |         |         | 2 | h | ALTA
            Dormi bem                                        | ROUTINE |         |         |   |   | MEDIA
            Bebi 2 litros de água                            | ROUTINE |         |         | 2 | L | ALTA
            Segui a dieta                                    | ROUTINE |         |         |   |   | MEDIA
            ''                                               | UNKNOWN |         |         |   |   | BAIXA
            """)
    void interpretaFrasesDeExemplo(String text, EntryType type, Direction direction, BigDecimal amount,
                                   BigDecimal quantity, String unit, Confidence confidence) {
        assertParsed(single(text), type, direction, amount, quantity, unit, confidence);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            # Marcador monetário colado no número, sem espaço.
            R$50                                     | FINANCE | EXPENSE | 50    |     |      | ALTA
            # Singular de "reais".
            1 real                                   | FINANCE | EXPENSE | 1     |     |      | ALTA
            # Gíria comum para dinheiro.
            30 conto                                 | FINANCE | EXPENSE | 30    |     |      | ALTA
            # Centavos com vírgula, sem marcador: número sozinho ainda é finança.
            50,90                                    | FINANCE | EXPENSE | 50.90 |     |      | MEDIA
            # Ponto com três dígitos é milhar no padrão brasileiro, não decimal.
            1.250                                    | FINANCE | EXPENSE | 1250  |     |      | MEDIA
            # Verbo de gasto e número seguido de preposição: o número é o valor.
            Paguei 12,50 no estacionamento           | FINANCE | EXPENSE | 12.50 |     |      | MEDIA
            Gastei 30 no açaí                        | FINANCE | EXPENSE | 30    |     |      | MEDIA
            # Verbo de gasto e número seguido de substantivo: o número é quantidade, falta o valor.
            Comprei 2 pães                           | FINANCE | EXPENSE |       |     |      | BAIXA
            Paguei 3 boletos                         | FINANCE | EXPENSE |       |     |      | BAIXA
            # Claramente um gasto, mas sem valor: confiança baixa para perguntar quanto foi.
            Comprei pão                              | FINANCE | EXPENSE |       |     |      | BAIXA
            # Valor por extenso fica para o LLM: finança sem valor, confiança baixa.
            cinquenta reais                          | FINANCE | EXPENSE |       |     |      | BAIXA
            # Marcador monetário vence a unidade quando os dois aparecem.
            Gastei 30 reais em 2 litros de açaí      | FINANCE | EXPENSE | 30    |     |      | ALTA
            # Entradas de dinheiro.
            Recebi 100 reais                         | FINANCE | INCOME  | 100   |     |      | ALTA
            Caiu o salário de 3.000                  | FINANCE | INCOME  | 3000  |     |      | MEDIA
            Reembolso de 50                          | FINANCE | INCOME  | 50    |     |      | MEDIA
            Pix recebido de 20                       | FINANCE | INCOME  | 20    |     |      | MEDIA
            Ganhei 200 no freela                     | FINANCE | INCOME  | 200   |     |      | MEDIA
            Caiu 100 na conta                        | FINANCE | INCOME  | 100   |     |      | MEDIA
            # "caiu" sem número não é dinheiro.
            O celular caiu                           | ROUTINE |         |       |     |      | MEDIA
            # Unidade colada no número e abreviada.
            Bebi 2l                                  | ROUTINE |         |       | 2   | L    | ALTA
            Bebi 500ml de água                       | ROUTINE |         |       | 500 | mL   | ALTA
            Dormi 7h                                 | ROUTINE |         |       | 7   | h    | ALTA
            Li 30min                                 | ROUTINE |         |       | 30  | min  | ALTA
            Corri 5,5km                              | ROUTINE |         |       | 5.5 | km   | ALTA
            Meditei 15 minutos                       | ROUTINE |         |       | 15  | min  | ALTA
            Bebi 3 copos de água                     | ROUTINE |         |       | 3   | copo | ALTA
            # Peso: unidade vence o sinal de entrada, "ganhei 2 kg" não é dinheiro.
            Pesei 72,5 kg                            | ROUTINE |         |       | 72.5| kg   | ALTA
            Ganhei 2 kg                              | ROUTINE |         |       | 2   | kg   | ALTA
            Perdi 1 quilo                            | ROUTINE |         |       | 1   | kg   | ALTA
            # Ponto decimal também é aceito quando não parece milhar.
            Bebi 2.5 litros                          | ROUTINE |         |       | 2.5 | L    | ALTA
            # Maiúsculas e acentos não mudam a interpretação.
            ESTUDEI 2 HORAS                          | ROUTINE |         |       | 2   | h    | ALTA
            # Horário do relógio não é duração: rotina sem quantidade.
            Acordei às 6h                            | ROUTINE |         |       |     |      | MEDIA
            Dormi às 23h30                           | ROUTINE |         |       |     |      | MEDIA
            # Número seguido de substantivo, sem sinal de dinheiro: contagem de rotina.
            Fiz 30 flexões                           | ROUTINE |         |       | 30  |      | MEDIA
            # "l" de "lanches" não pode ser lido como litro.
            Comi 2 lanches                           | ROUTINE |         |       | 2   |      | MEDIA
            # Número sem unidade, sem substantivo e sem sinal de dinheiro: ambíguo, pergunta.
            Acordei as 6                             | UNKNOWN |         |       |     |      | BAIXA
            # Só um horário, sem dizer o que aconteceu.
            às 6h                                    | UNKNOWN |         |       |     |      | BAIXA
            # Só espaços equivale a vazio.
            '   '                                    | UNKNOWN |         |       |     |      | BAIXA
            # Sem letras nem números não há o que interpretar.
            ?!                                       | UNKNOWN |         |       |     |      | BAIXA
            """)
    void trataCasosDeBorda(String text, EntryType type, Direction direction, BigDecimal amount,
                           BigDecimal quantity, String unit, Confidence confidence) {
        assertParsed(single(text), type, direction, amount, quantity, unit, confidence);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            # Negação ou verbo de falta na oração principal: hábito não cumprido.
            Não fui à academia                       | MISSED
            Não tomei água                           | MISSED
            Não bebi água                            | MISSED
            Nao bebi agua                            | MISSED
            Faltei na academia                       | MISSED
            Pulei o treino                           | MISSED
            Esqueci de beber água                    | MISSED
            Nem estudei hoje                         | MISSED
            Nunca medito                             | MISSED
            Deixei de ir à academia                  | MISSED
            Não dormi bem                            | MISSED
            Dormi mal                                | MISSED
            Não segui a dieta                        | MISSED
            # A negação não precisa abrir a frase.
            Hoje não fui à academia                  | MISSED
            # Com quantidade, continua não cumprido.
            Não bebi 2 litros de água                | MISSED
            # Negação em outra oração fala de outra coisa.
            Bebi água, não refrigerante              | DONE
            Fui à academia, não faltei               | DONE
            Fui à academia mas não treinei perna     | DONE
            # Negação e verbo de falta juntos se anulam.
            Não faltei na academia                   | DONE
            Não dormi mal                            | DONE
            # "Pular corda" é exercício, não falta.
            Pulei corda 10 minutos                   | DONE
            # Sem negação, cumprido.
            Fui à academia                           | DONE
            Dormi bem                                | DONE
            """)
    void distingueHabitoCumpridoDeNaoCumprido(String text, RoutineOutcome expected) {
        ParsedEntry result = single(text);

        assertThat(result.type()).isEqualTo(EntryType.ROUTINE);
        assertThat(result.outcome()).isEqualTo(expected);
    }

    /**
     * Finança negada não vira gasto nem entrada, com ou sem valor. O parser não modela um dia
     * sem gastos, então a frase vai para o fallback (LLM ou pergunta ao usuário) com confiança baixa,
     * em vez de criar um gasto que não existiu.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Não gastei nada hoje", "Não gastei 50 reais", "Nem paguei a conta", "Não recebi o salário"})
    void financaNegadaNuncaViraLancamentoComValor(String text) {
        ParsedEntry result = single(text);

        assertThat(result.type()).isEqualTo(EntryType.UNKNOWN);
        assertThat(result.amount()).isNull();
        assertThat(result.needsConfirmation()).isTrue();
    }

    /**
     * Evitar um hábito ruim é cumprir a meta, mas para saber disso o parser precisa da polaridade
     * do hábito ({@code comer besteira} é ruim), que vem com o módulo routines (etapa 5 do backlog).
     */
    @Disabled("Depende da polaridade do hábito, etapa 5 do backlog")
    @Test
    void evitarHabitoRuimEHabitoCumprido() {
        assertThat(single("Não comi besteira").outcome()).isEqualTo(RoutineOutcome.DONE);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource(delimiter = '|', nullValues = "SEM", textBlock = """
            Gastei 50 reais comprando energético na farmácia | PHARMACY
            Paguei 30 na drogaria                            | PHARMACY
            Gastei 200 reais no mercado                      | GROCERIES
            Paguei 25 no uber                                | TRANSPORT
            Gastei 150 de gasolina                           | TRANSPORT
            Paguei 12,50 no estacionamento                   | TRANSPORT
            Paguei o aluguel de 1.500                        | HOUSING
            Gastei 120 na consulta                           | HEALTH
            Paguei 99 reais da academia                      | HEALTH
            Caiu o salário de 3.000                          | WORK
            Ganhei 200 no freela                             | WORK
            Gastei 40 no cinema                              | LEISURE
            Comprei pão                                      | FOOD
            Gastei 30 no açaí                                | FOOD
            Pedi ifood de 45 reais                           | FOOD
            # Sem palavra conhecida, quem escolhe é o usuário.
            50 reais                                         | SEM
            Gastei 10 reais                                  | SEM
            # "gastei" não pode casar com "gas" (Moradia).
            Gastei 5 reais                                   | SEM
            """)
    void sugereCategoriaParaFinancas(String text, Category expected) {
        ParsedEntry result = single(text);

        assertThat(result.type()).isEqualTo(EntryType.FINANCE);
        assertThat(result.category()).isEqualTo(expected);
    }

    @Test
    void unidadesDoParserSaoAsUnidadesAceitasPeloContrato() {
        for (String unit : new String[] {"2 litros", "500ml", "2h", "30min", "5km", "70kg", "3 copos"}) {
            assertThat(ParsedEntry.UNITS).contains(single("Bebi " + unit).unit());
        }
    }

    @ParameterizedTest
    @NullSource
    void textoNuloViraDesconhecidoSemLancarExcecao(String text) {
        ParsedEntry result = single(text);

        assertThat(result.type()).isEqualTo(EntryType.UNKNOWN);
        assertThat(result.confidence()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"50 reais", "R$ 1.250,90", "50", "  50  "})
    void fraseQueESoOValorFicaSemDescricao(String text) {
        assertThat(single(text).description()).isNull();
    }

    @Test
    void descricaoGuardaOTextoSemEspacosExtras() {
        assertThat(single("  Gastei 50 reais   na farmácia ").description())
                .isEqualTo("Gastei 50 reais na farmácia");
    }

    @Test
    void confiancaBaixaPedeConfirmacaoDoUsuario() {
        assertThat(single("Comprei pão").needsConfirmation()).isTrue();
        assertThat(single("Fiz 30 flexões").needsConfirmation()).isFalse();
    }

    @Test
    void numeroComVirgulaPreservaOsCentavos() {
        assertThat(RuleBasedEntryParser.toDecimal("1.250,90")).isEqualTo(new BigDecimal("1250.90"));
    }

    /** Frases compostas geram vários itens (ADR 0003, item 4). */
    @Test
    void separaFraseCompostaEmVariosLancamentos() {
        List<ParsedEntry> result = parser.parse("Fui à academia e gastei 30 no açaí");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(EntryType.ROUTINE);
        assertThat(result.get(0).description()).isEqualTo("Fui à academia");
        assertThat(result.get(1).type()).isEqualTo(EntryType.FINANCE);
        assertThat(result.get(1).amount()).isEqualByComparingTo("30");
        assertThat(result.get(1).category()).isEqualTo(Category.FOOD);
        assertThat(result.get(1).description()).isEqualTo("gastei 30 no açaí");
    }

    /**
     * Cada linha é: frase | tipos dos itens, na ordem. Só vira item a parte com âncora própria
     * (verbo de ação, ou número quando o item anterior já tem o seu); na dúvida, fica um item só.
     */
    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            # Cada parte com verbo próprio é um item.
            Fui à academia e gastei 30 no açaí                | ROUTINE FINANCE
            Bebi 2 litros de água, estudei 1 hora             | ROUTINE ROUTINE
            Dormi mal e tomei 2 energéticos                   | ROUTINE ROUTINE
            Recebi 100 reais e gastei 40 no mercado           | FINANCE FINANCE
            Fui à academia, bebi 2 litros de água e dormi bem | ROUTINE ROUTINE ROUTINE
            Fui à academia e depois comi pizza                | ROUTINE ROUTINE
            Fui à academia e também meditei                   | ROUTINE ROUTINE
            Fui ao mercado aí gastei 200 reais                | ROUTINE FINANCE
            Bebi 2 litros de água e não fui à academia        | ROUTINE ROUTINE
            # Parte só com número herda o verbo quando o item anterior já tem valor ou quantidade.
            Gastei 30 no açaí e 20 no uber                    | FINANCE FINANCE
            Paguei 25 no uber; 30 no estacionamento           | FINANCE FINANCE
            Gastei 30 no açaí, 20 no uber e 10 no pão         | FINANCE FINANCE FINANCE
            Fiz 30 flexões e 20 abdominais                    | ROUTINE ROUTINE
            Bebi 2 litros de água e 1 de suco                 | ROUTINE ROUTINE
            Bebi 2 litros de água e 500ml de suco             | ROUTINE ROUTINE
            Recebi 100 reais e 50 do freela                   | FINANCE FINANCE
            # Na dúvida, um item só: a parte completa a anterior.
            Comprei pão e leite por 12 reais                  | FINANCE
            Comprei 2 pães e 1 leite                          | FINANCE
            Arroz e feijão                                    | ROUTINE
            Ontem, fui à academia                             | ROUTINE
            # A negação em outra oração fala de outra coisa e não é item.
            Bebi água, não refrigerante                       | ROUTINE
            Fui à academia, não faltei                        | ROUTINE
            # "mas" separa a oração principal, mas não cria item.
            Fui à academia mas não treinei perna              | ROUTINE
            # A vírgula do decimal não separa.
            Paguei 12,50 no estacionamento                    | FINANCE
            # O "aí" de "açaí" não é separador.
            Gastei 30 reais em 2 litros de açaí               | FINANCE
            # Gasto negado não tem valor, então a parte seguinte não herda o verbo.
            Não gastei 30 no açaí e 20 no uber                | UNKNOWN
            # O "e" dentro de um número composto não separa.
            Gastei 30 e 50                                    | FINANCE
            Gastei 10 reais e 50 centavos                     | FINANCE
            Estudei 1 hora e 30 minutos                       | ROUTINE
            Estudei 1 hora e meia                             | ROUTINE
            Bebi 2 litros e meio                              | ROUTINE
            Gastei 30 e 50 no mercado e 20 no uber            | FINANCE FINANCE
            """)
    void divideFraseCompostaSoQuandoCadaParteTemAncora(String text, String types) {
        List<EntryType> expected = Arrays.stream(types.split(" ")).map(EntryType::valueOf).toList();

        assertThat(parser.parse(text)).extracting(ParsedEntry::type).containsExactlyElementsOf(expected);
    }

    /**
     * Número composto com {@code e}: reais e centavos, horas e minutos, {@code e meia} e {@code e meio}.
     * Cada linha é: frase | tipo | direção | valor | quantidade | unidade | confiança.
     */
    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Gastei 30 e 50                       | FINANCE | EXPENSE | 30.50 |      |     | MEDIA
            Gastei 30 e 50 no mercado            | FINANCE | EXPENSE | 30.50 |      |     | MEDIA
            Gastei 10 reais e 50 centavos        | FINANCE | EXPENSE | 10.50 |      |     | ALTA
            Paguei 2 reais e 5 centavos          | FINANCE | EXPENSE | 2.05  |      |     | ALTA
            Estudei 1 hora e 30 minutos          | ROUTINE |         |       | 1.5  | h   | ALTA
            Corri 1h e 15min                     | ROUTINE |         |       | 1.25 | h   | ALTA
            Estudei 1 hora e meia                | ROUTINE |         |       | 1.5  | h   | ALTA
            Bebi 2 litros e meio                 | ROUTINE |         |       | 2.5  | L   | ALTA
            Corri 5 quilômetros e meio           | ROUTINE |         |       | 5.5  | km  | ALTA
            """)
    void juntaNumeroCompostoComE(String text, EntryType type, Direction direction, BigDecimal amount,
                                 BigDecimal quantity, String unit, Confidence confidence) {
        ParsedEntry result = single(text);

        assertParsed(result, type, direction, amount, quantity, unit, confidence);
        assertThat(result.description()).as("a descrição guarda o texto original").isEqualTo(text);
    }

    @Test
    void numeroCompostoNaoImpedeDividirAsOutrasPartes() {
        List<ParsedEntry> result = parser.parse("Gastei 30 e 50 no mercado e 20 no uber");

        assertThat(result).extracting(ParsedEntry::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("30.50"), new BigDecimal("20"));
        assertThat(result).extracting(ParsedEntry::description)
                .containsExactly("Gastei 30 e 50 no mercado", "20 no uber");
    }

    @Test
    void parteSemVerboHerdaOGastoAnteriorComCategoriaPropria() {
        List<ParsedEntry> result = parser.parse("Gastei 30 no açaí e 20 no uber");

        assertThat(result).extracting(ParsedEntry::direction).containsExactly(Direction.EXPENSE, Direction.EXPENSE);
        assertThat(result).extracting(ParsedEntry::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("30"), new BigDecimal("20"));
        assertThat(result).extracting(ParsedEntry::category).containsExactly(Category.FOOD, Category.TRANSPORT);
        assertThat(result).extracting(ParsedEntry::description).containsExactly("Gastei 30 no açaí", "20 no uber");
        assertThat(result).noneMatch(ParsedEntry::needsConfirmation);
    }

    @Test
    void parteSemVerboHerdaAEntradaAnterior() {
        List<ParsedEntry> result = parser.parse("Recebi 100 reais e 50 do freela");

        assertThat(result).extracting(ParsedEntry::direction).containsExactly(Direction.INCOME, Direction.INCOME);
        assertThat(result.get(1).amount()).isEqualByComparingTo("50");
        assertThat(result.get(1).category()).isEqualTo(Category.WORK);
    }

    @Test
    void entradaEGastoNaMesmaFraseTemDirecoesProprias() {
        List<ParsedEntry> result = parser.parse("Recebi 100 reais e gastei 40 no mercado");

        assertThat(result).extracting(ParsedEntry::direction).containsExactly(Direction.INCOME, Direction.EXPENSE);
        assertThat(result.get(1).category()).isEqualTo(Category.GROCERIES);
    }

    @Test
    void parteSemUnidadeHerdaAUnidadeDaRotinaAnterior() {
        List<ParsedEntry> result = parser.parse("Bebi 2 litros de água e 1 de suco");

        assertThat(result.get(1).quantity()).isEqualByComparingTo("1");
        assertThat(result.get(1).unit()).isEqualTo("L");
        assertThat(result.get(1).outcome()).isEqualTo(RoutineOutcome.DONE);
        assertThat(result.get(1).description()).isEqualTo("1 de suco");
        assertThat(result.get(1).needsConfirmation()).isFalse();
    }

    @Test
    void negacaoValeSoNaSuaOracao() {
        List<ParsedEntry> result = parser.parse("Dormi mal e tomei 2 energéticos");

        assertThat(result).extracting(ParsedEntry::outcome).containsExactly(RoutineOutcome.MISSED, RoutineOutcome.DONE);
        assertThat(result.get(1).quantity()).isEqualByComparingTo("2");
        assertThat(parser.parse("Bebi 2 litros de água e não fui à academia"))
                .extracting(ParsedEntry::outcome).containsExactly(RoutineOutcome.DONE, RoutineOutcome.MISSED);
    }

    @Test
    void valorQueCompletaAParteAnteriorFicaNumItemSo() {
        ParsedEntry result = single("Comprei pão e leite por 12 reais");

        assertThat(result.amount()).isEqualByComparingTo("12");
        assertThat(result.description()).isEqualTo("Comprei pão e leite por 12 reais");
    }

    @Test
    void soOItemAmbiguoPedeConfirmacao() {
        List<ParsedEntry> result = parser.parse("Fui à academia e comprei pão");

        assertThat(result).extracting(ParsedEntry::type).containsExactly(EntryType.ROUTINE, EntryType.FINANCE);
        assertThat(result).extracting(ParsedEntry::needsConfirmation).containsExactly(false, true);
    }

    @Test
    void ateCincoItensPorLancamento() {
        assertThat(parser.parse("Fui à academia, bebi água, li, meditei e dormi bem"))
                .hasSize(RuleBasedEntryParser.MAX_ITEMS);
    }

    @Test
    void maisDeCincoItensViramUmItemDesconhecidoParaConfirmar() {
        String text = "Fui à academia, bebi água, li, meditei, corri e dormi bem";

        ParsedEntry result = single(text);

        assertThat(result.type()).isEqualTo(EntryType.UNKNOWN);
        assertThat(result.description()).isEqualTo(text);
        assertThat(result.needsConfirmation()).isTrue();
    }

    private ParsedEntry single(String text) {
        List<ParsedEntry> result = parser.parse(text);
        assertThat(result).as("lançamentos de \"%s\"", text).hasSize(1);
        return result.getFirst();
    }

    private static void assertParsed(ParsedEntry result, EntryType type, Direction direction, BigDecimal amount,
                                     BigDecimal quantity, String unit, Confidence confidence) {
        assertThat(result.type()).as("tipo").isEqualTo(type);
        assertThat(result.direction()).as("direção").isEqualTo(direction);
        if (amount == null) {
            assertThat(result.amount()).as("valor").isNull();
        } else {
            assertThat(result.amount()).as("valor").isEqualByComparingTo(amount);
        }
        if (quantity == null) {
            assertThat(result.quantity()).as("quantidade").isNull();
        } else {
            assertThat(result.quantity()).as("quantidade").isEqualByComparingTo(quantity);
        }
        assertThat(result.unit()).as("unidade").isEqualTo(unit);
        assertThat(result.outcome()).as("cumprido")
                .isEqualTo(type == EntryType.ROUTINE ? RoutineOutcome.DONE : null);
        if (type != EntryType.FINANCE) {
            assertThat(result.category()).as("categoria").isNull();
        }
        assertThat(Confidence.of(result.confidence())).as("faixa de confiança (%s)", result.confidence())
                .isEqualTo(confidence);
    }

    enum Confidence {
        ALTA, MEDIA, BAIXA;

        static Confidence of(double value) {
            if (value >= 0.85) {
                return ALTA;
            }
            return value >= ParsedEntry.ASK_THRESHOLD ? MEDIA : BAIXA;
        }
    }
}
