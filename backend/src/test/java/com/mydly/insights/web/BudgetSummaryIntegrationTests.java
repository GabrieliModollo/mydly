package com.mydly.insights.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.GoalsTestData;
import com.mydly.MutableClock;
import com.mydly.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Orçamentos nos resumos do dia e da semana (etapa 7, ADR 0010): o alerta do dia, os orçamentos da
 * semana, as mensagens de aviso e de estouro e o mês até o dia de referência. Hoje, por padrão, é
 * quarta-feira, 11 de maio de 2039; cada teste usa dias próprios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BudgetSummaryIntegrationTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2039-05-11T15:00:00Z"));

    @TestBean
    private Clock clock;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    static Clock clock() {
        return CLOCK;
    }

    @BeforeEach
    void reset() {
        CLOCK.set("2039-05-11T15:00:00Z");
        GoalsTestData.reset(jdbc);
    }

    /**
     * O smoke test da especificação: R$ 100 por semana em Alimentação e R$ 85 de gasto dão o aviso de
     * 80% no alerta do dia e no resumo da semana.
     */
    @Test
    void oitentaECincoDeCemAvisaNoDiaENaSemana() throws Exception {
        putBudget("FOOD", "{\"period\": \"WEEKLY\", \"limit\": 100}").andExpect(status().isOk());
        create("Gastei 50 reais no almoço", at(LocalDate.of(2039, 5, 9), 12));
        create("Gastei 35 reais no lanche", at(LocalDate.of(2039, 5, 11), 10));

        mvc.perform(get("/api/v1/days/{date}/summary", "2039-05-11"))
                .andExpect(jsonPath("$.budgetAlerts.length()").value(1))
                .andExpect(jsonPath("$.budgetAlerts[0].category").value("FOOD"))
                .andExpect(jsonPath("$.budgetAlerts[0].status").value("NEAR"))
                .andExpect(jsonPath("$.budgetAlerts[0].percent").value(85))
                .andExpect(jsonPath("$.budgetAlerts[0].limit").value(100));
        mvc.perform(get("/api/v1/weeks/current/summary"))
                .andExpect(jsonPath("$.budgets[0].category").value("FOOD"))
                .andExpect(jsonPath("$.budgets[0].percent").value(85))
                .andExpect(jsonPath("$.insights[*].kind").value(hasItem("BUDGET_NEAR")));
    }

    @Test
    void acimaDoLimiteEstouraNoResumoDaSemana() throws Exception {
        LocalDate monday = LocalDate.of(2039, 5, 16);
        CLOCK.set(at(monday, 9));
        putBudget("LEISURE", "{\"period\": \"WEEKLY\", \"limit\": 100}").andExpect(status().isOk());
        create("Gastei 100 reais no cinema", at(monday, 8));
        // Exatamente no limite ainda é aviso.
        mvc.perform(get("/api/v1/weeks/current/summary"))
                .andExpect(jsonPath("$.insights[*].kind").value(hasItem("BUDGET_NEAR")));

        create("Gastei 2 reais no bar", at(monday, 8).plusSeconds(60));
        mvc.perform(get("/api/v1/weeks/current/summary"))
                .andExpect(jsonPath("$.insights[0].kind").value("BUDGET_OVER"))
                .andExpect(jsonPath("$.insights[0].numbers.category").value("LEISURE"))
                .andExpect(jsonPath("$.insights[0].numbers.percent").value(102));
    }

    @Test
    void comASemanaNoDomingoOResumoTrazOOrcamentoDeDomingoASabado() throws Exception {
        mvc.perform(put("/api/v1/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstDayOfWeek\": \"SUNDAY\"}")).andExpect(status().isOk());
        CLOCK.set(at(LocalDate.of(2039, 5, 25), 12));
        putBudget("TRANSPORT", "{\"period\": \"WEEKLY\", \"limit\": 50}").andExpect(status().isOk());
        create("Gastei 30 reais de uber", at(LocalDate.of(2039, 5, 21), 12));
        create("Gastei 20 reais de uber", at(LocalDate.of(2039, 5, 22), 12));

        mvc.perform(get("/api/v1/weeks/current/summary"))
                .andExpect(jsonPath("$.weekStart").value("2039-05-22"))
                .andExpect(jsonPath("$.budgets[0].periodStart").value("2039-05-22"))
                .andExpect(jsonPath("$.budgets[0].spent").value(20));
    }

    /** O alerta do dia 30/11 usa novembro inteiro: o gasto de 30/11 às 23:30 em São Paulo conta nele. */
    @Test
    void alertaDoDiaUsaOMesCivilEmSaoPaulo() throws Exception {
        CLOCK.set("2039-11-20T15:00:00Z");
        putBudget("LEISURE", "{\"period\": \"MONTHLY\", \"limit\": 100}").andExpect(status().isOk());
        create("Gastei 90 reais no cinema", Instant.parse("2039-12-01T02:30:00Z"));
        create("Gastei 10 reais no bar", Instant.parse("2039-12-01T15:00:00Z"));
        CLOCK.set("2039-12-01T16:00:00Z");

        mvc.perform(get("/api/v1/days/{date}/summary", "2039-11-30"))
                .andExpect(jsonPath("$.budgetAlerts[0].category").value("LEISURE"))
                .andExpect(jsonPath("$.budgetAlerts[0].period").value("MONTHLY"))
                .andExpect(jsonPath("$.budgetAlerts[0].spent").value(90))
                .andExpect(jsonPath("$.budgetAlerts[0].status").value("NEAR"));
        mvc.perform(get("/api/v1/days/{date}/summary", "2039-12-01")).andExpect(jsonPath("$.budgetAlerts").isEmpty());
    }

    /** No resumo da semana, o mês vai do dia 1 até o dia de referência da semana. */
    @Test
    void noResumoDaSemanaOMesVaiAteODiaDeReferencia() throws Exception {
        CLOCK.set("2039-06-01T15:00:00Z");
        putBudget("HOUSING", "{\"period\": \"MONTHLY\", \"limit\": 1000}").andExpect(status().isOk());
        create("Gastei 900 reais de aluguel", at(LocalDate.of(2039, 6, 3), 12));
        create("Gastei 200 reais de luz", at(LocalDate.of(2039, 6, 20), 12));
        CLOCK.set("2039-07-01T15:00:00Z");

        // Semana de 30/5 a 5/6: referência no domingo, 5/6; o mês vai de 1/6 a 5/6.
        mvc.perform(get("/api/v1/weeks/{start}/summary", "2039-05-30"))
                .andExpect(jsonPath("$.budgets[0].periodStart").value("2039-06-01"))
                .andExpect(jsonPath("$.budgets[0].periodEnd").value("2039-06-05"))
                .andExpect(jsonPath("$.budgets[0].spent").value(900))
                .andExpect(jsonPath("$.budgets[0].status").value("NEAR"));
        // Semana de 20/6 a 26/6: o mês já soma os dois gastos e passou do limite.
        mvc.perform(get("/api/v1/weeks/{start}/summary", "2039-06-20"))
                .andExpect(jsonPath("$.budgets[0].spent").value(1100))
                .andExpect(jsonPath("$.insights[0].kind").value("BUDGET_OVER"));
    }

    private ResultActions putBudget(String category, String body) throws Exception {
        return mvc.perform(put("/api/v1/budgets/{category}", category).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZONE).toInstant();
    }

    private void create(String text, Instant occurredAt) throws Exception {
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": "%s", "text": "%s", "occurredAt": "%s"}""".formatted(UUID.randomUUID(), text, occurredAt)))
                .andExpect(status().isCreated());
    }
}
