package com.mydly.entries;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do módulo entries: se o usuário escreveu algo num dia. O lembrete do dia (módulo
 * {@code notifications}) usa para não lembrar quem já lançou. Não expõe texto nem valores.
 */
@Service
public class EntryActivity {

    private final JdbcClient jdbc;

    EntryActivity(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code true} quando há ao menos um lançamento do usuário escrito nesse dia, no fuso informado. */
    @Transactional(readOnly = true)
    public boolean wroteOn(UUID userId, LocalDate day, ZoneId zone) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM entry
                                       WHERE user_id = :userId AND occurred_at >= :start AND occurred_at < :end)""")
                .param("userId", userId)
                .param("start", Timestamp.from(day.atStartOfDay(zone).toInstant()))
                .param("end", Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant()))
                .query(Boolean.class)
                .single();
    }
}
