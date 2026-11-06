package com.mydly.entries.internal;

import java.util.Objects;

/**
 * Estado de um item, gravado na coluna {@code item_state} alinhado com os itens do parser (ADR 0008).
 *
 * @param state          em que pé o item está
 * @param revision       começa em 1 e cresce a cada mudança de estado; vai nos eventos
 * @param parserRejected o usuário reabriu um item que o parser tinha aceitado sozinho; base para
 *                       medir e melhorar o parser
 */
public record ItemStatus(ItemState state, long revision, boolean parserRejected) {

    public ItemStatus {
        Objects.requireNonNull(state, "state");
        if (revision < 1) {
            throw new IllegalArgumentException("a revisão começa em 1: " + revision);
        }
    }

    static ItemStatus initial(ItemState state) {
        return new ItemStatus(state, 1, false);
    }

    ItemStatus next(ItemState newState, boolean rejected) {
        return new ItemStatus(newState, revision + 1, parserRejected || rejected);
    }
}
