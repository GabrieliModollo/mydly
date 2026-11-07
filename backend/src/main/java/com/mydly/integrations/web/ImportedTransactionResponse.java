package com.mydly.integrations.web;

import com.mydly.entries.Direction;
import com.mydly.integrations.internal.ImportedTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Uma transação importada, como o app mostra. */
@Schema(description = "Transação importada de um extrato")
record ImportedTransactionResponse(
        UUID id,
        @Schema(example = "2026-11-05") LocalDate date,
        @Schema(example = "50.00") BigDecimal amount,
        Direction direction,
        @Schema(description = "O texto do banco", example = "DROGASIL 1234") String description,
        @Schema(description = "IMPORTED (conta nos números), MATCHED (é o mesmo de um lançamento) ou IGNORED")
        ImportedTransaction.Status status,
        @Schema(description = "O lançamento com que casou, em MATCHED") UUID matchedEntryId,
        @Schema(description = "O item desse lançamento") Integer matchedItemIndex) {

    static ImportedTransactionResponse of(ImportedTransaction transaction) {
        return new ImportedTransactionResponse(transaction.id(), transaction.date(), transaction.amount(),
                transaction.direction(), transaction.description(), transaction.status(), transaction.matchedEntryId(),
                transaction.matchedItemIndex());
    }
}
