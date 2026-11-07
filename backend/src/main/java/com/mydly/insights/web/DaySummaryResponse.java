package com.mydly.insights.web;

import com.mydly.entries.Category;
import com.mydly.finance.BudgetPeriod;
import com.mydly.finance.BudgetProgress;
import com.mydly.finance.BudgetStatus;
import com.mydly.finance.PeriodTotals;
import com.mydly.routines.DailyGoalProgress;
import com.mydly.routines.DayHabits;
import com.mydly.routines.HabitStatus;
import com.mydly.routines.Polarity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resumo de um dia como a API devolve.
 */
@Schema(description = "Resumo do dia")
record DaySummaryResponse(
        LocalDate date,
        @Schema(description = "Soma das saídas ativas, em reais") BigDecimal spent,
        @Schema(description = "Soma das entradas ativas, em reais") BigDecimal received,
        @Schema(description = "Hábitos bons cumpridos mais hábitos ruins evitados") long habitsAchieved,
        @Schema(description = "Hábitos ruins consumidos") long slips,
        @Schema(description = "Saídas por categoria, da maior para a menor") List<CategoryAmount> spentByCategory,
        @Schema(description = "Hábito de cada item de rotina do dia, para o chip do app") List<ItemHabit> items,
        @Schema(description = "Progresso de cada meta diária de quantidade vigente no dia, mesmo sem registro")
        List<DailyGoal> dailyGoals,
        @Schema(description = "Categorias que chegaram a 80% do limite no período que contém o dia")
        List<BudgetAlert> budgetAlerts) {

    static DaySummaryResponse of(LocalDate date, PeriodTotals totals, DayHabits habits, List<DailyGoalProgress> goals,
                                 List<BudgetProgress> alerts) {
        return new DaySummaryResponse(date, totals.spent(), totals.received(), habits.achieved(), habits.slipped(),
                totals.spentByCategory().stream().map(t -> new CategoryAmount(t.category(), t.amount())).toList(),
                habits.logs().stream().map(ItemHabit::of).toList(),
                goals.stream().map(DailyGoal::of).toList(),
                alerts.stream().map(BudgetAlert::of).toList());
    }

    @Schema(description = "Saídas de uma categoria; categoria ausente = sem categoria")
    record CategoryAmount(Category category, BigDecimal amount) {
    }

    @Schema(description = "Item de rotina e o hábito do catálogo; hábito ausente = outros, fora dos indicadores")
    record ItemHabit(UUID entryId, int itemIndex, String habit, String habitName, Polarity polarity,
                     HabitStatus status) {

        static ItemHabit of(DayHabits.HabitLog log) {
            return new ItemHabit(log.entryId(), log.itemIndex(), log.code(), log.name(), log.polarity(), log.status());
        }
    }

    @Schema(description = "Meta diária de quantidade, como 1,5 de 2 L")
    record DailyGoal(@Schema(example = "HYDRATION") String habit,
                     @Schema(example = "Hidratação") String habitName,
                     @Schema(description = "Quantidade da meta", example = "2") BigDecimal target,
                     @Schema(description = "L, mL, h ou min", example = "L") String unit,
                     @Schema(description = "Soma do dia na unidade da meta, arredondada para baixo em duas casas",
                             example = "1.5") BigDecimal done,
                     @Schema(description = "A soma do dia chegou à meta") boolean met) {

        static DailyGoal of(DailyGoalProgress progress) {
            return new DailyGoal(progress.habitCode(), progress.habitName(), progress.target(), progress.unit(),
                    progress.done(), progress.met());
        }
    }

    @Schema(description = "Categoria em aviso (NEAR, de 80% a 100%) ou acima do limite (OVER)")
    record BudgetAlert(Category category, BudgetPeriod period, BigDecimal limit, BigDecimal spent,
                       @Schema(description = "Gasto em porcentagem do limite, arredondado para baixo") int percent,
                       BudgetStatus status) {

        static BudgetAlert of(BudgetProgress progress) {
            return new BudgetAlert(progress.category(), progress.period(), progress.limit(), progress.spent(),
                    progress.percent(), progress.status());
        }
    }
}
