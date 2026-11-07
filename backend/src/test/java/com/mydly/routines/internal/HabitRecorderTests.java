package com.mydly.routines.internal;

import static com.mydly.TestUsers.DEMO;
import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.TestcontainersConfiguration;
import com.mydly.entries.Direction;
import com.mydly.entries.EntryItemAccepted;
import com.mydly.entries.EntryItemRetracted;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.RoutineOutcome;
import com.mydly.routines.DayHabits;
import com.mydly.routines.HabitOutcomes;
import com.mydly.routines.HabitStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

/**
 * Consumidor de eventos do módulo routines: o hábito do catálogo, a polaridade e as mesmas regras
 * de revisão e lápide do módulo finance (ADR 0008).
 */
// O módulo usa a API pública de preferences (dia de hoje e semana), que entra junto (ADR 0010).
@ApplicationModuleTest(extraIncludes = {"preferences", "accounts"})
@Import(TestcontainersConfiguration.class)
class HabitRecorderTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final AtomicInteger DAYS = new AtomicInteger();

    @Autowired
    private HabitRecorder recorder;

    @Autowired
    private HabitCatalog catalog;

    @Autowired
    private HabitOutcomes outcomes;

    private LocalDate day;
    private Instant noon;

    @BeforeEach
    void freshDay() {
        day = LocalDate.of(2032, 1, 1).plusDays(DAYS.incrementAndGet());
        noon = day.atTime(12, 0).atZone(ZONE).toInstant();
    }

    /** Cada linha é: texto do item | hábito do catálogo, ou SEM para outros. */
    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource(delimiter = '|', nullValues = "SEM", textBlock = """
            Bebi 2 litros de água             | HYDRATION
            Fui à academia                    | GYM
            Fiz 30 flexões                    | GYM
            Estudei durante 2 horas           | STUDY
            Dormi mal                         | SLEEP
            Segui a dieta                     | DIET
            Li 30min                          | READING
            Não comi besteira                 | JUNK_FOOD
            Tomei 2 energéticos               | ENERGY_DRINK
            Deixei de fumar                   | SMOKING
            Pedi ifood                        | DELIVERY
            # A palavra que aparece primeiro decide.
            Bebi água, não refrigerante       | HYDRATION
            Não tomei refrigerante            | SODA
            # "li" não casa dentro de outras palavras.
            Limpei a casa                     | SEM
            Meditei                           | SEM
            """)
    void encontraOHabitoDoCatalogo(String text, String expected) {
        assertThat(catalog.match(text).map(HabitCatalog.Habit::code).orElse(null)).isEqualTo(expected);
    }

    /**
     * Sem palavra-chave, {@code bebi} com um volume e nada mais é Hidratação. A palavra-chave vence:
     * {@code bebi 2 litros de refrigerante} é Refrigerante, e bebida sem palavra-chave fica em outros.
     */
    @ParameterizedTest(name = "\"{0}\" ({1}) → {2}")
    @CsvSource(delimiter = '|', nullValues = "SEM", textBlock = """
            Bebi 1 litro                      | L    | HYDRATION
            bebi 500ml                        | mL   | HYDRATION
            Bebi 2 litros hoje                | L    | HYDRATION
            Bebi 2 litros de água             | L    | HYDRATION
            bebi 1 litro de cerveja           | L    | SEM
            bebi 500 ml de suco               | mL   | SEM
            bebi 2 litros de refrigerante     | L    | SODA
            """)
    void bebiComVolumeESemBebidaEAgua(String text, String unit, String expected) {
        assertThat(catalog.match(text, unit).map(HabitCatalog.Habit::code).orElse(null)).isEqualTo(expected);
    }

    @Test
    void naoComiBesteiraContaComoConquistaPorEvento(Scenario scenario) {
        scenario.publish(routine(UUID.randomUUID(), 1, RoutineOutcome.MISSED, "Não comi besteira"))
                .andWaitForStateChange(() -> outcomes.on(DEMO, day, ZONE), habits -> !habits.habits().isEmpty())
                .andVerify(habits -> {
                    assertThat(habits.achieved()).isEqualTo(1);
                    assertThat(habits.habits().getFirst().status()).isEqualTo(HabitStatus.ACHIEVED);
                });
    }

    @Test
    void deixeiDeFumarContaComoConquista() {
        recorder.on(routine(UUID.randomUUID(), 1, RoutineOutcome.MISSED, "Deixei de fumar"));

        assertThat(outcomes.on(DEMO, day, ZONE).achieved()).isEqualTo(1);
    }

    @Test
    void consumirHabitoRuimEDeslize() {
        recorder.on(routine(UUID.randomUUID(), 1, RoutineOutcome.DONE, "Tomei 2 energéticos"));

        DayHabits habits = outcomes.on(DEMO, day, ZONE);
        assertThat(habits.slipped()).isEqualTo(1);
        assertThat(habits.achieved()).isZero();
    }

    @Test
    void mesmoHabitoDuasVezesContaUmaVez() {
        recorder.on(routine(UUID.randomUUID(), 1, RoutineOutcome.DONE, "Bebi 1 litro de água"));
        recorder.on(routine(UUID.randomUUID(), 1, RoutineOutcome.DONE, "Bebi mais 1 litro de água"));

        assertThat(outcomes.on(DEMO, day, ZONE).achieved()).isEqualTo(1);
    }

    @Test
    void deslizeVenceAConquistaNoMesmoDia() {
        recorder.on(routine(UUID.randomUUID(), 1, RoutineOutcome.MISSED, "Não comi besteira"));
        recorder.on(routine(UUID.randomUUID(), 1, RoutineOutcome.DONE, "Comi besteira"));

        DayHabits habits = outcomes.on(DEMO, day, ZONE);
        assertThat(habits.slipped()).isEqualTo(1);
        assertThat(habits.achieved()).isZero();
    }

    @Test
    void rotinaForaDoCatalogoFicaRegistradaMasNaoConta() {
        UUID entryId = UUID.randomUUID();
        recorder.on(routine(entryId, 1, RoutineOutcome.DONE, "Meditei"));

        DayHabits habits = outcomes.on(DEMO, day, ZONE);
        assertThat(habits.habits()).isEmpty();
        assertThat(habits.logs()).singleElement()
                .satisfies(log -> {
                    assertThat(log.entryId()).isEqualTo(entryId);
                    assertThat(log.code()).isNull();
                });
    }

    @Test
    void retiradaEReaceiteSeguemARevisao() {
        UUID entryId = UUID.randomUUID();
        recorder.on(routine(entryId, 1, RoutineOutcome.DONE, "Fui à academia"));
        recorder.on(new EntryItemRetracted(DEMO, entryId, 0, 2));
        recorder.on(routine(entryId, 1, RoutineOutcome.DONE, "Fui à academia"));

        assertThat(outcomes.on(DEMO, day, ZONE).logs()).isEmpty();

        recorder.on(routine(entryId, 3, RoutineOutcome.DONE, "Fui à academia"));

        assertThat(outcomes.on(DEMO, day, ZONE).achieved()).isEqualTo(1);
    }

    @Test
    void itemDeFinancaNaoViraHabito() {
        recorder.on(new EntryItemAccepted(DEMO, UUID.randomUUID(), 0, 1, noon,
                ParsedEntry.finance(Direction.EXPENSE, BigDecimal.TEN, "Comprei energético", 0.95)));

        assertThat(outcomes.on(DEMO, day, ZONE).logs()).isEmpty();
    }

    private EntryItemAccepted routine(UUID entryId, long revision, RoutineOutcome outcome, String text) {
        return new EntryItemAccepted(DEMO, entryId, 0, revision, noon, ParsedEntry.routine(outcome, null, null, text, 0.6));
    }
}
