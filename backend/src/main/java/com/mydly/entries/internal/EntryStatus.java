package com.mydly.entries.internal;

/**
 * Situação de um lançamento. Os valores batem com a restrição {@code chk_entry_status} da tabela.
 */
public enum EntryStatus {
    /** Todos os itens valem ou foram descartados; os que valem já seguiram para {@code finance} e {@code routines}. */
    CONFIRMED,
    /** Algum item espera o usuário (ou o LLM) confirmar. */
    NEEDS_INPUT
}
