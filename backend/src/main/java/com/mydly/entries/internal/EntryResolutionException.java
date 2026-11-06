package com.mydly.entries.internal;

/**
 * Uma resolução de item que não pode ser aplicada. O motivo diz qual resposta a API devolve.
 */
public class EntryResolutionException extends RuntimeException {

    public enum Reason {
        /** Não existe lançamento com esse id. */
        ENTRY_NOT_FOUND,
        /** O lançamento não tem item nessa posição. */
        ITEM_NOT_FOUND,
        /** O lançamento mudou desde que o cliente o leu. */
        VERSION_MISMATCH,
        /** O item já foi resolvido ou nunca precisou de confirmação. */
        NOT_PENDING,
        /** Pediu para reabrir um item que já espera confirmação. */
        ALREADY_PENDING,
        /** Os dados enviados não formam uma resolução válida. */
        INVALID
    }

    private final Reason reason;

    EntryResolutionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
