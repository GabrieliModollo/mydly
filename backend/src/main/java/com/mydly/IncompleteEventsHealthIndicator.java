package com.mydly;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Saúde dos eventos entre módulos: {@code OUT_OF_SERVICE} quando algum evento está sem concluir há
 * mais que {@code mydly.events.stale-after}, o que quer dizer que algum total pode estar errado e a
 * nova tentativa não resolveu (ADR 0008).
 *
 * <p>Só conta os eventos. O conteúdo (texto e valor do lançamento) nunca sai daqui (LGPD).
 */
@Component("incompleteEvents")
class IncompleteEventsHealthIndicator implements HealthIndicator {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final EventRetryProperties properties;

    IncompleteEventsHealthIndicator(JdbcClient jdbc, Clock clock, EventRetryProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public Health health() {
        long stale = jdbc.sql("""
                        SELECT count(*) FROM event_publication
                        WHERE completion_date IS NULL AND publication_date <= :limit""")
                .param("limit", Timestamp.from(clock.instant().minus(properties.staleAfter())))
                .query(Long.class)
                .single();
        Health.Builder health = stale == 0 ? Health.up() : Health.status(Status.OUT_OF_SERVICE);
        return health.withDetail("staleEvents", stale).build();
    }
}
