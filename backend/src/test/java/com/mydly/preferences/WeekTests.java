package com.mydly.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * A semana de 7 dias que contém um dia, para qualquer dia de início (ADR 0010).
 */
class WeekTests {

    /** Sábado, 7 de novembro de 2026. */
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 7);

    @ParameterizedTest(name = "começando em {0}: {1} a {2}")
    @CsvSource({
            "MONDAY,    2026-11-02, 2026-11-08",
            "SUNDAY,    2026-11-01, 2026-11-07",
            "SATURDAY,  2026-11-07, 2026-11-13",
            "WEDNESDAY, 2026-11-04, 2026-11-10",
    })
    void semanaQueContemODia(DayOfWeek firstDay, LocalDate start, LocalDate end) {
        Week week = Week.containing(SATURDAY, firstDay);

        assertThat(week.start()).isEqualTo(start);
        assertThat(week.end()).isEqualTo(end);
        assertThat(week.contains(SATURDAY)).isTrue();
        assertThat(week.contains(end.plusDays(1))).isFalse();
    }

    @Test
    void semanaTemSeteDias() {
        assertThatThrownBy(() -> new Week(SATURDAY, SATURDAY.plusDays(7))).isInstanceOf(IllegalArgumentException.class);
    }
}
