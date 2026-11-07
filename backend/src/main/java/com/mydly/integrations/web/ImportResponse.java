package com.mydly.integrations.web;

import com.mydly.integrations.internal.ImportService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** O resumo de uma importação. */
@Schema(description = "Resumo da importação")
record ImportResponse(
        @Schema(description = "OFX ou CSV") String format,
        @Schema(description = "Transações lidas do arquivo") int read,
        @Schema(description = "Entraram nos números") int added,
        @Schema(description = "Já estavam lançadas no app e ficaram ligadas ao lançamento") int matched,
        @Schema(description = "Já tinham sido importadas antes") int duplicates,
        @Schema(description = "As transações novas deste arquivo") List<ImportedTransactionResponse> transactions) {

    static ImportResponse of(String format, ImportService.Summary summary) {
        return new ImportResponse(format, summary.read(), summary.added(), summary.matched(), summary.duplicates(),
                summary.transactions().stream().map(ImportedTransactionResponse::of).toList());
    }
}
