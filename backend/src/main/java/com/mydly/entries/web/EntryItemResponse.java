package com.mydly.entries.web;

import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.RoutineOutcome;
import com.mydly.entries.internal.EffectiveItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Item de um lançamento, no formato do contrato público da API, com o valor efetivo: a
 * confirmação do usuário quando existe, senão o que o parser entendeu.
 *
 * <p>É separado do {@link ParsedEntry} de propósito: o record de domínio também é o formato do
 * {@code parsed_json} no banco, e mudar um não pode mudar o outro sem querer.
 */
@Schema(description = "Item interpretado de um lançamento")
public record EntryItemResponse(
        EntryType type,
        @Schema(description = "Só em FINANCE: EXPENSE (saída) ou INCOME (entrada)") Direction direction,
        @Schema(description = "Só em ROUTINE: DONE (hábito cumprido) ou MISSED (não cumprido)") RoutineOutcome outcome,
        @Schema(description = "Só em FINANCE: categoria sugerida pelo parser ou escolhida pelo usuário") Category category,
        @Schema(description = "Valor em reais, só em FINANCE; ausente quando a frase não diz quanto") BigDecimal amount,
        @Schema(description = "Quantidade, só em ROUTINE") BigDecimal quantity,
        @Schema(description = "Unidade normalizada da quantidade; ausente em contagens") String unit,
        @Schema(description = "Texto do lançamento, ou ausente quando a frase é só o valor") String description,
        @Schema(description = "Confiança da interpretação, de 0 a 1; 1 quando confirmado pelo usuário") double confidence,
        @Schema(description = "Ainda precisa de confirmação do usuário") boolean needsConfirmation,
        @Schema(description = "O usuário confirmou ou descartou este item") boolean resolvedByUser,
        @Schema(description = "O usuário disse que não era nada a registrar") boolean dismissed) {

    static EntryItemResponse from(EffectiveItem effective) {
        ParsedEntry item = effective.item();
        return new EntryItemResponse(item.type(), item.direction(), item.outcome(), item.category(), item.amount(),
                item.quantity(), item.unit(), item.description(), item.confidence(), effective.needsConfirmation(),
                effective.resolvedByUser(), effective.dismissed());
    }
}
