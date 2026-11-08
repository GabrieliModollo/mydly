package com.mydly.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.MutableClock;
import com.mydly.TestUsers;
import com.mydly.TestcontainersConfiguration;
import com.mydly.accounts.DemoReset;
import com.mydly.entries.internal.DemoEntriesAccess;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O reset da demonstração (ADR 0017), com cada módulo de verdade: apaga o que o usuário tinha, grava as
 * três semanas de exemplo e não toca nos outros usuários. Roda com um usuário criado no teste, para
 * não apagar o demo dos outros testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoResetIntegrationTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    /** Meio-dia de uma quarta-feira em São Paulo, em 2044, longe dos dias dos outros testes. */
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2044-06-15T15:00:00Z"));

    @TestBean
    private Clock clock;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID demo;
    private UUID other;
    private LocalDate today;

    static Clock clock() {
        return CLOCK;
    }

    @BeforeEach
    void users() {
        demo = accounts.create("demo-reset-" + UUID.randomUUID().toString().substring(0, 8), "x").id();
        other = accounts.create("outra-reset-" + UUID.randomUUID().toString().substring(0, 8), "x").id();
        today = LocalDate.now(CLOCK.withZone(ZONE));
    }

    @Test
    void oResetTrocaOQueOUsuarioTinhaPelaSemanaDeExemplo() throws Exception {
        jdbc.sql("INSERT INTO preferences (user_id, first_day_of_week) VALUES (:userId, 'SUNDAY')")
                .param("userId", demo).update();
        jdbc.sql("INSERT INTO notification_settings (user_id) VALUES (:userId)").param("userId", demo).update();
        mvc.perform(post("/api/v1/entries")
                        .with(TestUsers.as(demo)).contentType("application/json")
                        .content("{\"text\": \"Gastei 999 reais no shopping\"}"))
                .andExpect(status().isCreated());

        reset(demo);

        assertThat(count("entry", demo)).isEqualTo(exampleSize());
        assertThat(jdbc.sql("SELECT count(*) FROM entry WHERE user_id = :userId AND raw_text LIKE '%shopping%'")
                .param("userId", demo).query(Long.class).single()).isZero();
        assertThat(count("habit_goal", demo)).isEqualTo(3);
        assertThat(count("budget", demo)).isEqualTo(3);
        assertThat(count("preferences", demo)).isZero();
        assertThat(count("notification_settings", demo)).isZero();
        assertThat(count("finance_transaction", demo)).isPositive();
        assertThat(count("habit_log", demo)).isPositive();
        // Hoje fica vazio para quem visita escrever.
        mvc.perform(get("/api/v1/entries").param("date", today.toString()).with(TestUsers.as(demo)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aSemanaAnteriorMostraOCruzamentoDeSonoEGasto() throws Exception {
        reset(demo);

        LocalDate previousMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        mvc.perform(get("/api/v1/weeks/{start}/summary", previousMonday).with(TestUsers.as(demo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysElapsed").value(7))
                .andExpect(jsonPath("$.insights[?(@.kind == 'SLEEP_AND_SPENDING')]").isNotEmpty());
    }

    @Test
    void resetarDuasVezesDaOMesmoResultado() {
        reset(demo);
        Map<String, Long> first = counts(demo);

        reset(demo);

        assertThat(counts(demo)).isEqualTo(first);
    }

    @Test
    void oResetNaoMexeNosOutrosUsuarios() throws Exception {
        mvc.perform(post("/api/v1/entries")
                        .with(TestUsers.as(other)).contentType("application/json")
                        .content("{\"text\": \"Gastei 30 reais na farmácia\"}"))
                .andExpect(status().isCreated());

        reset(demo);

        assertThat(count("entry", other)).isOne();
        assertThat(count("finance_transaction", other)).isOne();
    }

    @Test
    void semOUsuarioDeDemonstracaoOResetNaoFazNada() {
        DemoResetJob job = new DemoResetJob(new DemoProperties(true, "nao-existe-" + UUID.randomUUID()), accounts,
                events, transactionManager, CLOCK);

        assertThat(job.reset()).isFalse();
    }

    @Test
    void desligadoOResetNaoRodaNemAoSubirNemNaHoraMarcada() {
        String username = accounts.findById(demo).orElseThrow().username();
        DemoResetJob job = new DemoResetJob(new DemoProperties(false, username), accounts, events,
                transactionManager, CLOCK);

        job.run(null);
        job.daily();

        assertThat(count("habit_goal", demo)).isZero();
    }

    @Test
    void oJobLigadoRestauraOUsuarioConfigurado() {
        String username = accounts.findById(demo).orElseThrow().username();
        DemoResetJob job = new DemoResetJob(new DemoProperties(true, username), accounts, events,
                transactionManager, CLOCK);

        assertThat(job.reset()).isTrue();
        assertThat(count("entry", demo)).isEqualTo(exampleSize());
    }

    private void reset(UUID userId) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new DemoReset(userId, today)));
    }

    private long exampleSize() {
        return DemoEntriesAccess.size();
    }

    private Map<String, Long> counts(UUID userId) {
        return Map.of("entry", count("entry", userId), "habit_goal", count("habit_goal", userId),
                "budget", count("budget", userId), "finance_transaction", count("finance_transaction", userId),
                "habit_log", count("habit_log", userId));
    }

    private long count(String table, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = :userId").param("userId", userId)
                .query(Long.class).single();
    }
}
