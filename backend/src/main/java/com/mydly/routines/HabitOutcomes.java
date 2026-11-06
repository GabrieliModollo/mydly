package com.mydly.routines;

import com.mydly.entries.RoutineOutcome;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do módulo routines: como ficou cada hábito num dia, com a polaridade aplicada.
 * Só contam os registros ativos; itens retirados com {@code Não foi isso} ficam de fora.
 */
@Service
public class HabitOutcomes {

    private final JdbcClient jdbc;

    HabitOutcomes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Hábitos do dia no fuso informado. */
    @Transactional(readOnly = true)
    public DayHabits on(LocalDate day, ZoneId zone) {
        List<Row> rows = jdbc.sql("""
                        SELECT l.entry_id, l.item_index, l.outcome, h.code, h.name, h.polarity
                        FROM habit_log l LEFT JOIN habit h ON h.code = l.habit_code
                        WHERE l.active AND l.occurred_at >= :start AND l.occurred_at < :end
                        ORDER BY l.occurred_at, l.item_index""")
                .param("start", Timestamp.from(day.atStartOfDay(zone).toInstant()))
                .param("end", Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant()))
                .query((rs, n) -> new Row(rs.getObject("entry_id", UUID.class), rs.getInt("item_index"),
                        RoutineOutcome.valueOf(rs.getString("outcome")), rs.getString("code"), rs.getString("name"),
                        rs.getString("polarity") == null ? null : Polarity.valueOf(rs.getString("polarity"))))
                .list();

        List<DayHabits.HabitLog> logs = new ArrayList<>();
        Map<String, DayHabits.HabitDay> habits = new LinkedHashMap<>();
        for (Row row : rows) {
            HabitStatus status = row.code() == null ? null : HabitStatus.of(row.polarity(), row.outcome());
            logs.add(new DayHabits.HabitLog(row.entryId(), row.itemIndex(), row.code(), row.name(), row.polarity(),
                    status));
            if (row.code() != null) {
                habits.merge(row.code(), new DayHabits.HabitDay(row.code(), row.name(), row.polarity(), status),
                        HabitOutcomes::combine);
            }
        }
        return new DayHabits(new ArrayList<>(habits.values()), logs);
    }

    /**
     * Dois registros do mesmo hábito no dia: um bom vale como cumprido se algum cumpriu, e um ruim vale
     * como deslize se algum foi consumido.
     */
    private static DayHabits.HabitDay combine(DayHabits.HabitDay first, DayHabits.HabitDay second) {
        HabitStatus strongest = first.polarity() == Polarity.GOOD
                ? (first.status() == HabitStatus.ACHIEVED ? first.status() : second.status())
                : (first.status() == HabitStatus.SLIPPED ? first.status() : second.status());
        return new DayHabits.HabitDay(first.code(), first.name(), first.polarity(), strongest);
    }

    private record Row(UUID entryId, int itemIndex, RoutineOutcome outcome, String code, String name,
                       Polarity polarity) {
    }
}
