package com.mydly.routines;

/**
 * Placar de um hábito com meta numa semana (ADR 0010). O tipo vem da versão vigente no dia de
 * referência: o último dia da semana ou hoje se a semana está em curso.
 *
 * <ul>
 *   <li>{@link GoalKind#WEEKLY_FREQUENCY} num hábito bom: {@code achieved} são os dias cumpridos e
 *       {@code target} é a meta de dias.</li>
 *   <li>{@link GoalKind#WEEKLY_FREQUENCY} num hábito ruim: é um limite. {@code slips} são os dias de
 *       deslize e {@code limit} o máximo; {@code achieved} continua sendo os dias evitados e
 *       {@code target} fica nulo.</li>
 *   <li>{@link GoalKind#DAILY_QUANTITY}: {@code achieved} são os dias em que a quantidade bateu a meta
 *       vigente naquele dia e {@code target} são os dias da semana com meta diária vigente.</li>
 * </ul>
 *
 * @param habitCode o hábito do catálogo
 * @param habitName o nome do hábito
 * @param polarity  bom ou ruim
 * @param kind      o tipo da meta da semana; nunca {@link GoalKind#NONE}
 * @param achieved  dias cumpridos (bom cumprido ou ruim evitado) ou dias com a meta diária batida
 * @param target    a meta de dias; nulo no limite de hábito ruim
 * @param limit     o máximo de dias de deslize; só no limite de hábito ruim
 * @param slips     os dias de deslize; só no limite de hábito ruim
 */
public record WeekGoalScore(String habitCode, String habitName, Polarity polarity, GoalKind kind, int achieved,
                            Integer target, Integer limit, Integer slips) {

    /** {@code true} quando é o limite de um hábito ruim. */
    public boolean isLimit() {
        return limit != null;
    }
}
