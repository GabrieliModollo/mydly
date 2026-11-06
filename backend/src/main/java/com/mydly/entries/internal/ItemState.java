package com.mydly.entries.internal;

/**
 * Em que pé está um item (ADR 0008).
 */
public enum ItemState {
    /** Esperando o usuário confirmar ou descartar. */
    PENDING,
    /** Aceito sem perguntar: o parser teve confiança suficiente. */
    ACCEPTED_AUTO,
    /** Aceito pelo usuário no painel de confirmação. */
    ACCEPTED_BY_USER,
    /** O usuário disse que não era nada a registrar. */
    DISMISSED;

    boolean isAccepted() {
        return this == ACCEPTED_AUTO || this == ACCEPTED_BY_USER;
    }
}
