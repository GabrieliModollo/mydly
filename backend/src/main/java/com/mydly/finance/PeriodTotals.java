package com.mydly.finance;

import com.mydly.entries.Category;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Totais de um período, só com as transações ativas.
 *
 * @param spent           soma das saídas
 * @param received        soma das entradas
 * @param spentByCategory saídas por categoria, da maior para a menor; categoria {@code null} = sem categoria
 */
public record PeriodTotals(BigDecimal spent, BigDecimal received, List<CategoryTotal> spentByCategory) {

    public PeriodTotals {
        Objects.requireNonNull(spent, "spent");
        Objects.requireNonNull(received, "received");
        spentByCategory = List.copyOf(spentByCategory);
    }

    /** Saídas de uma categoria; {@code category} é {@code null} para as sem categoria. */
    public record CategoryTotal(Category category, BigDecimal amount) {
    }
}
