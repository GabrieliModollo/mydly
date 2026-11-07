package com.mydly.insights.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * O dia de referência de uma semana (ADR 0010): hoje na semana em curso, o último dia numa semana
 * encerrada e o primeiro numa semana que ainda não começou.
 */
class WeekSummariesTests {

    @ParameterizedTest(name = "hoje {0}: referência {1}")
    @CsvSource({
            "2026-11-04, 2026-11-04",
            "2026-11-02, 2026-11-02",
            "2026-11-08, 2026-11-08",
            "2026-11-09, 2026-11-08",
            "2027-01-15, 2026-11-08",
            "2026-11-01, 2026-11-02",
    })
    void diaDeReferencia(LocalDate today, LocalDate reference) {
        assertThat(WeekSummaries.referenceDay(LocalDate.of(2026, 11, 2), today)).isEqualTo(reference);
    }
}
