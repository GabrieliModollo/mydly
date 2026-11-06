package com.mydly.entries.web;

import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import com.mydly.entries.RoutineOutcome;
import com.mydly.entries.internal.EntryService;
import com.mydly.entries.internal.ResolutionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Corpo do {@code POST /api/v1/entries/{id}/items/{index}/resolution}.
 */
@Schema(description = "Resposta do usuário para um item: confirmar, descartar ou reabrir")
public record ResolveItemRequest(
        @Schema(description = "CONFIRM_FINANCE, CONFIRM_ROUTINE, DISMISS ou REOPEN", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ResolutionAction action,
        @Schema(description = "CONFIRM_FINANCE: valor em reais, maior que zero", example = "8.50") BigDecimal amount,
        @Schema(description = "CONFIRM_FINANCE: EXPENSE (padrão) ou INCOME") Direction direction,
        @Schema(description = "CONFIRM_FINANCE: categoria (opcional)", example = "FOOD") Category category,
        @Schema(description = "CONFIRM_ROUTINE: quantidade (opcional)") BigDecimal quantity,
        @Schema(description = "CONFIRM_ROUTINE: unidade da quantidade: L, mL, h, min, km, kg ou copo") String unit,
        @Schema(description = "CONFIRM_ROUTINE: DONE (padrão) ou MISSED") RoutineOutcome outcome) {

    EntryService.ResolveCommand toCommand() {
        return new EntryService.ResolveCommand(action, amount, direction, category, quantity, unit, outcome);
    }
}
