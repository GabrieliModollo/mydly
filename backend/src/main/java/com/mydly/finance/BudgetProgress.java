package com.mydly.finance;

import com.mydly.entries.Category;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * O gasto de uma categoria contra o limite no período.
 *
 * @param category    a categoria
 * @param period      semanal ou mensal
 * @param limit       o limite vigente no dia de referência
 * @param validFrom   desde quando esse limite vale
 * @param periodStart o primeiro dia do período avaliado
 * @param periodEnd   o último dia do período avaliado, inclusive
 * @param spent       as saídas ativas da categoria no período
 * @param percent     o gasto em porcentagem do limite, arredondado para baixo: 79,9% é 79
 * @param status      abaixo do aviso, no aviso (80% a 100%) ou acima do limite
 */
public record BudgetProgress(Category category, BudgetPeriod period, BigDecimal limit, LocalDate validFrom,
                             LocalDate periodStart, LocalDate periodEnd, BigDecimal spent, int percent,
                             BudgetStatus status) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal NEAR_PERCENT = BigDecimal.valueOf(80);

    /** O progresso de um orçamento ativo, com os limiares em comparação exata. */
    public static BudgetProgress of(Budget budget, LocalDate periodStart, LocalDate periodEnd, BigDecimal spent) {
        if (!budget.active()) {
            throw new IllegalArgumentException("orçamento sem limite");
        }
        return new BudgetProgress(budget.category(), budget.period(), budget.limit(), budget.validFrom(), periodStart,
                periodEnd, spent, percent(spent, budget.limit()), status(spent, budget.limit()));
    }

    /** {@code gasto > limite} é estouro; {@code gasto * 100 >= 80 * limite} é aviso. */
    public static BudgetStatus status(BigDecimal spent, BigDecimal limit) {
        if (spent.compareTo(limit) > 0) {
            return BudgetStatus.OVER;
        }
        if (spent.multiply(HUNDRED).compareTo(limit.multiply(NEAR_PERCENT)) >= 0) {
            return BudgetStatus.NEAR;
        }
        return BudgetStatus.OK;
    }

    /** O percentual inteiro, sempre arredondado para baixo, para nunca mostrar 80% num gasto de 79,9%. */
    public static int percent(BigDecimal spent, BigDecimal limit) {
        return spent.multiply(HUNDRED).divide(limit, 0, RoundingMode.DOWN).intValueExact();
    }
}
