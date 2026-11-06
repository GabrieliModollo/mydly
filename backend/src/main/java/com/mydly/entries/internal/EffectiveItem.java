package com.mydly.entries.internal;

import com.mydly.entries.ParsedEntry;

/**
 * Um item como ele vale agora: a resolução do usuário, se houver, senão o que o parser entendeu.
 *
 * @param item   o item efetivo; num descarte ou num item pendente, é o do parser
 * @param status o estado e a revisão do item
 */
public record EffectiveItem(ParsedEntry item, ItemStatus status) {

    /** O usuário confirmou ou descartou este item. */
    public boolean resolvedByUser() {
        return status.state() == ItemState.ACCEPTED_BY_USER || status.state() == ItemState.DISMISSED;
    }

    /** O usuário disse que não era nada a registrar. */
    public boolean dismissed() {
        return status.state() == ItemState.DISMISSED;
    }

    /** Ainda falta o usuário confirmar. */
    public boolean needsConfirmation() {
        return status.state() == ItemState.PENDING;
    }

    /** O item pode voltar a esperar confirmação: {@code Não foi isso} ou {@code Desfazer} no app. */
    public boolean canReopen() {
        return status.state() != ItemState.PENDING;
    }
}
