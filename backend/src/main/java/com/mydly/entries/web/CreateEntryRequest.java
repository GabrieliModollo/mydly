package com.mydly.entries.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Corpo do {@code POST /api/v1/entries}.
 */
@Schema(description = "Lançamento em texto livre")
public record CreateEntryRequest(

        @Schema(description = "Id gerado pelo app. Reenviar o mesmo id não duplica o lançamento. "
                + "Se omitido, o servidor gera.", example = "3f1c2a9e-8b7d-4c6a-9e2f-1a2b3c4d5e6f")
        UUID id,

        @Schema(description = "O que o usuário escreveu", example = "Gastei 50 reais comprando energético na farmácia",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = CreateEntryRequest.MAX_TEXT_LENGTH)
        @NotBlank
        @Size(max = MAX_TEXT_LENGTH)
        String text,

        @Schema(description = "Quando o usuário escreveu (pode ser antes da sincronização). Se omitido, usa o momento atual.",
                example = "2026-11-05T21:30:00Z")
        Instant occurredAt) {

    public static final int MAX_TEXT_LENGTH = 1000;
}
