package com.mydly.entries.web;

import com.mydly.entries.internal.Entry;
import com.mydly.entries.internal.EntryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lançamento como a API devolve: o texto original e os itens interpretados.
 */
@Schema(description = "Lançamento interpretado")
public record EntryResponse(
        UUID id,
        @Schema(description = "Texto original, como foi enviado") String text,
        Instant occurredAt,
        EntryStatus status,
        @Schema(description = "Se o app deve confirmar com o usuário antes de registrar") boolean needsConfirmation,
        @Schema(description = "Itens interpretados; uma frase composta pode gerar mais de um")
        List<EntryItemResponse> items) {

    static EntryResponse from(Entry entry) {
        return new EntryResponse(
                entry.getId(),
                entry.getRawText(),
                entry.getOccurredAt(),
                entry.getStatus(),
                entry.getStatus() == EntryStatus.NEEDS_INPUT,
                entry.getItems().stream().map(EntryItemResponse::from).toList());
    }
}
