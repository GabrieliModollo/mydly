package com.mydly.entries;

import java.util.Objects;
import java.util.UUID;

/**
 * Um item que valia deixou de valer: o usuário tocou em {@code Não foi isso} e o item voltou a
 * esperar confirmação (ADR 0008). Quem registrou o item marca o registro como inativo, sem apagar,
 * para que um {@link EntryItemAccepted} antigo que chegue atrasado não o ressuscite.
 *
 * @param userId    o dono do lançamento (ADR 0012)
 * @param entryId   o lançamento
 * @param itemIndex a posição do item no lançamento
 * @param revision  a revisão do item depois da retirada
 */
public record EntryItemRetracted(UUID userId, UUID entryId, int itemIndex, long revision) {

    public EntryItemRetracted {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(entryId, "entryId");
    }
}
