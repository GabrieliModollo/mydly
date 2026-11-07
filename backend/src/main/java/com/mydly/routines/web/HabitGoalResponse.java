package com.mydly.routines.web;

import com.mydly.routines.GoalKind;
import com.mydly.routines.HabitGoal;
import com.mydly.routines.Measure;
import com.mydly.routines.Polarity;
import com.mydly.routines.internal.HabitGoalService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Um hábito do catálogo e a meta com efeito hoje, como a API devolve.
 */
@Schema(description = "Hábito do catálogo com a meta vigente hoje")
record HabitGoalResponse(
        @Schema(description = "Código do hábito", example = "HYDRATION") String habit,
        @Schema(description = "Nome do hábito", example = "Hidratação") String name,
        Polarity polarity,
        @Schema(description = "Dimensão de medida: VOLUME, DURATION ou NONE") Measure measure,
        @Schema(description = "Tipos de meta aceitos pelo hábito") List<GoalKind> allowedKinds,
        @Schema(description = "Unidades aceitas na meta diária; vazio quando o hábito não aceita meta diária")
        List<String> allowedUnits,
        @Schema(description = "A meta vigente hoje; ausente quando o hábito está sem meta") Goal goal) {

    static HabitGoalResponse of(HabitGoalService.CurrentGoal current) {
        var habit = current.habit();
        boolean daily = habit.polarity() == Polarity.GOOD && habit.measure() != Measure.NONE;
        List<GoalKind> kinds = daily ? List.of(GoalKind.DAILY_QUANTITY, GoalKind.WEEKLY_FREQUENCY)
                : List.of(GoalKind.WEEKLY_FREQUENCY);
        List<String> units = !daily ? List.of()
                : habit.measure() == Measure.VOLUME ? List.of("L", "mL") : List.of("h", "min");
        return new HabitGoalResponse(habit.code(), habit.name(), habit.polarity(), habit.measure(), kinds, units,
                current.goal().map(Goal::of).orElse(null));
    }

    @Schema(description = "Meta de um hábito; num hábito ruim, WEEKLY_FREQUENCY é o limite de dias")
    record Goal(
            @Schema(description = "DAILY_QUANTITY ou WEEKLY_FREQUENCY") GoalKind kind,
            @Schema(description = "DAILY_QUANTITY: quantidade por dia", example = "2") BigDecimal targetQuantity,
            @Schema(description = "DAILY_QUANTITY: L, mL, h ou min", example = "L") String unit,
            @Schema(description = "WEEKLY_FREQUENCY: dias por semana (no máximo, num hábito ruim)") Integer daysPerWeek,
            @Schema(description = "Desde quando esta versão da meta vale") LocalDate validFrom) {

        static Goal of(HabitGoal goal) {
            return new Goal(goal.kind(), goal.targetQuantity(), goal.unit(), goal.daysPerWeek(), goal.validFrom());
        }
    }
}
