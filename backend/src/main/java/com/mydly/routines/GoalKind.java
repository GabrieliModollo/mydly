package com.mydly.routines;

/**
 * Tipo de uma versão de meta de hábito (ADR 0010).
 */
public enum GoalKind {
    /** Quantidade por dia, como 2 L de água: o dia é cumprido quando a soma do dia chega ao alvo. */
    DAILY_QUANTITY,
    /** Dias por semana, como academia 4 vezes. Num hábito ruim é o limite: no máximo n dias. */
    WEEKLY_FREQUENCY,
    /** Versão sem meta: grava a remoção sem apagar o histórico. */
    NONE
}
