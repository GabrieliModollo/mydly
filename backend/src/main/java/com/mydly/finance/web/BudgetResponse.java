package com.mydly.finance.web;

import com.mydly.entries.Category;
import com.mydly.finance.BudgetPeriod;
import com.mydly.finance.BudgetProgress;
import com.mydly.finance.BudgetStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma categoria com o orçamento vigente hoje e o gasto no período atual, como a API devolve.
 */
@Schema(description = "Categoria com o orçamento vigente hoje e o progresso no período atual")
record BudgetResponse(
        @Schema(example = "FOOD") Category category,
        @Schema(description = "WEEKLY ou MONTHLY; ausente quando a categoria está sem orçamento") BudgetPeriod period,
        @Schema(description = "Limite em reais; ausente quando a categoria está sem orçamento", example = "100.00")
        BigDecimal limit,
        @Schema(description = "Desde quando este limite vale") LocalDate validFrom,
        @Schema(description = "O gasto no período atual; ausente sem orçamento") Progress progress) {

    static BudgetResponse empty(Category category) {
        return new BudgetResponse(category, null, null, null, null);
    }

    static BudgetResponse of(BudgetProgress progress) {
        return new BudgetResponse(progress.category(), progress.period(), progress.limit(), progress.validFrom(),
                new Progress(progress.periodStart(), progress.periodEnd(), progress.spent(), progress.percent(),
                        progress.status()));
    }

    @Schema(description = "Gasto da categoria no período contra o limite")
    record Progress(
            @Schema(description = "Primeiro dia do período (a semana do usuário ou o dia 1 do mês)") LocalDate periodStart,
            @Schema(description = "Último dia do período, inclusive") LocalDate periodEnd,
            @Schema(description = "Saídas ativas da categoria no período, em reais", example = "85.00") BigDecimal spent,
            @Schema(description = "Gasto em porcentagem do limite, arredondado para baixo", example = "85") int percent,
            @Schema(description = "OK abaixo de 80%; NEAR de 80% a 100%; OVER acima do limite") BudgetStatus status) {
    }
}
