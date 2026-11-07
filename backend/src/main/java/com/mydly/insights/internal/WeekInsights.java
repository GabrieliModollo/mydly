package com.mydly.insights.internal;

import com.mydly.entries.Category;
import com.mydly.finance.BudgetPeriod;
import com.mydly.finance.BudgetProgress;
import com.mydly.finance.BudgetStatus;
import com.mydly.insights.internal.WeekSummary.DayMark;
import com.mydly.insights.internal.WeekSummary.HabitWeek;
import com.mydly.insights.internal.WeekSummary.Insight;
import com.mydly.insights.internal.WeekSummary.Previous;
import com.mydly.routines.GoalKind;
import com.mydly.routines.Polarity;
import com.mydly.routines.WeekGoalScore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Regras das mensagens da semana, sem LLM (ADR 0009 e ADR 0010). Cada regra só dispara acima de um
 * limiar, e o cruzamento de sono com gastos exige pelo menos {@link #MIN_DAYS_PER_GROUP} dias em cada
 * grupo: com menos que isso a diferença pode ser acaso, e é melhor nenhuma mensagem do que uma falsa.
 */
final class WeekInsights {

    static final int MIN_DAYS = 3;
    static final int MIN_DAYS_PER_GROUP = 3;
    static final BigDecimal MIN_INCREASE = new BigDecimal("1.20");
    static final int MAX_INSIGHTS = 3;
    static final String SLEEP = "SLEEP";

    /** Os nomes das categorias em português, como o app mostra. */
    static final Map<Category, String> CATEGORY_NAMES = new EnumMap<>(Map.of(
            Category.FOOD, "Alimentação", Category.GROCERIES, "Mercado", Category.PHARMACY, "Farmácia",
            Category.TRANSPORT, "Transporte", Category.HOUSING, "Moradia", Category.HEALTH, "Saúde",
            Category.LEISURE, "Lazer", Category.WORK, "Trabalho", Category.OTHER, "Outros"));

    private WeekInsights() {
    }

    /**
     * O que a semana teve, já só com os dias decorridos.
     *
     * @param habits      os hábitos com algum registro ou com meta na semana
     * @param spent       gasto da semana até o último dia decorrido
     * @param previous    a semana anterior, ou {@code null} sem dados
     * @param spentPerDay gasto de cada dia decorrido, na ordem
     * @param hasData     a semana tem algum registro, mesmo fora do catálogo
     * @param upTo        o último dia decorrido por extenso ({@code quarta}) numa semana em andamento, ou
     *                    {@code null} numa semana completa
     * @param inProgress  hoje cai nesta semana, inclusive quando é o último dia dela
     * @param budgets     o gasto de cada categoria com orçamento contra o limite
     */
    record WeekFacts(List<HabitWeek> habits, BigDecimal spent, Previous previous, List<BigDecimal> spentPerDay,
                     boolean hasData, String upTo, boolean inProgress, List<BudgetProgress> budgets) {

        WeekFacts {
            habits = List.copyOf(habits);
            budgets = List.copyOf(budgets);
        }

        /** Uma semana encerrada e sem orçamentos. */
        WeekFacts(List<HabitWeek> habits, BigDecimal spent, Previous previous, List<BigDecimal> spentPerDay,
                  boolean hasData, String upTo) {
            this(habits, spent, previous, spentPerDay, hasData, upTo, false, List.of());
        }

        long achievedTotal() {
            return habits.stream().mapToLong(HabitWeek::achievedDays).sum();
        }
    }

    /** As mensagens da semana no tom pedido: no máximo três, a primeira é a principal. */
    static List<Insight> evaluate(WeekFacts facts, Tone tone) {
        return findings(facts).stream().limit(MAX_INSIGHTS)
                .map(finding -> new Insight(finding.kind(),
                        InsightTemplates.render(finding.kind(), finding.variant(), tone, finding.text()),
                        finding.numbers()))
                .toList();
    }

    /**
     * Todas as regras que dispararam, na ordem de prioridade de {@link InsightKind}. O resumo simples e
     * as boas-vindas só entram quando nenhuma outra regra disparou.
     */
    static List<Finding> findings(WeekFacts facts) {
        List<Finding> findings = new ArrayList<>();
        budget(facts, BudgetStatus.OVER).ifPresent(findings::add);
        recurringSlip(facts).ifPresent(findings::add);
        goalAtRisk(facts).ifPresent(findings::add);
        spendingUp(facts).ifPresent(findings::add);
        budget(facts, BudgetStatus.NEAR).ifPresent(findings::add);
        sleepAndSpending(facts).ifPresent(findings::add);
        goalMet(facts).ifPresent(findings::add);
        streak(facts).ifPresent(findings::add);
        if (findings.isEmpty()) {
            findings.add(facts.hasData() ? overview(facts) : new Finding(InsightKind.WELCOME, Map.of(), Map.of()));
        }
        findings.sort(Comparator.comparing(Finding::kind));
        return findings;
    }

    /**
     * A categoria com o maior percentual entre as que estão no estado pedido: acima do limite
     * ({@link BudgetStatus#OVER}) ou de 80% a 100% ({@link BudgetStatus#NEAR}).
     */
    static Optional<Finding> budget(WeekFacts facts, BudgetStatus status) {
        InsightKind kind = status == BudgetStatus.OVER ? InsightKind.BUDGET_OVER : InsightKind.BUDGET_NEAR;
        return facts.budgets().stream()
                .filter(budget -> budget.status() == status)
                .max(Comparator.comparing(WeekInsights::ratio))
                .map(budget -> {
                    Map<String, Object> numbers = new LinkedHashMap<>();
                    numbers.put("category", budget.category().name());
                    numbers.put("period", budget.period().name());
                    numbers.put("spent", budget.spent());
                    numbers.put("limit", budget.limit());
                    numbers.put("percent", budget.percent());
                    return new Finding(kind, numbers, Map.of(
                            "Category", CATEGORY_NAMES.get(budget.category()),
                            "period", budget.period() == BudgetPeriod.WEEKLY ? "da semana" : "do mês",
                            "spent", money(budget.spent()), "limit", money(budget.limit()),
                            "percent", String.valueOf(budget.percent())));
                });
    }

    private static BigDecimal ratio(BudgetProgress budget) {
        return budget.spent().divide(budget.limit(), 6, RoundingMode.DOWN);
    }

    /**
     * Uma meta de dias por semana que não fecha mais, só na semana em curso. Num hábito bom, quando os
     * dias cumpridos mais os que restam (hoje, se ainda não foi cumprido, e os dias futuros) não chegam
     * à meta; no último dia possível ainda não dispara. Num hábito ruim, quando os deslizes já passaram
     * do limite. Entre várias, vale a que está mais longe de fechar.
     */
    static Optional<Finding> goalAtRisk(WeekFacts facts) {
        if (!facts.inProgress()) {
            return Optional.empty();
        }
        Finding worst = null;
        int worstGap = 0;
        for (HabitWeek habit : facts.habits()) {
            WeekGoalScore goal = habit.goal();
            if (goal == null || goal.kind() != GoalKind.WEEKLY_FREQUENCY) {
                continue;
            }
            int gap;
            Finding finding;
            if (goal.isLimit()) {
                gap = goal.slips() - goal.limit();
                finding = limitFinding(InsightKind.GOAL_AT_RISK, habit, goal);
            } else {
                int remaining = remainingDays(habit);
                gap = goal.target() - (goal.achieved() + remaining);
                finding = targetFinding(InsightKind.GOAL_AT_RISK, habit, goal);
                finding.numbers().put("remaining", remaining);
            }
            if (gap > worstGap) {
                worst = finding;
                worstGap = gap;
            }
        }
        return Optional.ofNullable(worst);
    }

    /**
     * Uma meta de dias por semana atingida: num hábito bom, os dias cumpridos chegaram à meta, mesmo
     * com a semana em curso; num hábito ruim, a semana encerrou com os deslizes dentro do limite.
     * Entre várias, vale a primeira na ordem dos hábitos.
     */
    static Optional<Finding> goalMet(WeekFacts facts) {
        for (HabitWeek habit : facts.habits()) {
            WeekGoalScore goal = habit.goal();
            if (goal == null || goal.kind() != GoalKind.WEEKLY_FREQUENCY) {
                continue;
            }
            if (goal.isLimit()) {
                if (!facts.inProgress() && goal.slips() <= goal.limit()) {
                    return Optional.of(limitFinding(InsightKind.GOAL_MET, habit, goal));
                }
            } else if (goal.achieved() >= goal.target()) {
                return Optional.of(targetFinding(InsightKind.GOAL_MET, habit, goal));
            }
        }
        return Optional.empty();
    }

    /** Os dias que ainda podem ser cumpridos: hoje, se ainda não foi cumprido, e os que não chegaram. */
    static int remainingDays(HabitWeek habit) {
        int future = (int) habit.days().stream().filter(day -> day == DayMark.FUTURE).count();
        int elapsed = habit.daysElapsed();
        boolean todayOpen = elapsed > 0 && habit.days().get(elapsed - 1) != DayMark.ACHIEVED;
        return future + (todayOpen ? 1 : 0);
    }

    private static Finding targetFinding(InsightKind kind, HabitWeek habit, WeekGoalScore goal) {
        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("habit", habit.code());
        numbers.put("achieved", goal.achieved());
        numbers.put("target", goal.target());
        return new Finding(kind, null, numbers, Map.of("Habit", habit.name(),
                "achieved", String.valueOf(goal.achieved()), "targetDays", days(goal.target())));
    }

    private static Finding limitFinding(InsightKind kind, HabitWeek habit, WeekGoalScore goal) {
        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("habit", habit.code());
        numbers.put("slips", goal.slips());
        numbers.put("limit", goal.limit());
        return new Finding(kind, InsightTemplates.LIMIT, numbers, Map.of("Habit", habit.name(),
                "slips", String.valueOf(goal.slips()), "slipsDays", days(goal.slips()),
                "limitDays", days(goal.limit())));
    }

    /** {@code 1 dia} ou {@code 4 dias}. */
    private static String days(int count) {
        return count == 1 ? "1 dia" : count + " dias";
    }

    /** O hábito ruim que mais apareceu, se apareceu em {@link #MIN_DAYS} dias ou mais. */
    static Optional<Finding> recurringSlip(WeekFacts facts) {
        return facts.habits().stream()
                .filter(habit -> habit.polarity() == Polarity.BAD)
                .map(habit -> Map.entry(habit, count(habit.days(), DayMark.SLIPPED)))
                .filter(entry -> entry.getValue() >= MIN_DAYS)
                .max(Map.Entry.comparingByValue())
                .map(entry -> habitFinding(InsightKind.RECURRING_SLIP, entry.getKey(), entry.getValue()));
    }

    /** O hábito bom com a maior sequência de dias seguidos cumpridos, se ela tiver {@link #MIN_DAYS} ou mais. */
    static Optional<Finding> streak(WeekFacts facts) {
        return facts.habits().stream()
                .filter(habit -> habit.polarity() == Polarity.GOOD)
                .map(habit -> Map.entry(habit, longestRun(habit.days())))
                .filter(entry -> entry.getValue() >= MIN_DAYS)
                .max(Map.Entry.comparingByValue())
                .map(entry -> habitFinding(InsightKind.STREAK, entry.getKey(), entry.getValue()));
    }

    /**
     * Gasto pelo menos 20% acima da semana anterior nos mesmos dias decorridos (segunda a quarta contra
     * segunda a quarta); só compara quando a anterior teve gasto nesses dias.
     */
    static Optional<Finding> spendingUp(WeekFacts facts) {
        Previous previous = facts.previous();
        if (previous == null || previous.spent().signum() <= 0
                || facts.spent().compareTo(previous.spent().multiply(MIN_INCREASE)) < 0) {
            return Optional.empty();
        }
        long percent = percentAbove(facts.spent(), previous.spent());
        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("spent", facts.spent());
        numbers.put("previous", previous.spent());
        numbers.put("percent", percent);
        numbers.put("comparedDays", previous.comparedDays());
        String upTo = facts.upTo() == null ? "" : " até " + facts.upTo();
        return Optional.of(new Finding(InsightKind.SPENDING_UP, numbers, Map.of(
                "spent", money(facts.spent()), "previous", money(previous.spent()), "percent", String.valueOf(percent),
                "upTo", upTo)));
    }

    /**
     * Gasto médio por dia nos dias de sono ruim (Sono não cumprido) contra os outros dias decorridos.
     * Só aparece com {@link #MIN_DAYS_PER_GROUP} dias ou mais em cada grupo e quando os dias de sono
     * ruim gastaram pelo menos 20% mais. A mensagem cita os números reais.
     */
    static Optional<Finding> sleepAndSpending(WeekFacts facts) {
        List<DayMark> sleep = facts.habits().stream().filter(habit -> SLEEP.equals(habit.code()))
                .findFirst().map(HabitWeek::days).orElse(List.of());
        if (sleep.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal badTotal = BigDecimal.ZERO;
        BigDecimal restTotal = BigDecimal.ZERO;
        int badDays = 0;
        int restDays = 0;
        for (int day = 0; day < facts.spentPerDay().size(); day++) {
            DayMark mark = sleep.get(day);
            BigDecimal spent = facts.spentPerDay().get(day);
            if (mark == DayMark.MISSED || mark == DayMark.SLIPPED) {
                badTotal = badTotal.add(spent);
                badDays++;
            } else {
                restTotal = restTotal.add(spent);
                restDays++;
            }
        }
        if (badDays < MIN_DAYS_PER_GROUP || restDays < MIN_DAYS_PER_GROUP) {
            return Optional.empty();
        }
        BigDecimal avgBad = badTotal.divide(BigDecimal.valueOf(badDays), 2, RoundingMode.HALF_UP);
        BigDecimal avgRest = restTotal.divide(BigDecimal.valueOf(restDays), 2, RoundingMode.HALF_UP);
        boolean higher = avgRest.signum() == 0
                ? avgBad.signum() > 0
                : avgBad.compareTo(avgRest.multiply(MIN_INCREASE)) >= 0;
        if (!higher) {
            return Optional.empty();
        }
        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("badDays", badDays);
        numbers.put("restDays", restDays);
        numbers.put("avgBad", avgBad);
        numbers.put("avgRest", avgRest);
        if (avgRest.signum() > 0) {
            numbers.put("percent", percentAbove(avgBad, avgRest));
        }
        return Optional.of(new Finding(InsightKind.SLEEP_AND_SPENDING, numbers, Map.of(
                "badDays", String.valueOf(badDays), "restDays", String.valueOf(restDays),
                "avgBad", money(avgBad), "avgRest", money(avgRest))));
    }

    private static Finding overview(WeekFacts facts) {
        long achieved = facts.achievedTotal();
        String label = achieved == 1 ? "1 hábito cumprido" : achieved + " hábitos cumpridos";
        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("habitsAchieved", achieved);
        numbers.put("spent", facts.spent());
        return new Finding(InsightKind.OVERVIEW, numbers, Map.of("achieved", label, "spent", money(facts.spent())));
    }

    private static Finding habitFinding(InsightKind kind, HabitWeek habit, int days) {
        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("habit", habit.code());
        numbers.put("days", days);
        return new Finding(kind, numbers, Map.of("Habit", habit.name(), "habit", habit.name().toLowerCase(Locale.ROOT),
                "days", String.valueOf(days)));
    }

    private static int count(List<DayMark> days, DayMark mark) {
        return (int) days.stream().filter(day -> day == mark).count();
    }

    private static int longestRun(List<DayMark> days) {
        int longest = 0;
        int current = 0;
        for (DayMark day : days) {
            current = day == DayMark.ACHIEVED ? current + 1 : 0;
            longest = Math.max(longest, current);
        }
        return longest;
    }

    private static long percentAbove(BigDecimal value, BigDecimal base) {
        return value.divide(base, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static final String[] DAY_NAMES = {"segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo"};

    /** O dia da semana por extenso e em minúsculas: {@code quarta}, {@code sábado}. */
    static String dayName(LocalDate day) {
        return DAY_NAMES[day.getDayOfWeek().getValue() - 1];
    }

    /** {@code 1250.9} → {@code R$ 1.250,90}, com espaço comum depois do símbolo. */
    static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value).replace(' ', ' ');
    }

    /**
     * Uma regra que disparou.
     *
     * @param variant a variante do texto ({@link InsightTemplates#LIMIT}) ou {@code null} para o padrão
     * @param numbers os números para a resposta da API
     * @param text    os valores já formatados para os marcadores do texto
     */
    record Finding(InsightKind kind, String variant, Map<String, Object> numbers, Map<String, String> text) {

        /** Uma regra com o texto padrão do tipo. */
        Finding(InsightKind kind, Map<String, Object> numbers, Map<String, String> text) {
            this(kind, null, numbers, text);
        }
    }
}
