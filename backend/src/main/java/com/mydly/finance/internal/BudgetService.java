package com.mydly.finance.internal;

import com.mydly.entries.Category;
import com.mydly.finance.Budget;
import com.mydly.finance.BudgetPeriod;
import com.mydly.preferences.UserCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gravação dos orçamentos (ADR 0010). Mudar o limite grava uma versão nova a partir de hoje, no fuso
 * do usuário; remover grava a versão sem limite. Duas gravações no mesmo dia ficam numa versão só.
 */
@Service
public class BudgetService {

    private final BudgetRepository repository;
    private final UserCalendar calendar;

    BudgetService(BudgetRepository repository, UserCalendar calendar) {
        this.repository = repository;
        this.calendar = calendar;
    }

    /** O orçamento com efeito hoje. */
    @Transactional(readOnly = true)
    public Optional<Budget> current(UUID userId, Category category) {
        return repository.budgetOn(userId, category, calendar.today());
    }

    /** Define o limite a partir de hoje. Um limite igual ao que já vale não grava versão nova. */
    @Transactional
    public void set(UUID userId, Category category, BudgetPeriod period, BigDecimal limit) {
        LocalDate today = calendar.today();
        Optional<Budget> current = repository.budgetOn(userId, category, today);
        boolean same = current.isPresent() && current.get().period() == period
                && current.get().limit().compareTo(limit) == 0;
        if (!same) {
            repository.upsert(userId, new Budget(category, today, period, limit));
        }
    }

    /** Tira o limite a partir de hoje. Sem orçamento vigente, não faz nada. */
    @Transactional
    public void remove(UUID userId, Category category) {
        LocalDate today = calendar.today();
        if (repository.budgetOn(userId, category, today).isPresent()) {
            repository.upsert(userId, new Budget(category, today, null, null));
        }
    }
}
