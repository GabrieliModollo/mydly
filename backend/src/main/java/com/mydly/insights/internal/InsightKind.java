package com.mydly.insights.internal;

/**
 * Tipos de mensagem da semana, na ordem de prioridade para a mensagem principal (ADR 0009 e ADR 0010).
 */
public enum InsightKind {
    /** Uma categoria passou do limite do orçamento (acima de 100%). */
    BUDGET_OVER,
    /** Um hábito ruim em 3 dias ou mais. */
    RECURRING_SLIP,
    /**
     * Uma meta de dias por semana que não dá mais para cumprir nos dias que restam, ou um limite de
     * hábito ruim que já passou. Só na semana em curso.
     */
    GOAL_AT_RISK,
    /** Gasto pelo menos 20% acima da semana anterior. */
    SPENDING_UP,
    /** Uma categoria chegou a 80% do limite do orçamento, sem passar dele. */
    BUDGET_NEAR,
    /** Nos dias de sono ruim o gasto médio foi pelo menos 20% maior, com 3 dias ou mais em cada grupo. */
    SLEEP_AND_SPENDING,
    /** Uma meta de dias por semana atingida, ou um limite de hábito ruim respeitado na semana encerrada. */
    GOAL_MET,
    /** Um hábito bom cumprido 3 dias seguidos ou mais. */
    STREAK,
    /** A semana teve dados, mas nenhuma regra acima disparou: um resumo simples. */
    OVERVIEW,
    /** A semana não tem nenhum dado: boas-vindas, nunca bronca. */
    WELCOME
}
