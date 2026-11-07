package com.mydly.finance;

/**
 * Período de um orçamento (ADR 0010).
 */
public enum BudgetPeriod {
    /** A semana do usuário, que segue o dia de início das preferências. */
    WEEKLY,
    /** O mês civil, do dia 1 ao último dia, no fuso do usuário. */
    MONTHLY
}
