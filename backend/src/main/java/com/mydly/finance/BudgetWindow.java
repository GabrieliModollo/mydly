package com.mydly.finance;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Os períodos em que os orçamentos são avaliados, sempre explícitos: quem chama decide qual é a
 * semana (pelas preferências) e até onde vai o mês. O limite de cada categoria é a versão vigente no
 * dia de referência.
 *
 * @param referenceDay o dia que decide a versão do limite
 * @param weekStart    o primeiro dia da semana dos orçamentos semanais
 * @param weekEnd      o último dia da semana, inclusive
 * @param monthStart   o primeiro dia do mês dos orçamentos mensais
 * @param monthEnd     o último dia considerado do mês, inclusive
 */
public record BudgetWindow(LocalDate referenceDay, LocalDate weekStart, LocalDate weekEnd, LocalDate monthStart,
                           LocalDate monthEnd) {

    public BudgetWindow {
        Objects.requireNonNull(referenceDay, "referenceDay");
        Objects.requireNonNull(weekStart, "weekStart");
        Objects.requireNonNull(weekEnd, "weekEnd");
        Objects.requireNonNull(monthStart, "monthStart");
        Objects.requireNonNull(monthEnd, "monthEnd");
    }

    /**
     * Os períodos inteiros que contêm o dia: a semana informada e o mês civil do dia, do dia 1 ao
     * último. É o que vale para o período atual (aba Metas e alertas do dia).
     */
    public static BudgetWindow wholePeriods(LocalDate referenceDay, LocalDate weekStart) {
        return new BudgetWindow(referenceDay, weekStart, weekStart.plusDays(6),
                referenceDay.withDayOfMonth(1), referenceDay.with(TemporalAdjusters.lastDayOfMonth()));
    }

    /**
     * A semana informada e o mês do dia de referência só até esse dia. É o que vale no resumo de uma
     * semana: o mês é avaliado do dia 1 até o dia de referência.
     */
    public static BudgetWindow upTo(LocalDate referenceDay, LocalDate weekStart) {
        return new BudgetWindow(referenceDay, weekStart, weekStart.plusDays(6), referenceDay.withDayOfMonth(1),
                referenceDay);
    }
}
