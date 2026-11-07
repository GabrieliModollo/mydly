package com.mydly.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.Category;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Limiares do orçamento (ADR 0010): comparação exata em {@link BigDecimal}, aviso a partir de 80%,
 * exatamente 100% ainda é aviso, e o percentual exibido é arredondado para baixo.
 */
class BudgetProgressTests {

    @ParameterizedTest(name = "R$ {0} de R$ {1}: {2}, {3}%")
    @CsvSource({
            "0,       100, OK,   0",
            "79.99,   100, OK,   79",
            "79.9,    100, OK,   79",
            "80,      100, NEAR, 80",
            "85,      100, NEAR, 85",
            "99.99,   100, NEAR, 99",
            "100,     100, NEAR, 100",
            "100.01,  100, OVER, 100",
            "250,     100, OVER, 250",
            "26.66,   33.33, OK,   79",
            "26.67,   33.33, NEAR, 80",
            "26.65,   33.33, OK,   79",
    })
    void limiaresExatosEPercentualParaBaixo(String spent, String limit, BudgetStatus status, int percent) {
        Budget budget = new Budget(Category.FOOD, LocalDate.of(2026, 11, 1), BudgetPeriod.WEEKLY, new BigDecimal(limit));

        BudgetProgress progress = BudgetProgress.of(budget, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8),
                new BigDecimal(spent));

        assertThat(progress.status()).isEqualTo(status);
        assertThat(progress.percent()).isEqualTo(percent);
    }

    @Test
    void periodoInteiroEMesAteODiaDeReferencia() {
        LocalDate reference = LocalDate.of(2026, 11, 18);
        LocalDate weekStart = LocalDate.of(2026, 11, 15);

        BudgetWindow whole = BudgetWindow.wholePeriods(reference, weekStart);
        BudgetWindow upTo = BudgetWindow.upTo(reference, weekStart);

        assertThat(whole.weekEnd()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(whole.monthStart()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(whole.monthEnd()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(upTo.monthEnd()).isEqualTo(reference);
    }
}
