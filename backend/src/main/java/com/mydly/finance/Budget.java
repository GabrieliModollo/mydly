package com.mydly.finance;

import com.mydly.entries.Category;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Uma versão do orçamento de uma categoria, que vale de {@code validFrom} até a próxima versão
 * (ADR 0010). A versão de remoção não tem período nem limite.
 *
 * @param category  a categoria
 * @param validFrom o primeiro dia em que esta versão vale
 * @param period    semanal ou mensal; nulo na versão de remoção
 * @param limit     o limite de gasto em reais, maior que zero; nulo na versão de remoção
 */
public record Budget(Category category, LocalDate validFrom, BudgetPeriod period, BigDecimal limit) {

    public Budget {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(validFrom, "validFrom");
        if ((period == null) != (limit == null)) {
            throw new IllegalArgumentException("period e limit vão juntos");
        }
        if (limit != null && limit.signum() <= 0) {
            throw new IllegalArgumentException("o limite precisa ser maior que zero");
        }
    }

    /** {@code true} quando esta versão tem limite (não é a de remoção). */
    public boolean active() {
        return limit != null;
    }
}
