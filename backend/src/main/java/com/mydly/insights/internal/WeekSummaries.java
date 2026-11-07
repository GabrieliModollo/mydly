package com.mydly.insights.internal;

import com.mydly.finance.FinanceTotals;
import com.mydly.finance.PeriodTotals;
import com.mydly.insights.internal.WeekSummary.DayMark;
import com.mydly.insights.internal.WeekSummary.HabitWeek;
import com.mydly.insights.internal.WeekSummary.Previous;
import com.mydly.routines.DayHabits;
import com.mydly.routines.HabitOutcomes;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Monta o resumo de uma semana de 7 dias que começa no dia configurado em {@link WeekDefinition}.
 * Só consulta as APIs públicas de {@code finance} e {@code routines}, dia a dia, no fuso do usuário.
 */
@Service
public class WeekSummaries {

    /** Fuso da semana até existir fuso por usuário; o mesmo do resumo do dia. */
    public static final ZoneId USER_ZONE = ZoneId.of("America/Sao_Paulo");

    private final FinanceTotals finance;
    private final HabitOutcomes habits;
    private final Clock clock;
    private final WeekDefinition definition;

    WeekSummaries(FinanceTotals finance, HabitOutcomes habits, Clock clock, WeekDefinition definition) {
        this.finance = finance;
        this.habits = habits;
        this.clock = clock;
        this.definition = definition;
    }

    /** O dia em que a semana começa. */
    public DayOfWeek firstDayOfWeek() {
        return definition.firstDayOfWeek();
    }

    /** O primeiro dia da semana de hoje, no fuso do usuário. */
    public LocalDate currentWeekStart() {
        return LocalDate.now(clock.withZone(USER_ZONE)).with(TemporalAdjusters.previousOrSame(firstDayOfWeek()));
    }

    /**
     * O resumo da semana que começa em {@code start}.
     *
     * @throws IllegalArgumentException se a data não cair no dia em que a semana começa
     */
    public WeekSummary of(LocalDate start, Tone tone) {
        if (start.getDayOfWeek() != firstDayOfWeek()) {
            throw new IllegalArgumentException("a semana começa em " + firstDayOfWeek() + ": " + start);
        }
        LocalDate today = LocalDate.now(clock.withZone(USER_ZONE));
        int elapsed = (int) Math.max(0, Math.min(7, ChronoUnit.DAYS.between(start, today) + 1));

        List<DayHabits> days = daysOf(start, elapsed);
        List<HabitWeek> habitWeeks = habitWeeks(days, elapsed);
        PeriodTotals money = totals(start, 7);
        List<BigDecimal> spentPerDay = new ArrayList<>(
                finance.spentPerDay(start, start.plusDays(elapsed), USER_ZONE).values());
        boolean hasData = money.spent().signum() > 0 || money.received().signum() > 0
                || days.stream().anyMatch(day -> !day.logs().isEmpty());

        Previous previous = previousWeek(start.minusDays(7), elapsed);
        String upTo = elapsed > 0 && elapsed < 7 ? WeekInsights.dayName(start.plusDays(elapsed - 1)) : null;
        BigDecimal spentSoFar = elapsed == 7 ? money.spent() : totals(start, elapsed).spent();
        WeekInsights.WeekFacts facts = new WeekInsights.WeekFacts(habitWeeks, spentSoFar, previous, spentPerDay,
                hasData, upTo);
        return new WeekSummary(start, elapsed, habitWeeks, money, previous, WeekInsights.evaluate(facts, tone));
    }

    private List<DayHabits> daysOf(LocalDate start, int count) {
        List<DayHabits> days = new ArrayList<>();
        for (int day = 0; day < count; day++) {
            days.add(habits.on(start.plusDays(day), USER_ZONE));
        }
        return days;
    }

    private PeriodTotals totals(LocalDate start, int days) {
        return finance.between(start.atStartOfDay(USER_ZONE).toInstant(),
                start.plusDays(days).atStartOfDay(USER_ZONE).toInstant());
    }

    /** Os hábitos que apareceram na semana, na ordem do primeiro registro, com a marca de cada dia. */
    private static List<HabitWeek> habitWeeks(List<DayHabits> days, int elapsed) {
        Map<String, DayHabits.HabitDay> seen = new LinkedHashMap<>();
        days.forEach(day -> day.habits().forEach(habit -> seen.putIfAbsent(habit.code(), habit)));
        List<HabitWeek> weeks = new ArrayList<>();
        for (DayHabits.HabitDay habit : seen.values()) {
            List<DayMark> marks = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                marks.add(day >= elapsed ? DayMark.FUTURE : markOn(days.get(day), habit.code()));
            }
            int achieved = (int) marks.stream().filter(mark -> mark == DayMark.ACHIEVED).count();
            weeks.add(new HabitWeek(habit.code(), habit.name(), habit.polarity(), marks, achieved, elapsed));
        }
        return weeks;
    }

    private static DayMark markOn(DayHabits day, String code) {
        return day.habits().stream().filter(habit -> habit.code().equals(code)).findFirst()
                .map(habit -> DayMark.valueOf(habit.status().name()))
                .orElse(DayMark.NONE);
    }

    /**
     * A semana anterior nos mesmos dias decorridos da atual (segunda a quarta contra segunda a quarta),
     * para a comparação ser justa no meio da semana. {@code null} quando a semana anterior inteira não
     * teve nenhum dado.
     */
    private Previous previousWeek(LocalDate start, int elapsed) {
        PeriodTotals wholeWeek = totals(start, 7);
        List<DayHabits> wholeDays = daysOf(start, 7);
        boolean hasData = wholeWeek.spent().signum() > 0 || wholeWeek.received().signum() > 0
                || wholeDays.stream().anyMatch(day -> !day.logs().isEmpty());
        if (!hasData) {
            return null;
        }
        int compared = Math.max(elapsed, 1);
        BigDecimal spent = compared == 7 ? wholeWeek.spent() : totals(start, compared).spent();
        long achieved = wholeDays.subList(0, compared).stream().mapToLong(DayHabits::achieved).sum();
        return new Previous(spent, achieved, compared);
    }
}
