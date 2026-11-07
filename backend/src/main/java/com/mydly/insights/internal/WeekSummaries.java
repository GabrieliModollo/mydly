package com.mydly.insights.internal;

import com.mydly.finance.BudgetProgress;
import com.mydly.finance.BudgetWindow;
import com.mydly.finance.Budgets;
import com.mydly.finance.FinanceTotals;
import com.mydly.finance.PeriodTotals;
import com.mydly.insights.internal.WeekSummary.DayMark;
import com.mydly.insights.internal.WeekSummary.HabitWeek;
import com.mydly.insights.internal.WeekSummary.Previous;
import com.mydly.preferences.UserCalendar;
import com.mydly.preferences.WeekPreferences;
import com.mydly.routines.DayHabits;
import com.mydly.routines.HabitGoals;
import com.mydly.routines.HabitOutcomes;
import com.mydly.routines.WeekGoalScore;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Monta o resumo de uma semana de 7 dias que começa no dia das preferências ({@link WeekPreferences}).
 * Só consulta as APIs públicas de {@code finance}, {@code routines} e {@code preferences}, no fuso do
 * usuário.
 *
 * <p>As metas e os orçamentos da semana são avaliados no dia de referência (ADR 0010): o último dia da
 * semana, ou hoje se ela está em curso.
 */
@Service
public class WeekSummaries {

    /** Fuso da semana até existir fuso por usuário; o mesmo do resumo do dia. */
    public static final ZoneId USER_ZONE = UserCalendar.ZONE;

    private final FinanceTotals finance;
    private final Budgets budgets;
    private final HabitOutcomes habits;
    private final HabitGoals goals;
    private final WeekPreferences preferences;
    private final UserCalendar calendar;

    WeekSummaries(FinanceTotals finance, Budgets budgets, HabitOutcomes habits, HabitGoals goals,
                  WeekPreferences preferences, UserCalendar calendar) {
        this.finance = finance;
        this.budgets = budgets;
        this.habits = habits;
        this.goals = goals;
        this.preferences = preferences;
        this.calendar = calendar;
    }

    /** O dia em que a semana começa, nas preferências atuais. */
    public DayOfWeek firstDayOfWeek() {
        return preferences.firstDayOfWeek();
    }

    /** O primeiro dia da semana de hoje, no fuso do usuário. */
    public LocalDate currentWeekStart() {
        return preferences.currentWeek().start();
    }

    /**
     * O dia que decide a meta e o limite de uma semana: hoje, se ela está em curso; senão o último dia
     * dela (ou o primeiro, numa semana que ainda não começou).
     */
    static LocalDate referenceDay(LocalDate start, LocalDate today) {
        LocalDate end = start.plusDays(6);
        if (today.isBefore(start)) {
            return start;
        }
        return today.isAfter(end) ? end : today;
    }

    /**
     * O resumo da semana que começa em {@code start}.
     *
     * @throws IllegalArgumentException se a data não cair no dia em que a semana começa
     */
    public WeekSummary of(LocalDate start, Tone tone) {
        DayOfWeek firstDay = firstDayOfWeek();
        if (start.getDayOfWeek() != firstDay) {
            throw new IllegalArgumentException("a semana começa em " + firstDay + ": " + start);
        }
        LocalDate end = start.plusDays(6);
        LocalDate today = calendar.today();
        int elapsed = (int) Math.max(0, Math.min(7, ChronoUnit.DAYS.between(start, today) + 1));
        boolean inProgress = !today.isBefore(start) && !today.isAfter(end);
        LocalDate reference = referenceDay(start, today);

        List<DayHabits> days = daysOf(start, elapsed);
        List<WeekGoalScore> scores = goals.week(start, end, reference, USER_ZONE);
        List<HabitWeek> habitWeeks = habitWeeks(days, elapsed, scores);
        List<BudgetProgress> budgetProgress = budgets.progress(BudgetWindow.upTo(reference, start), USER_ZONE);
        PeriodTotals money = totals(start, 7);
        List<BigDecimal> spentPerDay = new ArrayList<>(
                finance.spentPerDay(start, start.plusDays(elapsed), USER_ZONE).values());
        boolean hasData = money.spent().signum() > 0 || money.received().signum() > 0
                || days.stream().anyMatch(day -> !day.logs().isEmpty());

        Previous previous = previousWeek(start.minusDays(7), elapsed);
        String upTo = elapsed > 0 && elapsed < 7 ? WeekInsights.dayName(start.plusDays(elapsed - 1)) : null;
        BigDecimal spentSoFar = elapsed == 7 ? money.spent() : totals(start, elapsed).spent();
        WeekInsights.WeekFacts facts = new WeekInsights.WeekFacts(habitWeeks, spentSoFar, previous, spentPerDay,
                hasData, upTo, inProgress, budgetProgress);
        return new WeekSummary(start, elapsed, habitWeeks, money, budgetProgress, previous,
                WeekInsights.evaluate(facts, tone));
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

    /**
     * Os hábitos que apareceram na semana, na ordem do primeiro registro, com a marca de cada dia e o
     * placar contra a meta; depois, os hábitos com meta que não tiveram nenhum registro.
     */
    private static List<HabitWeek> habitWeeks(List<DayHabits> days, int elapsed, List<WeekGoalScore> scores) {
        Map<String, WeekGoalScore> byCode = scores.stream()
                .collect(Collectors.toMap(WeekGoalScore::habitCode, Function.identity()));
        Map<String, DayHabits.HabitDay> seen = new LinkedHashMap<>();
        days.forEach(day -> day.habits().forEach(habit -> seen.putIfAbsent(habit.code(), habit)));
        List<HabitWeek> weeks = new ArrayList<>();
        for (DayHabits.HabitDay habit : seen.values()) {
            List<DayMark> marks = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                marks.add(day >= elapsed ? DayMark.FUTURE : markOn(days.get(day), habit.code()));
            }
            int achieved = (int) marks.stream().filter(mark -> mark == DayMark.ACHIEVED).count();
            weeks.add(new HabitWeek(habit.code(), habit.name(), habit.polarity(), marks, achieved, elapsed,
                    byCode.get(habit.code())));
        }
        for (WeekGoalScore score : scores) {
            if (!seen.containsKey(score.habitCode())) {
                List<DayMark> marks = new ArrayList<>();
                for (int day = 0; day < 7; day++) {
                    marks.add(day >= elapsed ? DayMark.FUTURE : DayMark.NONE);
                }
                weeks.add(new HabitWeek(score.habitCode(), score.habitName(), score.polarity(), marks, 0, elapsed,
                        score));
            }
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
