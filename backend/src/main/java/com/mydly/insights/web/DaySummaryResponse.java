package com.mydly.insights.web;

import com.mydly.entries.Category;
import com.mydly.finance.PeriodTotals;
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
        @Schema(description = "Hábito de cada item de rotina do dia, para o chip do app") List<ItemHabit> items) {

    static DaySummaryResponse of(LocalDate date, PeriodTotals totals, DayHabits habits) {
        return new DaySummaryResponse(date, totals.spent(), totals.received(), habits.achieved(), habits.slipped(),
                totals.spentByCategory().stream().map(t -> new CategoryAmount(t.category(), t.amount())).toList(),
                habits.logs().stream().map(ItemHabit::of).toList());
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
}
