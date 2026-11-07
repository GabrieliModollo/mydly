package com.mydly.finance;

import com.mydly.entries.Category;
import com.mydly.finance.internal.BudgetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do módulo finance: o gasto de cada categoria contra o orçamento (ADR 0010). Os
 * períodos chegam explícitos em {@link BudgetWindow}; o limite é a versão vigente no dia de
 * referência, e o gasto é o das saídas ativas da categoria no período inteiro da janela.
 */
@Service
public class Budgets {

    private final BudgetRepository repository;
    private final FinanceTotals totals;

    Budgets(BudgetRepository repository, FinanceTotals totals) {
        this.repository = repository;
        this.totals = totals;
    }

    /** O progresso de toda categoria com orçamento no dia de referência, na ordem das categorias. */
    @Transactional(readOnly = true)
    public List<BudgetProgress> progress(UUID userId, BudgetWindow window, ZoneId zone) {
        Map<Category, List<Budget>> versions = repository.allVersions(userId, window.referenceDay());
        List<Budget> active = new ArrayList<>();
        for (Category category : Category.values()) {
            BudgetRepository.versionOn(versions.getOrDefault(category, List.of()), window.referenceDay())
                    .filter(Budget::active)
                    .ifPresent(active::add);
        }
        if (active.isEmpty()) {
            return List.of();
        }
        PeriodTotals week = null;
        PeriodTotals month = null;
        List<BudgetProgress> progress = new ArrayList<>();
        for (Budget budget : active) {
            boolean weekly = budget.period() == BudgetPeriod.WEEKLY;
            LocalDate from = weekly ? window.weekStart() : window.monthStart();
            LocalDate to = weekly ? window.weekEnd() : window.monthEnd();
            if (weekly && week == null) {
                week = totals(userId, from, to, zone);
            } else if (!weekly && month == null) {
                month = totals(userId, from, to, zone);
            }
            BigDecimal spent = spentIn(weekly ? week : month, budget.category());
            progress.add(BudgetProgress.of(budget, from, to, spent));
        }
        return progress;
    }

    /** O progresso de uma categoria, ou vazio quando ela não tem orçamento no dia de referência. */
    @Transactional(readOnly = true)
    public Optional<BudgetProgress> progress(UUID userId, Category category, BudgetWindow window, ZoneId zone) {
        return progress(userId, window, zone).stream().filter(item -> item.category() == category).findFirst();
    }

    private PeriodTotals totals(UUID userId, LocalDate from, LocalDate to, ZoneId zone) {
        return totals.between(userId, from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private static BigDecimal spentIn(PeriodTotals totals, Category category) {
        return totals.spentByCategory().stream()
                .filter(total -> total.category() == category)
                .map(PeriodTotals.CategoryTotal::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
