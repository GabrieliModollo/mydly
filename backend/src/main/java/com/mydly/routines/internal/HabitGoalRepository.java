package com.mydly.routines.internal;

import com.mydly.entries.RoutineOutcome;
import com.mydly.routines.GoalKind;
import com.mydly.routines.HabitGoal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Versões das metas ({@code habit_goal}) e os registros com quantidade ({@code habit_log}) que as
 * metas precisam. Uma versão nunca é apagada; a do mesmo dia é substituída pela gravação nova.
 */
@Repository
public class HabitGoalRepository {

    private static final String UPSERT = """
            INSERT INTO habit_goal (habit_code, valid_from, kind, target_quantity, unit, days_per_week)
            VALUES (:habitCode, :validFrom, :kind, :targetQuantity, :unit, :daysPerWeek)
            ON CONFLICT (habit_code, valid_from) DO UPDATE SET
                kind = EXCLUDED.kind,
                target_quantity = EXCLUDED.target_quantity,
                unit = EXCLUDED.unit,
                days_per_week = EXCLUDED.days_per_week,
                updated_at = now()
            """;

    private final JdbcClient jdbc;

    HabitGoalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava a versão; se já existe uma no mesmo dia para o hábito, ela é substituída. */
    public void upsert(HabitGoal goal) {
        jdbc.sql(UPSERT)
                .param("habitCode", goal.habitCode())
                .param("validFrom", goal.validFrom())
                .param("kind", goal.kind().name())
                .param("targetQuantity", goal.targetQuantity())
                .param("unit", goal.unit())
                .param("daysPerWeek", goal.daysPerWeek())
                .update();
    }

    /** As versões de um hábito que começam até {@code until}, da mais antiga para a mais nova. */
    public List<HabitGoal> versions(String habitCode, LocalDate until) {
        return jdbc.sql("""
                        SELECT habit_code, valid_from, kind, target_quantity, unit, days_per_week
                        FROM habit_goal WHERE habit_code = :habitCode AND valid_from <= :until
                        ORDER BY valid_from""")
                .param("habitCode", habitCode)
                .param("until", until)
                .query((rs, n) -> map(rs))
                .list();
    }

    /** As versões de todos os hábitos que começam até {@code until}, por hábito. */
    public Map<String, List<HabitGoal>> allVersions(LocalDate until) {
        return jdbc.sql("""
                        SELECT habit_code, valid_from, kind, target_quantity, unit, days_per_week
                        FROM habit_goal WHERE valid_from <= :until
                        ORDER BY habit_code, valid_from""")
                .param("until", until)
                .query((rs, n) -> map(rs))
                .list()
                .stream()
                .collect(Collectors.groupingBy(HabitGoal::habitCode));
    }

    /**
     * Os registros ativos de hábitos do catálogo de {@code from} até o dia anterior a {@code until},
     * por hábito e por dia no fuso informado.
     */
    public Map<String, Map<LocalDate, List<GoalRules.DayLog>>> logs(LocalDate from, LocalDate until, ZoneId zone) {
        return jdbc.sql("""
                        SELECT habit_code, (occurred_at AT TIME ZONE :zone)::date AS day, outcome, quantity, unit
                        FROM habit_log
                        WHERE active AND habit_code IS NOT NULL AND occurred_at >= :start AND occurred_at < :end
                        ORDER BY occurred_at, item_index""")
                .param("zone", zone.getId())
                .param("start", Timestamp.from(from.atStartOfDay(zone).toInstant()))
                .param("end", Timestamp.from(until.atStartOfDay(zone).toInstant()))
                .query((rs, n) -> new LogRow(rs.getString("habit_code"), rs.getObject("day", LocalDate.class),
                        new GoalRules.DayLog(RoutineOutcome.valueOf(rs.getString("outcome")),
                                rs.getBigDecimal("quantity"), rs.getString("unit"))))
                .list()
                .stream()
                .collect(Collectors.groupingBy(LogRow::habitCode, Collectors.groupingBy(LogRow::day,
                        Collectors.mapping(LogRow::log, Collectors.toList()))));
    }

    private static HabitGoal map(ResultSet rs) throws SQLException {
        int days = rs.getInt("days_per_week");
        Integer daysPerWeek = rs.wasNull() ? null : days;
        return new HabitGoal(rs.getString("habit_code"), rs.getObject("valid_from", LocalDate.class),
                GoalKind.valueOf(rs.getString("kind")), rs.getBigDecimal("target_quantity"), rs.getString("unit"),
                daysPerWeek);
    }

    private record LogRow(String habitCode, LocalDate day, GoalRules.DayLog log) {
    }
}
