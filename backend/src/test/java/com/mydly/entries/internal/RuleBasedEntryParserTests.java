package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.Direction;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import java.math.BigDecimal;
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

    /**
     * Frases compostas devem gerar vários lançamentos (ADR 0003, item 4). O contrato já devolve
     * lista, mas o parser ainda trata a frase inteira como um lançamento só; fica para a próxima etapa.
     */
    @Disabled("Frases compostas ficam para a próxima etapa")
    @Test
    void separaFraseCompostaEmVariosLancamentos() {
        List<ParsedEntry> result = parser.parse("Fui à academia e gastei 30 no açaí");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(EntryType.ROUTINE);
        assertThat(result.get(1).type()).isEqualTo(EntryType.FINANCE);
        assertThat(result.get(1).amount()).isEqualByComparingTo("30");
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
