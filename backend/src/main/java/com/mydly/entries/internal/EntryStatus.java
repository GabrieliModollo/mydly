package com.mydly.entries.internal;

/**
 * Situação de um lançamento. Os valores batem com a restrição {@code chk_entry_status} da tabela.
 */
public enum EntryStatus {

    /** Interpretado com confiança suficiente, aguardando os módulos de domínio. */
    PENDING,

    /** Registrado em {@code finance} ou {@code routines}. */
    CONFIRMED,

    /** Confiança baixa: o app precisa perguntar ao usuário (ou ao LLM) antes de registrar. */
    NEEDS_INPUT
}
