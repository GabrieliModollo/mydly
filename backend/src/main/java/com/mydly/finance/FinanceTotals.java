package com.mydly.finance;

import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do módulo finance: totais de gastos e entradas por período e por categoria.
 * Só contam as transações ativas; itens retirados com {@code Não foi isso} ficam de fora.
 */
@Service
public class FinanceTotals {

    private final JdbcClient jdbc;

    FinanceTotals(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Totais de um dia no fuso informado. */
    @Transactional(readOnly = true)
    public PeriodTotals on(LocalDate day, ZoneId zone) {
        return between(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** Totais das transações com {@code start <= occurredAt < end}. */
    @Transactional(readOnly = true)
    public PeriodTotals between(Instant start, Instant end) {
        List<Row> rows = jdbc.sql("""
                        SELECT direction, category, sum(amount) AS total
                        FROM finance_transaction
                        WHERE active AND occurred_at >= :start AND occurred_at < :end
                        GROUP BY direction, category""")
                .param("start", Timestamp.from(start))
                .param("end", Timestamp.from(end))
                .query((rs, n) -> new Row(Direction.valueOf(rs.getString("direction")),
                        rs.getString("category") == null ? null : Category.valueOf(rs.getString("category")),
                        rs.getBigDecimal("total")))
                .list();
        BigDecimal spent = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        List<PeriodTotals.CategoryTotal> byCategory = new ArrayList<>();
        for (Row row : rows) {
            if (row.direction() == Direction.EXPENSE) {
                spent = spent.add(row.total());
                byCategory.add(new PeriodTotals.CategoryTotal(row.category(), row.total()));
            } else {
                received = received.add(row.total());
            }
        }
        byCategory.sort(Comparator.comparing(PeriodTotals.CategoryTotal::amount).reversed());
        return new PeriodTotals(spent, received, byCategory);
    }

    private record Row(Direction direction, Category category, BigDecimal total) {
    }
}
