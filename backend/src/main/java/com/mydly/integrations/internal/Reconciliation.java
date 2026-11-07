package com.mydly.integrations.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A regra de reconciliação (ADR 0014), sem banco, para ser testada à parte.
 *
 * <p>Uma transação do banco e um lançamento manual são o mesmo gasto quando têm o mesmo usuário, a
 * mesma direção, o mesmo valor exato e a data do banco vai de um dia antes até três dias depois da
 * data do lançamento: o banco costuma lançar a compra no cartão um ou dois dias depois, e o
 * lançamento de antes da meia-noite pode cair no dia seguinte no banco. Com mais de um candidato,
 * vale o de data mais próxima e, no empate, o mais antigo.
 */
final class Reconciliation {

    /** O banco pode lançar até um dia antes do que a pessoa escreveu. */
    static final int BANK_DAYS_BEFORE = 1;

    /** E até três dias depois. */
    static final int BANK_DAYS_AFTER = 3;

    private Reconciliation() {
    }

    /** O primeiro dia do lançamento manual que ainda casa com uma transação do banco nesse dia. */
    static LocalDate firstManualDay(LocalDate bankDay) {
        return bankDay.minusDays(BANK_DAYS_AFTER);
    }

    /** O último dia do lançamento manual que ainda casa com uma transação do banco nesse dia. */
    static LocalDate lastManualDay(LocalDate bankDay) {
        return bankDay.plusDays(BANK_DAYS_BEFORE);
    }

    /** {@code true} quando a transação do banco nesse dia pode ser o lançamento manual nesse outro. */
    static boolean withinWindow(LocalDate bankDay, LocalDate manualDay) {
        long days = ChronoUnit.DAYS.between(manualDay, bankDay);
        return days >= -BANK_DAYS_BEFORE && days <= BANK_DAYS_AFTER;
    }

    /**
     * O melhor candidato para um dia: o de data mais próxima dentro da janela e, no empate, o primeiro
     * da lista (quem chama ordena do mais antigo ao mais novo).
     *
     * @param day         o dia do outro lado (do banco, quando os candidatos são lançamentos)
     * @param bankSide    {@code true} quando {@code day} é o dia do banco
     * @param candidates  os candidatos ainda livres
     * @param candidateDay o dia de cada candidato
     */
    static <T> Optional<T> best(LocalDate day, boolean bankSide, List<T> candidates, Function<T, LocalDate> candidateDay) {
        return candidates.stream()
                .filter(candidate -> bankSide
                        ? withinWindow(day, candidateDay.apply(candidate))
                        : withinWindow(candidateDay.apply(candidate), day))
                .min(Comparator.comparingLong(candidate ->
                        Math.abs(ChronoUnit.DAYS.between(day, candidateDay.apply(candidate)))));
    }
}
