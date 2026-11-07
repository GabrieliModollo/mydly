package com.mydly.routines;

import java.math.BigDecimal;

/**
 * Progresso de uma meta diária de quantidade num dia, como 1,5 de 2 L.
 *
 * @param habitCode o hábito do catálogo
 * @param habitName o nome do hábito
 * @param target    a quantidade da meta vigente no dia
 * @param unit      a unidade da meta
 * @param done      a soma das quantidades cumpridas do dia, convertida para a unidade da meta e
 *                  arredondada para baixo em duas casas, para nunca parecer cumprida sem estar
 * @param met       a soma do dia chegou à meta (comparação exata, sem arredondamento)
 */
public record DailyGoalProgress(String habitCode, String habitName, BigDecimal target, String unit, BigDecimal done,
                                boolean met) {
}
