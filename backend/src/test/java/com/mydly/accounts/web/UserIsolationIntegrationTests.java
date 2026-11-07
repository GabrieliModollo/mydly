package com.mydly.accounts.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.GoalsTestData;
import com.mydly.TestUsers;
import com.mydly.TestcontainersConfiguration;
import com.mydly.accounts.internal.AccountRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Cada usuário só vê e muda o que é seu (etapa 9, ADR 0012): lançamentos, os números que saem deles,
 * metas, limites e preferências. Os pedidos sem {@code .with(...)} são do usuário demo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserIsolationIntegrationTests {

    /** Um dia só destes testes, longe dos dias que os outros testes usam. */
    private static final LocalDate DAY = LocalDate.of(2041, 3, 13);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcClient jdbc;

    private RequestPostProcessor other;
    private UUID otherId;

    @BeforeEach
    void otherUser() {
        GoalsTestData.reset(jdbc);
        otherId = accounts.create("outra-" + UUID.randomUUID().toString().substring(0, 8), "x").id();
        other = TestUsers.as(otherId);
    }

    @AfterEach
    void reset() {
        GoalsTestData.reset(jdbc);
    }

    @Test
    void oLancamentoDeUmUsuarioNaoApareceParaOOutro() throws Exception {
        UUID id = UUID.randomUUID();
        create(id, "Gastei 50 reais na farmácia", other).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/entries").param("date", DAY.toString()).with(other))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/entries").param("date", DAY.toString()))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
        mvc.perform(get("/api/v1/entries/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/entries/{id}/items/0/resolution", id).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\": \"REOPEN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reenviarOIdDeOutroUsuarioEConflitoSemDevolverOLancamentoDele() throws Exception {
        UUID id = UUID.randomUUID();
        create(id, "Gastei 50 reais na farmácia", other).andExpect(status().isCreated());

        create(id, "Gastei 50 reais na farmácia", TestUsers.as(TestUsers.DEMO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.text").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());
        assertThat(jdbc.sql("SELECT user_id FROM entry WHERE id = ?").param(id).query(UUID.class).single())
                .isEqualTo(otherId);
    }

    @Test
    void oResumoDoDiaSoContaOQueEDoUsuario() throws Exception {
        create(UUID.randomUUID(), "Gastei 50 reais na farmácia", other).andExpect(status().isCreated());
        create(UUID.randomUUID(), "Bebi 2 litros de água", other).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/days/{date}/summary", DAY).with(other))
                .andExpect(jsonPath("$.spent").value(50))
                .andExpect(jsonPath("$.habitsAchieved").value(1));
        mvc.perform(get("/api/v1/days/{date}/summary", DAY))
                .andExpect(jsonPath("$.spent").value(0))
                .andExpect(jsonPath("$.habitsAchieved").value(0));
    }

    @Test
    void metasLimitesEPreferenciasSaoDeCadaUsuario() throws Exception {
        mvc.perform(put("/api/v1/goals/habits/{habit}", "HYDRATION").with(other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 2, \"unit\": \"L\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/budgets/{category}", "FOOD").with(other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"period\": \"WEEKLY\", \"limit\": 100}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/preferences").with(other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstDayOfWeek\": \"SUNDAY\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/goals/habits/{habit}", "HYDRATION").with(other))
                .andExpect(jsonPath("$.goal.targetQuantity").value(2));
        mvc.perform(get("/api/v1/goals/habits/{habit}", "HYDRATION"))
                .andExpect(jsonPath("$.goal").doesNotExist());
        mvc.perform(get("/api/v1/budgets/{category}", "FOOD").with(other))
                .andExpect(jsonPath("$.limit").value(100));
        mvc.perform(get("/api/v1/budgets/{category}", "FOOD"))
                .andExpect(jsonPath("$.limit").doesNotExist());
        mvc.perform(get("/api/v1/preferences").with(other))
                .andExpect(jsonPath("$.firstDayOfWeek").value("SUNDAY"));
        mvc.perform(get("/api/v1/preferences"))
                .andExpect(jsonPath("$.firstDayOfWeek").value("MONDAY"));
    }

    @Test
    void usuarioNovoComecaComASemanaNaSegunda() throws Exception {
        mvc.perform(get("/api/v1/preferences").with(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstDayOfWeek").value("MONDAY"));
    }

    private org.springframework.test.web.servlet.ResultActions create(UUID id, String text, RequestPostProcessor user)
            throws Exception {
        return mvc.perform(post("/api/v1/entries").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"" + id + "\", \"text\": \"" + text + "\", \"occurredAt\": \"" + DAY
                        + "T15:00:00Z\"}"));
    }
}
