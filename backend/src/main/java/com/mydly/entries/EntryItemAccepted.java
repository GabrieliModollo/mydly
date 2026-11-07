package com.mydly.entries;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Um item de lançamento passou a valer: o parser teve confiança suficiente ou o usuário confirmou.
 * Os módulos {@code finance} e {@code routines} registram o item a partir deste evento (ADR 0008).
 *
 * <p>O evento pode chegar mais de uma vez ou fora de ordem, quando o registro de publicação reenvia.
 * Por isso traz a revisão do item: o consumidor guarda a revisão que aplicou e ignora as menores ou iguais.
 *
 * @param userId     o dono do lançamento (ADR 0012); quem registra grava junto
 * @param entryId    o lançamento
 * @param itemIndex  a posição do item no lançamento
 * @param revision   a revisão do item; cresce a cada mudança de estado
 * @param occurredAt quando o lançamento aconteceu
 * @param item       o item efetivo; com a confirmação do usuário quando houver
 */
public record EntryItemAccepted(UUID userId, UUID entryId, int itemIndex, long revision, Instant occurredAt, ParsedEntry item) {

    public EntryItemAccepted {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(item, "item");
    }
}
