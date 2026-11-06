package com.mydly.entries.internal;

/**
 * O que o usuário fez com um item: confirmou ou descartou um pendente, ou reabriu um já resolvido.
 */
public enum ResolutionAction {

    /** Confirmou como finança, informando valor, direção e categoria. */
    CONFIRM_FINANCE,

    /** Confirmou como rotina, com ou sem quantidade. */
    CONFIRM_ROUTINE,

    /** Disse que não era nada a registrar ({@code Não foi um gasto}). O texto original fica guardado. */
    DISMISS,
    /**
     * {@code Não foi isso} ou {@code Desfazer}: um item aceito ou descartado volta a esperar confirmação
     * (ADR 0008). Não é gravado como resolução; apaga a que existia.
     */
    REOPEN
}
