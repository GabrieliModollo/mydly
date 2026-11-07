package com.mydly.finance.internal;

import com.mydly.entries.Category;
import com.mydly.finance.Budget;
import com.mydly.finance.BudgetPeriod;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Versões dos orçamentos ({@code budget}), sempre de um usuário. Uma versão nunca é apagada; a do
 * mesmo dia é substituída pela gravação nova.
 */
@Repository
public class BudgetRepository {

    private static final String UPSERT = """
            INSERT INTO budget (user_id, category, valid_from, period, limit_amount)
            VALUES (:userId, :category, :validFrom, :period, :limit)
            ON CONFLICT (user_id, category, valid_from) DO UPDATE SET
                period = EXCLUDED.period,
                limit_amount = EXCLUDED.limit_amount,
                updated_at = now()
            """;

    private final JdbcClient jdbc;

    BudgetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava a versão; se já existe uma no mesmo dia para a categoria, ela é substituída. */
    public void upsert(UUID userId, Budget budget) {
        jdbc.sql(UPSERT)
                .param("userId", userId)
                .param("category", budget.category().name())
                .param("validFrom", budget.validFrom())
                .param("period", budget.period() == null ? null : budget.period().name())
                .param("limit", budget.limit())
                .update();
    }

    /** As versões de todas as categorias do usuário que começam até {@code until}, por categoria. */
    public Map<Category, List<Budget>> allVersions(UUID userId, LocalDate until) {
        return jdbc.sql("""
                        SELECT category, valid_from, period, limit_amount FROM budget
                        WHERE user_id = :userId AND valid_from <= :until ORDER BY category, valid_from""")
                .param("userId", userId)
                .param("until", until)
                .query((rs, n) -> new Budget(Category.valueOf(rs.getString("category")),
                        rs.getObject("valid_from", LocalDate.class),
                        rs.getString("period") == null ? null : BudgetPeriod.valueOf(rs.getString("period")),
                        rs.getBigDecimal("limit_amount")))
                .list()
                .stream()
                .collect(Collectors.groupingBy(Budget::category));
    }

    /** A versão vigente em {@code day}: a de maior {@code validFrom} até esse dia. */
    public static Optional<Budget> versionOn(List<Budget> versions, LocalDate day) {
        return versions.stream()
                .filter(version -> !version.validFrom().isAfter(day))
                .max(Comparator.comparing(Budget::validFrom));
    }

    /** O orçamento com efeito em {@code day}: a versão vigente, se ela não for a de remoção. */
    public Optional<Budget> budgetOn(UUID userId, Category category, LocalDate day) {
        return versionOn(allVersions(userId, day).getOrDefault(category, List.of()), day).filter(Budget::active);
    }
}
