package com.mydly.routines.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.routines.Measure;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Conversão das unidades das metas diárias (ADR 0010): tudo vai para a unidade base da dimensão (mL
 * ou min), em {@link BigDecimal}, e só a exibição arredonda, sempre para baixo.
 */
class QuantitiesTests {

    @ParameterizedTest(name = "{0} {1} em {2} = {3} na base")
    @CsvSource({
            "2,     L,   VOLUME,   2000",
            "1.5,   L,   VOLUME,   1500",
            "500,   mL,  VOLUME,   500",
            "1,     h,   DURATION, 60",
            "1.25,  h,   DURATION, 75.00",
            "30,    min, DURATION, 30",
    })
    void converteParaABaseDaDimensao(String quantity, String unit, Measure measure, String base) {
        assertThat(Quantities.toBase(new BigDecimal(quantity), unit, measure))
                .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo(base));
    }

    @ParameterizedTest(name = "{0} {1} não soma numa meta de {2}")
    @CsvSource({
            "3,   copo, VOLUME",
            "2,   L,    DURATION",
            "30,  min,  VOLUME",
            "5,   km,   DURATION",
            "72,  kg,   VOLUME",
            "30,  ,     VOLUME",
            ",    L,    VOLUME",
    })
    void unidadeDeOutraDimensaoContagemOuSemQuantidadeNaoSomam(String quantity, String unit, Measure measure) {
        BigDecimal value = quantity == null ? null : new BigDecimal(quantity);

        assertThat(Quantities.toBase(value, unit, measure)).isEqualTo(Optional.empty());
    }

    @ParameterizedTest(name = "{0} na base em {1} = {2}")
    @CsvSource({
            "1500, L,   1.50",
            "1999, L,   1.99",
            "500,  mL,  500.00",
            "50,   h,   0.83",
            "90,   h,   1.50",
            "90,   min, 90.00",
    })
    void exibeNaUnidadeDaMetaArredondandoParaBaixo(String base, String unit, String expected) {
        assertThat(Quantities.fromBase(new BigDecimal(base), unit)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0} é {1}")
    @CsvSource({"L, VOLUME", "mL, VOLUME", "h, DURATION", "min, DURATION", "copo, NONE", "km, NONE"})
    void dimensaoDeCadaUnidade(String unit, Measure measure) {
        assertThat(Quantities.measureOf(unit)).isEqualTo(measure);
    }
}
