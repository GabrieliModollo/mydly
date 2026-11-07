package com.mydly.finance;

/**
 * Como o gasto de uma categoria está em relação ao limite.
 */
public enum BudgetStatus {
    /** Abaixo de 80% do limite. */
    OK,
    /** De 80% até 100% do limite, inclusive: exatamente no limite ainda é aviso, não estouro. */
    NEAR,
    /** Acima do limite. */
    OVER
}
