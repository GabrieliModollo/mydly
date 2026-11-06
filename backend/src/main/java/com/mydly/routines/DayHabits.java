package com.mydly.routines;

import java.util.List;
import java.util.UUID;

/**
 * Hábitos de um dia: o status de cada hábito do catálogo que apareceu e cada registro com o seu hábito.
 *
 * @param habits um por hábito do catálogo que apareceu no dia
 * @param logs   um por item de rotina ativo, inclusive os fora do catálogo
 */
public record DayHabits(List<HabitDay> habits, List<HabitLog> logs) {

    public DayHabits {
        habits = List.copyOf(habits);
        logs = List.copyOf(logs);
    }

    /** Hábitos bons cumpridos mais hábitos ruins evitados. */
    public long achieved() {
        return habits.stream().filter(habit -> habit.status() == HabitStatus.ACHIEVED).count();
    }

    /** Hábitos ruins consumidos. */
    public long slipped() {
        return habits.stream().filter(habit -> habit.status() == HabitStatus.SLIPPED).count();
    }

    /**
     * Um hábito no dia. Um hábito bom fica cumprido se algum registro o cumpriu; um ruim vira deslize
     * se algum registro o consumiu.
     */
    public record HabitDay(String code, String name, Polarity polarity, HabitStatus status) {
    }

    /**
     * Um item de rotina e o hábito em que ele caiu. Sem hábito ({@code code} nulo) é {@code outros} e
     * fica fora dos indicadores.
     */
    public record HabitLog(UUID entryId, int itemIndex, String code, String name, Polarity polarity,
                           HabitStatus status) {
    }
}
