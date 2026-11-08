package com.mydly.entries.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A cota do LLM na tabela {@code llm_usage} (ADR 0017). A reserva é uma instrução só: soma 1 na linha
 * do usuário e do dia quando ainda há vaga para ele e para todos, e não muda nada quando não há.
 *
 * <p>Dois pedidos ao mesmo tempo podem passar juntos da cota de todos por uma chamada ou outra; a de
 * cada usuário é exata, porque a linha dele fica travada durante a atualização.
 */
@Component
@EnableConfigurationProperties(LlmQuotaProperties.class)
class DatabaseLlmQuota implements LlmQuota {

    private static final String ACQUIRE = """
            INSERT INTO llm_usage (user_id, day, calls)
            SELECT :userId, :day, 1
            WHERE :perUser > 0
              AND (SELECT COALESCE(SUM(calls), 0) FROM llm_usage WHERE day = :day) < :total
            ON CONFLICT (user_id, day) DO UPDATE SET calls = llm_usage.calls + 1
            WHERE llm_usage.calls < :perUser
              AND (SELECT COALESCE(SUM(calls), 0) FROM llm_usage WHERE day = :day) < :total
            """;

    private final JdbcClient jdbc;
    private final LlmQuotaProperties properties;
    private final Clock clock;

    DatabaseLlmQuota(JdbcClient jdbc, LlmQuotaProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(UUID userId) {
        LocalDate today = LocalDate.now(clock.withZone(EntryService.USER_ZONE));
        int changed = jdbc.sql(ACQUIRE)
                .param("userId", userId)
                .param("day", today)
                .param("perUser", properties.perUser())
                .param("total", properties.total())
                .update();
        return changed == 1;
    }
}
