package com.mydly.entries;

/**
 * Se o hábito de uma rotina foi cumprido ou não.
 */
public enum RoutineOutcome {

    /** O hábito aconteceu: {@code fui à academia}, {@code bebi 2 litros de água}. É o padrão. */
    DONE,

    /** O hábito não aconteceu: {@code não fui à academia}, {@code esqueci de beber água}, {@code dormi mal}. */
    MISSED
}
