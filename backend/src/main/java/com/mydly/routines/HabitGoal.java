package com.mydly.routines;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Uma versão da meta de um hábito, que vale de {@code validFrom} até a próxima versão (ADR 0010).
 *
 * @param habitCode      o hábito do catálogo
 * @param validFrom      o primeiro dia em que esta versão vale
 * @param kind           o tipo; {@link GoalKind#NONE} é a versão sem meta
 * @param targetQuantity em {@link GoalKind#DAILY_QUANTITY}: a quantidade por dia, em {@code unit}
 * @param unit           em {@link GoalKind#DAILY_QUANTITY}: {@code L}, {@code mL}, {@code h} ou {@code min}
 * @param daysPerWeek    em {@link GoalKind#WEEKLY_FREQUENCY}: dias por semana; num hábito ruim é o limite
 */
public record HabitGoal(String habitCode, LocalDate validFrom, GoalKind kind, BigDecimal targetQuantity, String unit,
                        Integer daysPerWeek) {

    public HabitGoal {
        Objects.requireNonNull(habitCode, "habitCode");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(kind, "kind");
    }

    /** {@code true} quando esta versão tem meta (não é a versão de remoção). */
    public boolean active() {
        return kind != GoalKind.NONE;
    }
}
