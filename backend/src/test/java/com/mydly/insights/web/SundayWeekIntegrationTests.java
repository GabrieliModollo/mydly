package com.mydly.insights.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.GoalsTestData;
import com.mydly.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Semana que começa no domingo, escolhida nas preferências ({@code PUT /api/v1/preferences}): nada no
 * código supõe segunda. Hoje é quarta, 9 de junho de 2038; a semana começa no domingo, 6 de junho.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SundayWeekIntegrationTests {

    @TestBean
    private Clock clock;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    static Clock clock() {
        return Clock.fixed(Instant.parse("2038-06-09T15:00:00Z"), ZoneOffset.UTC);
    }

    @BeforeEach
    void sundayFirst() throws Exception {
        mvc.perform(put("/api/v1/preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstDayOfWeek\": \"SUNDAY\"}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void reset() {
        GoalsTestData.reset(jdbc);
    }

    @Test
    void semanaAtualComecaNoDomingo() throws Exception {
        mvc.perform(get("/api/v1/weeks/current/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2038-06-06"))
                .andExpect(jsonPath("$.weekEnd").value("2038-06-12"))
                .andExpect(jsonPath("$.daysElapsed").value(4));
    }

    @Test
    void sabadoFechaASemanaEDomingoAbreAProxima() throws Exception {
        create("Gastei 40 reais", "2038-06-05T15:00:00Z");
        create("Gastei 25 reais", "2038-06-06T15:00:00Z");

        mvc.perform(get("/api/v1/weeks/{start}/summary", "2038-05-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.money.spent").value(40));
        mvc.perform(get("/api/v1/weeks/{start}/summary", "2038-06-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.money.spent").value(25));
    }

    @Test
    void qualquerDiaPodeSerOInicio() throws Exception {
        mvc.perform(put("/api/v1/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstDayOfWeek\": \"THURSDAY\"}")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/weeks/current/summary"))
                .andExpect(jsonPath("$.weekStart").value("2038-06-03"))
                .andExpect(jsonPath("$.weekEnd").value("2038-06-09"));
    }

    @Test
    void segundaNaoEInicioDeSemanaQuandoElaComecaNoDomingo() throws Exception {
        mvc.perform(get("/api/v1/weeks/{start}/summary", "2038-06-07")).andExpect(status().isBadRequest());
    }

    /**
     * Ao trocar o dia de início, as semanas passadas também passam a ser vistas na definição nova: o
     * início antigo deixa de valer, e a semana atual é achada por {@code /weeks/current}.
     */
    @Test
    void trocarODiaRecalculaAsSemanasPassadas() throws Exception {
        create("Gastei 10 reais", "2038-05-17T15:00:00Z"); // segunda
        create("Gastei 20 reais", "2038-05-23T15:00:00Z"); // domingo

        // Domingo a sábado: a segunda (17/5) fica na semana de 16/5, e o domingo (23/5) abre a seguinte.
        mvc.perform(get("/api/v1/weeks/{start}/summary", "2038-05-16")).andExpect(jsonPath("$.money.spent").value(10));

        mvc.perform(put("/api/v1/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstDayOfWeek\": \"MONDAY\"}")).andExpect(status().isOk());

        // Segunda a domingo: as duas caem na mesma semana, a de 17/5.
        mvc.perform(get("/api/v1/weeks/{start}/summary", "2038-05-16")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/weeks/{start}/summary", "2038-05-17")).andExpect(jsonPath("$.money.spent").value(30));
        mvc.perform(get("/api/v1/weeks/current/summary")).andExpect(jsonPath("$.weekStart").value("2038-06-07"));
    }

    private void create(String text, String occurredAt) throws Exception {
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": "%s", "text": "%s", "occurredAt": "%s"}""".formatted(UUID.randomUUID(), text, occurredAt)))
                .andExpect(status().isCreated());
    }
}
