package com.mydly.insights.web;

import static org.hamcrest.Matchers.containsString;
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
 * Comparação justa com a semana anterior: numa quarta-feira (10 de junho de 2037), a semana atual é
 * comparada com segunda a quarta da semana anterior, e não com a semana inteira.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PartialWeekComparisonIntegrationTests {

    @TestBean
    private Clock clock;

    @Autowired
    private MockMvc mvc;

    static Clock clock() {
        return Clock.fixed(Instant.parse("2037-06-10T15:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void quartaComCompraGrandeNaSegundaComparaComOsMesmosDias() throws Exception {
        create("Gastei 100 reais", "2037-06-01T15:00:00Z");
        // A quinta da semana anterior ainda não tem par nesta semana e não entra na comparação.
        create("Gastei 1000 reais", "2037-06-04T15:00:00Z");
        create("Gastei 300 reais", "2037-06-08T15:00:00Z");

        mvc.perform(get("/api/v1/weeks/current/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysElapsed").value(3))
                .andExpect(jsonPath("$.previous.spent").value(100))
                .andExpect(jsonPath("$.previous.comparedDays").value(3))
                .andExpect(jsonPath("$.insights[0].kind").value("SPENDING_UP"))
                .andExpect(jsonPath("$.insights[0].numbers.percent").value(200))
                .andExpect(jsonPath("$.insights[0].text").value(containsString("até quarta")));
    }

    private void create(String text, String occurredAt) throws Exception {
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": "%s", "text": "%s", "occurredAt": "%s"}""".formatted(UUID.randomUUID(), text, occurredAt)))
                .andExpect(status().isCreated());
    }
}
