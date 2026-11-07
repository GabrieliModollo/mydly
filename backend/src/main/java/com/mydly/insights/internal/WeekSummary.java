package com.mydly.insights.internal;

import com.mydly.finance.BudgetProgress;
import com.mydly.finance.PeriodTotals;
import com.mydly.routines.Polarity;
import com.mydly.routines.WeekGoalScore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * O resumo de uma semana de 7 dias, no fuso do usuário.
 *
 * @param weekStart   o primeiro dia da semana, no dia de início das preferências
 * @param daysElapsed dias da semana que já começaram: 7 numa semana passada, de 1 a 7 na atual, 0 numa futura
 * @param habits      os hábitos que tiveram algum registro na semana e, depois deles, os que têm meta
 *                    sem nenhum registro
 * @param money       gastos e entradas da semana
 * @param budgets     o gasto de cada categoria com orçamento contra o limite (ADR 0010)
 * @param previous    a semana anterior nos mesmos dias decorridos, ou {@code null} quando ela não tem nenhum dado
 * @param insights    no máximo três mensagens; a primeira é a principal
 */
public record WeekSummary(LocalDate weekStart, int daysElapsed, List<HabitWeek> habits, PeriodTotals money,
                          List<BudgetProgress> budgets, Previous previous, List<Insight> insights) {

    public WeekSummary {
        habits = List.copyOf(habits);
        budgets = List.copyOf(budgets);
        insights = List.copyOf(insights);
    }

    public LocalDate weekEnd() {
        return weekStart.plusDays(6);
    }

    /** Como um dia ficou para um hábito. */
    public enum DayMark {
        ACHIEVED,
        SLIPPED,
        MISSED,
        /** O dia já começou e não teve registro desse hábito. */
        NONE,
        /** O dia ainda não chegou. */
        FUTURE
    }

    /**
     * Um hábito na semana: os 7 dias, a partir do primeiro dia da semana, o placar de dias cumpridos
     * sobre os dias decorridos e, quando há meta, o placar contra ela.
     *
     * @param goal o placar contra a meta da semana, ou {@code null} sem meta
     */
    public record HabitWeek(String code, String name, Polarity polarity, List<DayMark> days, int achievedDays,
                            int daysElapsed, WeekGoalScore goal) {

        public HabitWeek {
            days = List.copyOf(days);
        }

        /** Um hábito sem meta. */
        public HabitWeek(String code, String name, Polarity polarity, List<DayMark> days, int achievedDays,
                         int daysElapsed) {
            this(code, name, polarity, days, achievedDays, daysElapsed, null);
        }
    }

    /**
     * O que a semana anterior teve nos mesmos dias decorridos da atual, para a comparação ser justa.
     *
     * @param comparedDays quantos dias, a partir do início, entraram na conta (7 numa semana completa)
     */
    public record Previous(BigDecimal spent, long habitsAchieved, int comparedDays) {
    }

    /**
     * Uma mensagem da semana.
     *
     * @param kind    o tipo, que o app usa para escolher o cartão
     * @param text    a mensagem no tom pedido
     * @param numbers os números que sustentam a mensagem, para o app mostrar e os testes conferirem
     */
    public record Insight(InsightKind kind, String text, Map<String, Object> numbers) {

        public Insight {
            numbers = Map.copyOf(numbers);
        }
    }
}
