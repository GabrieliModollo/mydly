package com.mydly.insights.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Semana que começa no domingo ({@code mydly.week.first-day-of-week=SUNDAY}): nada no código supõe
 * segunda. Hoje é quarta, 9 de junho de 2038; a semana começa no domingo, 6 de junho.
 */
@SpringBootTest(properties = "mydly.week.first-day-of-week=SUNDAY")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SundayWeekIntegrationTests {

    @TestBean
    private Clock clock;

    @Autowired
    private MockMvc mvc;

    static Clock clock() {
        return Clock.fixed(Instant.parse("2038-06-09T15:00:00Z"), ZoneOffset.UTC);
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
    void segundaNaoEInicioDeSemanaQuandoElaComecaNoDomingo() throws Exception {
        mvc.perform(get("/api/v1/weeks/{start}/summary", "2038-06-07")).andExpect(status().isBadRequest());
    }

    private void create(String text, String occurredAt) throws Exception {
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": "%s", "text": "%s", "occurredAt": "%s"}""".formatted(UUID.randomUUID(), text, occurredAt)))
                .andExpect(status().isCreated());
    }
}
