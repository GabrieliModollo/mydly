package com.mydly.routines.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mydly.entries.RoutineOutcome;
import com.mydly.routines.DailyGoalProgress;
import com.mydly.routines.GoalKind;
import com.mydly.routines.HabitGoal;
import com.mydly.routines.Measure;
import com.mydly.routines.Polarity;
import com.mydly.routines.WeekGoalScore;
import com.mydly.routines.internal.GoalRules.DayLog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Regras das metas de hábito (ADR 0010): validação contra o hábito, versão vigente, dia cumprido na
 * meta diária e placar da semana com a regra do dia de referência.
 */
class GoalRulesTests {

    private static final HabitCatalog.Habit HYDRATION = HabitCatalog.Habit.of("HYDRATION", "Hidratação", Polarity.GOOD,
            Measure.VOLUME);
    private static final HabitCatalog.Habit STUDY = HabitCatalog.Habit.of("STUDY", "Estudo", Polarity.GOOD,
            Measure.DURATION);
    private static final HabitCatalog.Habit GYM = HabitCatalog.Habit.of("GYM", "Academia", Polarity.GOOD,
            Measure.DURATION);
    private static final HabitCatalog.Habit DIET = HabitCatalog.Habit.of("DIET", "Dieta", Polarity.GOOD, Measure.NONE);
    private static final HabitCatalog.Habit DELIVERY = HabitCatalog.Habit.of("DELIVERY", "Delivery", Polarity.BAD,
            Measure.NONE);

