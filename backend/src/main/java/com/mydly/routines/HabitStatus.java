package com.mydly.routines;

import com.mydly.entries.RoutineOutcome;

/**
 * Como um hábito ficou, já levando em conta a polaridade.
 */
public enum HabitStatus {
    /** Hábito bom cumprido ou hábito ruim evitado: conta em {@code Hábitos cumpridos}. */
    ACHIEVED,
    /** Hábito ruim consumido: um deslize, que alimenta as broncas. */
    SLIPPED,
    /** Hábito bom não cumprido. */
    MISSED;

    /**
     * {@code DONE} quer dizer que a ação aconteceu: num hábito ruim isso é deslize, e {@code MISSED}
     * (não aconteceu) é conquista. Assim {@code Não comi besteira} e {@code Deixei de fumar} contam a favor.
     */
    public static HabitStatus of(Polarity polarity, RoutineOutcome outcome) {
        boolean happened = outcome == RoutineOutcome.DONE;
        if (polarity == Polarity.GOOD) {
            return happened ? ACHIEVED : MISSED;
        }
        return happened ? SLIPPED : ACHIEVED;
    }
}
