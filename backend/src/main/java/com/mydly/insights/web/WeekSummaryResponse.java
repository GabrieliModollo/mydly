package com.mydly.insights.web;

import com.mydly.entries.Category;
import com.mydly.finance.BudgetPeriod;
import com.mydly.finance.BudgetProgress;
import com.mydly.finance.BudgetStatus;
import com.mydly.insights.internal.InsightKind;
import com.mydly.insights.internal.Tone;
import com.mydly.insights.internal.WeekSummary;
import com.mydly.routines.GoalKind;
import com.mydly.routines.Polarity;
import com.mydly.routines.WeekGoalScore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Resumo da semana como a API devolve.
 */
@Schema(description = "Resumo da semana")
record WeekSummaryResponse(
        @Schema(description = "O primeiro dia da semana") LocalDate weekStart,
        @Schema(description = "O último dia da semana") LocalDate weekEnd,
        @Schema(description = "Dias que já começaram: 7 numa semana passada, de 1 a 7 na atual") int daysElapsed,
        @Schema(description = "Tom usado nas mensagens") String tone,
        @Schema(description = "Os hábitos com algum registro na semana e, depois deles, os com meta sem registro")
        List<HabitRow> habits,
        Money money,
        @Schema(description = "As categorias com orçamento no dia de referência: o limite e o gasto do período. "
                + "O semanal é a própria semana; o mensal vai do dia 1 do mês até o dia de referência")
        List<BudgetView> budgets,
        @Schema(description = "A semana anterior nos mesmos dias decorridos; ausente quando ela não tem dados")
        PreviousWeek previous,
        @Schema(description = "No máximo três mensagens; a primeira é a principal") List<InsightView> insights) {

    static WeekSummaryResponse of(WeekSummary summary, Tone tone) {
        return new WeekSummaryResponse(summary.weekStart(), summary.weekEnd(), summary.daysElapsed(), tone.parameter(),
                summary.habits().stream().map(HabitRow::of).toList(),
                new Money(summary.money().spent(), summary.money().received(),
                        summary.money().spentByCategory().stream()
                                .map(total -> new CategoryAmount(total.category(), total.amount())).toList()),
                summary.budgets().stream().map(BudgetView::of).toList(),
                summary.previous() == null ? null
                        : new PreviousWeek(summary.previous().spent(), summary.previous().habitsAchieved(),
                                summary.previous().comparedDays()),
                summary.insights().stream()
                        .map(insight -> new InsightView(insight.kind(), insight.text(), insight.numbers())).toList());
    }

    @Schema(description = "Um hábito na semana, com os 7 dias a partir do primeiro dia da semana")
    record HabitRow(String code, String name, Polarity polarity,
                    @Schema(description = "ACHIEVED, SLIPPED, MISSED, NONE (sem registro) ou FUTURE")
                    List<WeekSummary.DayMark> days,
                    Score score) {

        static HabitRow of(WeekSummary.HabitWeek habit) {
            WeekGoalScore goal = habit.goal();
            Score score = goal == null
                    ? new Score(habit.achievedDays(), habit.daysElapsed(), null, null, null, null)
                    : new Score(goal.achieved(), habit.daysElapsed(), goal.target(), goal.limit(), goal.slips(),
                            goal.kind());
            return new HabitRow(habit.code(), habit.name(), habit.polarity(), habit.days(), score);
        }
    }

    @Schema(description = "Placar do hábito; com meta, traz também o alvo, ou o limite e os deslizes num hábito ruim")
    record Score(
            @Schema(description = "Sem meta ou com meta de dias: dias cumpridos (bom cumprido ou ruim evitado). "
                    + "Com meta diária: dias em que a quantidade bateu a meta do dia") int achieved,
            @Schema(description = "Dias decorridos") int elapsedDays,
            @Schema(description = "Meta de dias por semana; com meta diária, os dias da semana com meta. Ausente sem "
                    + "meta e no limite de hábito ruim") Integer target,
            @Schema(description = "Limite de hábito ruim: no máximo este número de dias de deslize") Integer limit,
            @Schema(description = "Limite de hábito ruim: dias de deslize na semana") Integer slips,
            @Schema(description = "Tipo da meta da semana (a vigente no dia de referência); ausente sem meta")
            GoalKind goal) {
    }

    @Schema(description = "Gastos e entradas da semana")
    record Money(BigDecimal spent, BigDecimal received, List<CategoryAmount> spentByCategory) {
    }

    @Schema(description = "Saídas de uma categoria; categoria ausente = sem categoria")
    record CategoryAmount(Category category, BigDecimal amount) {
    }

    @Schema(description = "O orçamento de uma categoria na semana")
    record BudgetView(Category category, BudgetPeriod period, BigDecimal limit,
                      @Schema(description = "Primeiro dia do período avaliado") LocalDate periodStart,
                      @Schema(description = "Último dia do período avaliado, inclusive") LocalDate periodEnd,
                      @Schema(description = "Saídas ativas da categoria no período") BigDecimal spent,
                      @Schema(description = "Gasto em porcentagem do limite, arredondado para baixo") int percent,
                      @Schema(description = "OK, NEAR (80% a 100%) ou OVER (acima do limite)") BudgetStatus status) {

        static BudgetView of(BudgetProgress progress) {
            return new BudgetView(progress.category(), progress.period(), progress.limit(), progress.periodStart(),
                    progress.periodEnd(), progress.spent(), progress.percent(), progress.status());
        }
    }

    @Schema(description = "Totais da semana anterior nos mesmos dias decorridos, para a comparação")
    record PreviousWeek(BigDecimal spent, long habitsAchieved,
                        @Schema(description = "Dias comparados a partir do início (7 numa semana completa)")
                        int comparedDays) {
    }

    @Schema(description = "Uma mensagem da semana e os números que a sustentam")
    record InsightView(InsightKind kind, String text, Map<String, Object> numbers) {
    }
}