    /** Segunda-feira; a semana dos testes vai de 2 a 8 de novembro de 2026. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final List<LocalDate> WEEK = MONDAY.datesUntil(MONDAY.plusDays(7)).toList();

    // Validação

    @ParameterizedTest(name = "{0} em {1}")
    @CsvSource({"2, L", "500, mL", "0.5, L"})
    void metaDiariaDeHidratacaoEmVolume(String quantity, String unit) {
        GoalRules.validate(HYDRATION, GoalKind.DAILY_QUANTITY, new BigDecimal(quantity), unit, null);
    }

    @ParameterizedTest(name = "{0} em {1}")
    @CsvSource({"1.5, h", "30, min"})
    void metaDiariaDeEstudoEmDuracao(String quantity, String unit) {
        GoalRules.validate(STUDY, GoalKind.DAILY_QUANTITY, new BigDecimal(quantity), unit, null);
    }

    @ParameterizedTest(name = "{1} na {0} é recusado")
    @CsvSource({"HYDRATION, h", "HYDRATION, min", "STUDY, L", "STUDY, mL", "HYDRATION, copo", "HYDRATION, km"})
    void unidadeIncompativelComOHabitoERecusada(String code, String unit) {
        HabitCatalog.Habit habit = code.equals("HYDRATION") ? HYDRATION : STUDY;

        assertThatThrownBy(() -> GoalRules.validate(habit, GoalKind.DAILY_QUANTITY, BigDecimal.TEN, unit, null))
                .isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void metaDiariaEmHabitoSemMedidaERecusada() {
        assertThatThrownBy(() -> GoalRules.validate(DIET, GoalKind.DAILY_QUANTITY, BigDecimal.ONE, "L", null))
                .isInstanceOf(InvalidGoalException.class).hasMessageContaining("não tem medida");
    }

    @Test
    void habitoRuimSoAceitaLimiteDeDias() {
        assertThatThrownBy(() -> GoalRules.validate(DELIVERY, GoalKind.DAILY_QUANTITY, BigDecimal.ONE, "L", null))
                .isInstanceOf(InvalidGoalException.class).hasMessageContaining("Hábito ruim");
        GoalRules.validate(DELIVERY, GoalKind.WEEKLY_FREQUENCY, null, null, 0);
        GoalRules.validate(DELIVERY, GoalKind.WEEKLY_FREQUENCY, null, null, 7);
    }

    @ParameterizedTest(name = "{1} dias em {0}")
    @CsvSource({"GYM, 0", "GYM, 8", "DELIVERY, -1", "DELIVERY, 8"})
    void diasPorSemanaForaDaFaixa(String code, int days) {
        HabitCatalog.Habit habit = code.equals("GYM") ? GYM : DELIVERY;

        assertThatThrownBy(() -> GoalRules.validate(habit, GoalKind.WEEKLY_FREQUENCY, null, null, days))
                .isInstanceOf(InvalidGoalException.class);
    }

    @ParameterizedTest(name = "quantidade {0}")
    @CsvSource({"0", "-1", "1.255", "1000001"})
    void quantidadeInvalida(String quantity) {
        assertThatThrownBy(() -> GoalRules.validate(HYDRATION, GoalKind.DAILY_QUANTITY, new BigDecimal(quantity), "L",
                null)).isInstanceOf(InvalidGoalException.class);
    }

    @Test
    void camposDeOutroTipoSaoRecusados() {
        assertThatThrownBy(() -> GoalRules.validate(HYDRATION, GoalKind.DAILY_QUANTITY, BigDecimal.ONE, "L", 3))
                .isInstanceOf(InvalidGoalException.class);
        assertThatThrownBy(() -> GoalRules.validate(GYM, GoalKind.WEEKLY_FREQUENCY, BigDecimal.ONE, null, 3))
                .isInstanceOf(InvalidGoalException.class);
        assertThatThrownBy(() -> GoalRules.validate(GYM, GoalKind.NONE, null, null, null))
                .isInstanceOf(InvalidGoalException.class);
    }

    // Versões

    @Test
    void aVersaoVigenteEADeMaiorValidFromAteODia() {
        List<HabitGoal> versions = List.of(daily("2", "L", MONDAY), daily("3", "L", MONDAY.plusDays(3)),
                none(MONDAY.plusDays(5)));

        assertThat(GoalRules.versionOn(versions, MONDAY.minusDays(1))).isEmpty();
        assertThat(GoalRules.goalOn(versions, MONDAY).orElseThrow().targetQuantity()).isEqualByComparingTo("2");
        assertThat(GoalRules.goalOn(versions, MONDAY.plusDays(2)).orElseThrow().targetQuantity()).isEqualByComparingTo("2");
        assertThat(GoalRules.goalOn(versions, MONDAY.plusDays(3)).orElseThrow().targetQuantity()).isEqualByComparingTo("3");
        assertThat(GoalRules.versionOn(versions, MONDAY.plusDays(6)).orElseThrow().kind()).isEqualTo(GoalKind.NONE);
        assertThat(GoalRules.goalOn(versions, MONDAY.plusDays(6))).isEmpty();
    }

    // Dia cumprido na meta diária

    @Test
    void umLitroMaisQuinhentosMlDaUmEMeioDeDoisLitros() {
        HabitGoal goal = daily("2", "L", MONDAY);
        List<DayLog> logs = List.of(done("1", "L"), done("500", "mL"));

        DailyGoalProgress progress = GoalRules.daily(HYDRATION, goal, logs);

        assertThat(progress.done()).isEqualByComparingTo("1.5");
        assertThat(progress.target()).isEqualByComparingTo("2");
        assertThat(progress.met()).isFalse();
    }

    @Test
    void metaEmMlComRegistrosEmLitros() {
        HabitGoal goal = daily("1500", "mL", MONDAY);

        assertThat(GoalRules.dayMet(goal, List.of(done("1.5", "L")))).isTrue();
        assertThat(GoalRules.daily(HYDRATION, goal, List.of(done("1.2", "L"))).done()).isEqualByComparingTo("1200");
    }

    @Test
    void metaEmHorasComRegistrosEmMinutos() {
        HabitGoal goal = daily("1.5", "h", MONDAY);

        assertThat(GoalRules.dayMet(goal, List.of(done("45", "min"), done("45", "min")))).isTrue();
        assertThat(GoalRules.dayMet(goal, List.of(done("89", "min")))).isFalse();
        assertThat(GoalRules.daily(STUDY, goal, List.of(done("50", "min"))).done()).isEqualByComparingTo("0.83");
    }

    @Test
    void registrosSemQuantidadeOuDeOutraDimensaoNaoSomam() {
        HabitGoal goal = daily("2", "L", MONDAY);
        List<DayLog> logs = List.of(done("3", "copo"), done(null, null), done("30", "min"), done("1", "L"));

        assertThat(GoalRules.doneBase(goal, logs)).isEqualByComparingTo("1000");
        assertThat(GoalRules.dayStatus(Polarity.GOOD, logs)).isPresent();
    }

    @Test
    void registroNaoCumpridoNaoSoma() {
        HabitGoal goal = daily("2", "L", MONDAY);

        assertThat(GoalRules.doneBase(goal, List.of(new DayLog(RoutineOutcome.MISSED, new BigDecimal("2"), "L"))))
                .isEqualByComparingTo("0");
    }

    @Test
    void exatamenteNaMetaCumpre() {
        assertThat(GoalRules.dayMet(daily("2", "L", MONDAY), List.of(done("2000", "mL")))).isTrue();
        assertThat(GoalRules.daily(HYDRATION, daily("2", "L", MONDAY), List.of(done("1999", "mL"))).done())
                .isEqualByComparingTo("1.99");
    }

    // Placar da semana

    @Test
    void metaDiariaTrocadaNoMeioDaSemanaAvaliaCadaDiaPelaMetaDele() {
        // 2 L de segunda a quarta; 3 L de quinta em diante. Bebeu 2 L todo dia.
        List<HabitGoal> versions = List.of(daily("2", "L", MONDAY.minusDays(30)), daily("3", "L", MONDAY.plusDays(3)));
        Map<LocalDate, List<DayLog>> logs = everyDay(done("2", "L"));

        WeekGoalScore score = GoalRules.week(HYDRATION, versions, WEEK, WEEK.getLast(), logs).orElseThrow();

        assertThat(score.kind()).isEqualTo(GoalKind.DAILY_QUANTITY);
        assertThat(score.achieved()).isEqualTo(3);
        assertThat(score.target()).isEqualTo(7);
    }

    @Test
    void metaDiariaCriadaNaQuartaSoContaDaQuartaEmDiante() {
        List<HabitGoal> versions = List.of(daily("2", "L", MONDAY.plusDays(2)));

        WeekGoalScore score = GoalRules.week(HYDRATION, versions, WEEK, MONDAY.plusDays(4), everyDay(done("2", "L")))
                .orElseThrow();

        assertThat(score.target()).isEqualTo(5);
        assertThat(score.achieved()).isEqualTo(5);
    }

    @Test
    void metaSemanalTrocadaNoMeioDaSemanaVaiPelaVersaoDoDiaDeReferencia() {
        // 4 dias até quarta; 2 dias de quinta em diante.
        List<HabitGoal> versions = List.of(weekly(4, MONDAY.minusDays(30)), weekly(2, MONDAY.plusDays(3)));
        Map<LocalDate, List<DayLog>> logs = Map.of(MONDAY, List.of(done(null, null)),
                MONDAY.plusDays(1), List.of(done(null, null)));

        WeekGoalScore onTuesday = GoalRules.week(GYM, versions, WEEK, MONDAY.plusDays(1), logs).orElseThrow();
        WeekGoalScore closed = GoalRules.week(GYM, versions, WEEK, WEEK.getLast(), logs).orElseThrow();

        assertThat(onTuesday.target()).isEqualTo(4);
        assertThat(closed.target()).isEqualTo(2);
        assertThat(closed.achieved()).isEqualTo(2);
    }

    @Test
    void metaRemovidaAntesDoDiaDeReferenciaDeixaASemanaSemMeta() {
        List<HabitGoal> versions = List.of(weekly(4, MONDAY.minusDays(30)), none(MONDAY.plusDays(3)));

        assertThat(GoalRules.week(GYM, versions, WEEK, WEEK.getLast(), Map.of())).isEmpty();
        assertThat(GoalRules.week(GYM, versions, WEEK, MONDAY.plusDays(2), Map.of())).isPresent();
    }

    @ParameterizedTest(name = "{0} deslizes com limite 2")
    @CsvSource({"1", "2", "3"})
    void limiteDeHabitoRuimContaOsDeslizes(int slips) {
        Map<LocalDate, List<DayLog>> logs = new HashMap<>();
        for (int day = 0; day < slips; day++) {
            logs.put(MONDAY.plusDays(day), List.of(done(null, null)));
        }
        logs.put(MONDAY.plusDays(5), List.of(new DayLog(RoutineOutcome.MISSED, null, null)));

        WeekGoalScore score = GoalRules.week(DELIVERY, List.of(weekly(2, MONDAY)), WEEK, WEEK.getLast(), logs)
                .orElseThrow();

        assertThat(score.isLimit()).isTrue();
        assertThat(score.limit()).isEqualTo(2);
        assertThat(score.slips()).isEqualTo(slips);
        assertThat(score.achieved()).as("dias evitados").isEqualTo(1);
        assertThat(score.target()).isNull();
    }

    private static HabitGoal daily(String quantity, String unit, LocalDate from) {
        return new HabitGoal("X", from, GoalKind.DAILY_QUANTITY, new BigDecimal(quantity), unit, null);
    }

    private static HabitGoal weekly(int days, LocalDate from) {
        return new HabitGoal("X", from, GoalKind.WEEKLY_FREQUENCY, null, null, days);
    }

    private static HabitGoal none(LocalDate from) {
        return new HabitGoal("X", from, GoalKind.NONE, null, null, null);
    }

    private static DayLog done(String quantity, String unit) {
        return new DayLog(RoutineOutcome.DONE, quantity == null ? null : new BigDecimal(quantity), unit);
    }

    private static Map<LocalDate, List<DayLog>> everyDay(DayLog log) {
        Map<LocalDate, List<DayLog>> logs = new HashMap<>();
        WEEK.forEach(day -> logs.put(day, List.of(log)));
        return logs;
    }
}
