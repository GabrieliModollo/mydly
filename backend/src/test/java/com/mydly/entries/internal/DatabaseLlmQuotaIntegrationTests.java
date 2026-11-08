package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.MutableClock;
import com.mydly.TestcontainersConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A cota diária do LLM no banco (ADR 0017): 2 chamadas por usuário e 3 no total, neste teste. Os dias
 * são de 2045, longe dos que os outros testes usam.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabaseLlmQuotaIntegrationTests {

    /** Meio-dia de 10/03/2045 em São Paulo. */
    private static final Instant NOON = Instant.parse("2045-03-10T15:00:00Z");

    @Autowired
    private JdbcClient jdbc;

    private final MutableClock clock = new MutableClock(NOON);
    private DatabaseLlmQuota quota;

    @BeforeEach
    void setUp() {
        clean();
        quota = new DatabaseLlmQuota(jdbc, new LlmQuotaProperties(2, 3), clock);
    }

    @AfterEach
    void clean() {
        jdbc.sql("DELETE FROM llm_usage WHERE day >= DATE '2045-01-01'").update();
    }

    @Test
    void cadaUsuarioTemSuaCotaPorDia() {
        UUID ana = UUID.randomUUID();
        UUID bia = UUID.randomUUID();

        assertThat(quota.tryAcquire(ana)).isTrue();
        assertThat(quota.tryAcquire(ana)).isTrue();
        assertThat(quota.tryAcquire(ana)).isFalse();
        assertThat(quota.tryAcquire(bia)).isTrue();
        assertThat(calls(ana)).isEqualTo(2);
    }

    @Test
    void aCotaDeTodosLimitaQuemAindaTemVaga() {
        UUID ana = UUID.randomUUID();
        UUID bia = UUID.randomUUID();
        UUID caio = UUID.randomUUID();
        quota.tryAcquire(ana);
        quota.tryAcquire(ana);
        quota.tryAcquire(bia);

        assertThat(quota.tryAcquire(bia)).isFalse();
        assertThat(quota.tryAcquire(caio)).isFalse();
    }

    @Test
    void oDiaNovoComecaComACotaInteira() {
        UUID ana = UUID.randomUUID();
        quota.tryAcquire(ana);
        quota.tryAcquire(ana);

        // 23h59 de 10/03 ainda é o mesmo dia em São Paulo; 00h01 de 11/03 já é outro.
        clock.set("2045-03-11T02:59:00Z");
        assertThat(quota.tryAcquire(ana)).isFalse();
        clock.set("2045-03-11T03:01:00Z");
        assertThat(quota.tryAcquire(ana)).isTrue();
    }

    @Test
    void cotaZeroDesligaOLlm() {
        DatabaseLlmQuota none = new DatabaseLlmQuota(jdbc, new LlmQuotaProperties(0, 3), clock);

        assertThat(none.tryAcquire(UUID.randomUUID())).isFalse();
    }

    private int calls(UUID userId) {
        return jdbc.sql("SELECT calls FROM llm_usage WHERE user_id = :userId").param("userId", userId)
                .query(Integer.class).single();
    }
}
