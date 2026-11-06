package com.mydly.entries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParsedEntryTests {

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01})
    void rejeitaConfiancaForaDoIntervaloDeZeroAUm(double confidence) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParsedEntry.unknown(null, confidence));
    }

    @Test
    void aceitaQuantidadeSemUnidadeParaContagens() {
        assertThat(ParsedEntry.routine(BigDecimal.valueOf(30), null, "fiz 30 flexões", 0.5).quantity())
                .isEqualByComparingTo("30");
    }

    @Test
    void rejeitaUnidadeSemQuantidade() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParsedEntry.routine(null, "L", null, 0.9));
    }

    @Test
    void exigeDirecaoEmFinanca() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParsedEntry.finance(null, BigDecimal.TEN, null, 0.9));
    }

    @Test
    void rejeitaDirecaoForaDeFinanca() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParsedEntry(EntryType.ROUTINE, Direction.EXPENSE, null, null, null, null, 0.6));
    }

    @Test
    void rejeitaTipoNulo() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ParsedEntry(null, null, null, null, null, null, 0.5));
    }
}
