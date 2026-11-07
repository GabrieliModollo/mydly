package com.mydly.insights.internal;

/**
 * Tipos de mensagem da semana, na ordem de prioridade para a mensagem principal.
 */
public enum InsightKind {
    /** Um hábito ruim em 3 dias ou mais. */
    RECURRING_SLIP,
    /** Gasto pelo menos 20% acima da semana anterior. */
    SPENDING_UP,
    /** Nos dias de sono ruim o gasto médio foi pelo menos 20% maior, com 3 dias ou mais em cada grupo. */
    SLEEP_AND_SPENDING,
    /** Um hábito bom cumprido 3 dias seguidos ou mais. */
    STREAK,
    /** A semana teve dados, mas nenhuma regra acima disparou: um resumo simples. */
    OVERVIEW,
    /** A semana não tem nenhum dado: boas-vindas, nunca bronca. */
    WELCOME
}
