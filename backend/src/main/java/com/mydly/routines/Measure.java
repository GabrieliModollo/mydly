package com.mydly.routines;

/**
 * Dimensão de medida de um hábito do catálogo. Só hábitos com medida aceitam meta diária de
 * quantidade, e a unidade da meta precisa ser da mesma dimensão.
 */
public enum Measure {
    /** Volume: {@code L} e {@code mL} (Hidratação). */
    VOLUME,
    /** Duração: {@code h} e {@code min} (Estudo, Sono, Leitura, Academia). */
    DURATION,
    /** Sem medida: o hábito só aceita meta de dias por semana. */
    NONE
}
