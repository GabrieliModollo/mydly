package com.mydly.preferences.internal;

import java.time.DayOfWeek;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A linha única da tabela {@code preferences} (id = 1), semeada pela migração V8 com a segunda.
 */
@Repository
public class PreferencesRepository {

    private final JdbcClient jdbc;

    PreferencesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public DayOfWeek firstDayOfWeek() {
        return DayOfWeek.valueOf(jdbc.sql("SELECT first_day_of_week FROM preferences WHERE id = 1")
                .query(String.class)
                .single());
    }

    public void saveFirstDayOfWeek(DayOfWeek day) {
        jdbc.sql("UPDATE preferences SET first_day_of_week = :day, updated_at = now() WHERE id = 1")
                .param("day", day.name())
                .update();
    }
}
