package com.mydly.preferences.internal;

import java.time.DayOfWeek;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * As preferências de cada usuário ({@code preferences}, uma linha por usuário). Usuário sem linha
 * ainda não mudou nada e fica com o padrão.
 */
@Repository
public class PreferencesRepository {

    private final JdbcClient jdbc;

    PreferencesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Dia de início da semana de quem ainda não escolheu. */
    public static final DayOfWeek DEFAULT_FIRST_DAY_OF_WEEK = DayOfWeek.MONDAY;

    public DayOfWeek firstDayOfWeek(UUID userId) {
        return jdbc.sql("SELECT first_day_of_week FROM preferences WHERE user_id = :userId")
                .param("userId", userId)
                .query(String.class)
                .optional()
                .map(DayOfWeek::valueOf)
                .orElse(DEFAULT_FIRST_DAY_OF_WEEK);
    }

    public void saveFirstDayOfWeek(UUID userId, DayOfWeek day) {
        jdbc.sql("""
                        INSERT INTO preferences (user_id, first_day_of_week) VALUES (:userId, :day)
                        ON CONFLICT (user_id) DO UPDATE SET
                            first_day_of_week = EXCLUDED.first_day_of_week,
                            updated_at = now()""")
                .param("userId", userId)
                .param("day", day.name())
                .update();
    }
}
