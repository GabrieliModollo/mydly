package com.mydly.entries;

/**
 * Tipo de um lançamento interpretado.
 */
public enum EntryType {

    /** Gasto ou receita, com valor monetário. */
    FINANCE,

    /** Hábito ou registro de rotina, com ou sem quantidade. */
    ROUTINE,

    /** O parser não conseguiu decidir; o usuário (ou o LLM) precisa esclarecer. */
    UNKNOWN
}
