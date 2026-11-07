package com.mydly.insights.internal;

import com.mydly.insights.internal.WeekSummary.DayMark;
import com.mydly.insights.internal.WeekSummary.HabitWeek;
import com.mydly.insights.internal.WeekSummary.Insight;
import com.mydly.insights.internal.WeekSummary.Previous;
import com.mydly.routines.Polarity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Regras das mensagens da semana, sem LLM (ADR 0009). Cada regra só dispara acima de um limiar, e o
 * cruzamento de sono com gastos exige pelo menos {@link #MIN_DAYS_PER_GROUP} dias em cada grupo: com
 * menos que isso a diferença pode ser acaso, e é melhor nenhuma mensagem do que uma falsa.
 */
final class WeekInsights {

    static final int MIN_DAYS = 3;
    static final int MIN_DAYS_PER_GROUP = 3;
    static final BigDecimal MIN_INCREASE = new BigDecimal("1.20");
    static final int MAX_INSIGHTS = 3;
    static final String SLEEP = "SLEEP";

    private WeekInsights() {
    }

    /**
     * O que a semana teve, já só com os dias decorridos.
     *
     * @param habits      os hábitos com algum registro na semana
     * @param spent       gasto da semana até o último dia decorrido
     * @param previous    a semana anterior, ou {@code null} sem dados
     * @param spentPerDay gasto de cada dia decorrido, na ordem
     * @param hasData     a semana tem algum registro, mesmo fora do catálogo
     * @param upTo        o último dia decorrido por extenso ({@code quarta}) numa semana em andamento, ou
     *                    {@code null} numa semana completa
     */
    record WeekFacts(List<HabitWeek> habits, BigDecimal spent, Previous previous, List<BigDecimal> spentPerDay,
                     boolean hasData, String upTo) {

        long achievedTotal() {
            return habits.stream().mapToLong(HabitWeek::achievedDays).sum();
        }
    }

    /** As mensagens da semana no tom pedido: no máximo três, a primeira é a principal. */
    static List<Insight> evaluate(WeekFacts facts, Tone tone) {
        List<Finding> findings = new ArrayList<>();
        recurringSlip(facts).ifPresent(findings::add);
        spendingUp(facts).ifPresent(findings::add);
        sleepAndSpending(facts).ifPresent(findings::add);
        streak(facts).ifPresent(findings::add);
        if (findings.isEmpty()) {
            findings.add(facts.hasData() ? overview(facts) : new Finding(InsightKind.WELCOME, Map.of(), Map.of()));
        }
        findings.sort(Comparator.comparing(Finding::kind));
        return findings.stream().limit(MAX_INSIGHTS)
                .map(finding -> new Insight(finding.kind(),
                        InsightTemplates.render(finding.kind(), tone, finding.text()), finding.numbers()))
                .toList();
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
    static String dayName(java.time.LocalDate day) {
        return DAY_NAMES[day.getDayOfWeek().getValue() - 1];
    }

    /** {@code 1250.9} → {@code R$ 1.250,90}, com espaço comum depois do símbolo. */
    static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value).replace(' ', ' ');
    }

    /**
     * Uma regra que disparou.
     *
     * @param numbers os números para a resposta da API
     * @param text    os valores já formatados para os marcadores do texto
     */
    record Finding(InsightKind kind, Map<String, Object> numbers, Map<String, String> text) {
    }
}
