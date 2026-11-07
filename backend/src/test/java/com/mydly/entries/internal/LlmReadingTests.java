package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.RoutineOutcome;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A leitura do LLM só vira item depois de validada campo a campo (ADR 0013). O que vier fora do
 * formato é descartado, e vale o que as regras entenderam.
 */
class LlmReadingTests {

    private static final String TRECHO = "gastei cinquenta reais na padaria";

    @Test
    void financaClaraEComValorEAceitaComOTrechoOriginal() {
        ParsedEntry item = reading("FINANCE", "EXPENSE", "50.00", "FOOD", null, null, null, true)
                .toParsedEntry(TRECHO).orElseThrow();

        assertThat(item.type()).isEqualTo(EntryType.FINANCE);
        assertThat(item.direction()).isEqualTo(Direction.EXPENSE);
        assertThat(item.amount()).isEqualByComparingTo("50");
        assertThat(item.category()).isEqualTo(Category.FOOD);
        assertThat(item.description()).isEqualTo(TRECHO);
        assertThat(item.confidence()).isEqualTo(LlmReading.CONFIDENCE_CLEAR);
        assertThat(item.needsConfirmation()).isFalse();
    }

    @ParameterizedTest(name = "valor {0} vira {1}")
    @CsvSource(nullValues = "SEM", value = {
            "10.5, 10.5",
            "1250.90, 1250.9",
            "0.01, 0.01",
            "1.005, SEM",
            "0, SEM",
            "-5, SEM",
            "10000000000, SEM",
            "SEM, SEM"})
    void valorInvalidoViraFinancaSemValorQuePerguntaAoUsuario(BigDecimal amount, BigDecimal expected) {
        ParsedEntry item = reading("FINANCE", "EXPENSE", amount == null ? null : amount.toPlainString(), null, null, null,
                null, true).toParsedEntry(TRECHO).orElseThrow();

        if (expected == null) {
            assertThat(item.amount()).isNull();
            assertThat(item.needsConfirmation()).isTrue();
        } else {
            assertThat(item.amount()).isEqualByComparingTo(expected);
            assertThat(item.needsConfirmation()).isFalse();
        }
    }

    @Test
    void leituraIncertaContinuaPerguntandoComASugestao() {
        ParsedEntry item = reading("FINANCE", "INCOME", "100", "WORK", null, null, null, false)
                .toParsedEntry("caiu o freela").orElseThrow();

        assertThat(item.direction()).isEqualTo(Direction.INCOME);
        assertThat(item.category()).isEqualTo(Category.WORK);
        assertThat(item.confidence()).isEqualTo(LlmReading.CONFIDENCE_UNSURE);
        assertThat(item.needsConfirmation()).isTrue();
    }

    @Test
    void financaSemDirecaoESaida() {
        assertThat(reading("FINANCE", null, "20", null, null, null, null, true).toParsedEntry(TRECHO).orElseThrow()
                .direction()).isEqualTo(Direction.EXPENSE);
    }

    @Test
    void rotinaComMedidaENaoCumprida() {
        ParsedEntry item = reading("ROUTINE", null, null, null, "MISSED", "1.50", "h", true)
                .toParsedEntry("não estudei a hora e meia de sempre").orElseThrow();

        assertThat(item.type()).isEqualTo(EntryType.ROUTINE);
        assertThat(item.outcome()).isEqualTo(RoutineOutcome.MISSED);
        assertThat(item.quantity()).isEqualByComparingTo("1.5");
        assertThat(item.unit()).isEqualTo("h");
        assertThat(item.needsConfirmation()).isFalse();
    }

    @ParameterizedTest(name = "quantidade {0} e unidade {1}")
    @CsvSource(nullValues = "SEM", value = {
            "30, SEM, 30, SEM",
            "2, litros, 2, SEM",
            "SEM, L, SEM, SEM",
            "0, L, SEM, SEM",
            "-1, min, SEM, SEM"})
    void rotinaSoGuardaQuantidadePositivaEUnidadeConhecida(BigDecimal quantity, String unit, BigDecimal expectedQuantity,
                                                          String expectedUnit) {
        ParsedEntry item = reading("ROUTINE", null, null, null, "DONE",
                quantity == null ? null : quantity.toPlainString(), unit, true).toParsedEntry("fiz flexões").orElseThrow();

        if (expectedQuantity == null) {
            assertThat(item.quantity()).isNull();
        } else {
            assertThat(item.quantity()).isEqualByComparingTo(expectedQuantity);
        }
        assertThat(item.unit()).isEqualTo(expectedUnit);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(nullValues = "SEM", value = {
            "UNKNOWN, SEM, SEM, SEM",
            "OUTRO, SEM, SEM, SEM",
            "SEM, SEM, SEM, SEM",
            "FINANCE, SAIDA, SEM, SEM",
            "FINANCE, EXPENSE, BEBIDA, SEM",
            "ROUTINE, SEM, SEM, TALVEZ"})
    void leituraDesconhecidaOuForaDoFormatoFicaComAsRegras(String type, String direction, String category,
                                                         String outcome) {
        assertThat(reading(type, direction, null, category, outcome, null, null, true).toParsedEntry(TRECHO)).isEmpty();
    }

    private static LlmReading reading(String type, String direction, String amount, String category, String outcome,
                                      String quantity, String unit, boolean clear) {
        return new LlmReading(0, type, direction, amount == null ? null : new BigDecimal(amount), category, outcome,
                quantity == null ? null : new BigDecimal(quantity), unit, clear);
    }
}
