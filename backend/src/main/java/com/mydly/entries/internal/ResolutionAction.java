package com.mydly.entries.internal;

/**
 * O que o usuário fez com um item que precisava de confirmação.
 */
public enum ResolutionAction {

    /** Confirmou como finança, informando valor, direção e categoria. */
    CONFIRM_FINANCE,

    /** Confirmou como rotina, com ou sem quantidade. */
    CONFIRM_ROUTINE,

    /** Disse que não era nada a registrar ({@code Não foi um gasto}). O texto original fica guardado. */
    DISMISS
}
