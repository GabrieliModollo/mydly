package com.mydly.integrations.internal;

import com.mydly.entries.Direction;
import com.mydly.finance.TransactionOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma transação externa já importada ({@code external_transaction}).
 *
 * @param matchedEntryId   o lançamento manual com que casou, em {@link Status#MATCHED}
 * @param matchedItemIndex o item desse lançamento
 * @param revision         cresce a cada mudança de status; vai para {@code finance_transaction}
 */
public record ImportedTransaction(UUID id, UUID userId, TransactionOrigin source, String externalId, LocalDate date,
                                  BigDecimal amount, Direction direction, String description, Status status,
                                  UUID matchedEntryId, Integer matchedItemIndex, long revision) {

    public enum Status {
        /** Conta nos números do usuário. */
        IMPORTED,
        /** É o mesmo gasto de um lançamento manual, e não conta de novo. */
        MATCHED,
        /** O usuário pediu para não contar. */
        IGNORED
    }

    /** {@code true} quando a transação entra nos números como ela mesma. */
    boolean counts() {
        return status == Status.IMPORTED;
    }

    ImportedTransaction with(Status newStatus, UUID entryId, Integer itemIndex) {
        return new ImportedTransaction(id, userId, source, externalId, date, amount, direction, description, newStatus,
                entryId, itemIndex, revision + 1);
    }
}
