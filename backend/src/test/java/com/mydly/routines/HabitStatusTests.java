package com.mydly.routines;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.RoutineOutcome;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Polaridade: num hábito ruim, a ação acontecer é deslize e não acontecer é conquista.
 * Cada linha é: polaridade | o que o parser entendeu | status.
 */
class HabitStatusTests {

    @ParameterizedTest(name = "{0} + {1} → {2}")
    @CsvSource(delimiter = '|', textBlock = """
            # "Bebi 2 litros de água"
            GOOD | DONE   | ACHIEVED
            # "Não fui à academia"
            GOOD | MISSED | MISSED
            # "Tomei 2 energéticos"
            BAD  | DONE   | SLIPPED
            # "Não comi besteira", "Deixei de fumar"
            BAD  | MISSED | ACHIEVED
            """)
    void aplicaAPolaridade(Polarity polarity, RoutineOutcome outcome, HabitStatus expected) {
        assertThat(HabitStatus.of(polarity, outcome)).isEqualTo(expected);
    }
}
