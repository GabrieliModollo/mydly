package com.mydly.preferences.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.GoalsTestData;
import com.mydly.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Preferências pela API (etapa 7, ADR 0010). O efeito na semana fica em
 * {@code SundayWeekIntegrationTests}, no módulo insights. O relógio fixo dá a esta classe um contexto
 * próprio, porque a preferência é global.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PreferencesIntegrationTests {

    @TestBean
    private Clock clock;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    static Clock clock() {
        return Clock.fixed(Instant.parse("2039-03-09T15:00:00Z"), ZoneOffset.UTC);
    }

    @AfterEach
    void reset() {
        GoalsTestData.reset(jdbc);
    }

    @Test
    void comecaNaSegundaComoNaMigracao() throws Exception {
        mvc.perform(get("/api/v1/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstDayOfWeek").value("MONDAY"));
    }

    @Test
    void trocarParaDomingoGravaAPreferencia() throws Exception {
        mvc.perform(put("/api/v1/preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstDayOfWeek\": \"SUNDAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstDayOfWeek").value("SUNDAY"));

        mvc.perform(get("/api/v1/preferences")).andExpect(jsonPath("$.firstDayOfWeek").value("SUNDAY"));
    }

    @ParameterizedTest(name = "corpo {0}")
    @ValueSource(strings = {"{\"firstDayOfWeek\": \"FUNDAY\"}", "{\"firstDayOfWeek\": null}", "{}",
            "não é json"})
    void valorInvalidoDevolve400EmProblemDetail(String body) throws Exception {
        mvc.perform(put("/api/v1/preferences").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/api/v1/preferences")).andExpect(jsonPath("$.firstDayOfWeek").value("MONDAY"));
    }
}
