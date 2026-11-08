package com.mydly.finance.internal;

import com.mydly.accounts.DemoReset;
import com.mydly.entries.Category;
import com.mydly.finance.Budget;
import com.mydly.finance.BudgetPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A parte de {@code finance} no reset da demonstração (ADR 0017): apaga as transações e os limites do
 * usuário de demonstração e grava os limites de exemplo. As transações voltam pelos eventos dos
 * lançamentos de exemplo, depois do commit.
 */
@Component
class DemoBudgets {

    private final JdbcClient jdbc;
    private final BudgetRepository budgets;

    DemoBudgets(JdbcClient jdbc, BudgetRepository budgets) {
        this.jdbc = jdbc;
        this.budgets = budgets;
    }

    @EventListener
    void on(DemoReset reset) {
        jdbc.sql("DELETE FROM finance_transaction WHERE user_id = :userId").param("userId", reset.userId()).update();
        jdbc.sql("DELETE FROM budget WHERE user_id = :userId").param("userId", reset.userId()).update();
        LocalDate validFrom = reset.today().minusDays(60);
        // Abaixo de 80% na semana anterior, para o aviso de orçamento não esconder o cruzamento de sono e gasto.
        budgets.upsert(reset.userId(), new Budget(Category.FOOD, validFrom, BudgetPeriod.WEEKLY, new BigDecimal("350")));
        budgets.upsert(reset.userId(),
                new Budget(Category.TRANSPORT, validFrom, BudgetPeriod.WEEKLY, new BigDecimal("100")));
        budgets.upsert(reset.userId(),
                new Budget(Category.LEISURE, validFrom, BudgetPeriod.MONTHLY, new BigDecimal("200")));
    }
}
