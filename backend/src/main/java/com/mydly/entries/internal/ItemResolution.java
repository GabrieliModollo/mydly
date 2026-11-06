package com.mydly.entries.internal;

import com.mydly.entries.ParsedEntry;
import java.util.Objects;

/**
 * A resposta do usuário para um item. É gravada na coluna {@code resolution}, sem tocar no
 * que o parser entendeu (ADR 0007).
 *
 * @param action o que o usuário fez
 * @param item   o item como o usuário confirmou; {@code null} quando ele descartou
 */
public record ItemResolution(ResolutionAction action, ParsedEntry item) {

    public ItemResolution {
        Objects.requireNonNull(action, "action");
        if ((action == ResolutionAction.DISMISS) != (item == null)) {
            throw new IllegalArgumentException("só o descarte vem sem item");
        }
    }

    static ItemResolution dismissed() {
        return new ItemResolution(ResolutionAction.DISMISS, null);
    }

    static ItemResolution confirmed(ParsedEntry item) {
        ResolutionAction action = switch (item.type()) {
            case FINANCE -> ResolutionAction.CONFIRM_FINANCE;
            case ROUTINE -> ResolutionAction.CONFIRM_ROUTINE;
            case UNKNOWN -> throw new IllegalArgumentException("não se confirma um item como desconhecido");
        };
        return new ItemResolution(action, item);
    }
}
