package com.mydly.finance.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Orçamentos pela API (etapa 7, ADR 0010): limiares, período semanal e mensal, versão do mesmo dia e
 * {@code valid_from} no fuso de São Paulo, com um relógio que cada teste acerta. Hoje, por padrão, é
 * quarta-feira, 11 de maio de 2039; cada teste usa dias próprios. O orçamento nos resumos do dia e da
 * semana fica em {@code BudgetSummaryIntegrationTests}, no módulo insights.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BudgetIntegrationTests {

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

    /** R$ 100 por semana em Alimentação e R$ 85 de gasto dão o aviso de 80%. */
    @Test
    void oitentaECincoDeCemEAviso() throws Exception {
        putBudget("FOOD", "{\"period\": \"WEEKLY\", \"limit\": 100}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.period").value("WEEKLY"))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.validFrom").value("2039-05-11"))
                .andExpect(jsonPath("$.progress.periodStart").value("2039-05-09"))
                .andExpect(jsonPath("$.progress.periodEnd").value("2039-05-15"))
                .andExpect(jsonPath("$.progress.spent").value(0))
                .andExpect(jsonPath("$.progress.status").value("OK"));

        // O gasto de segunda conta: o período inteiro vale, mesmo antes de o limite existir.
        create("Gastei 50 reais no almoço", at(LocalDate.of(2039, 5, 9), 12));
        create("Gastei 35 reais no lanche", at(LocalDate.of(2039, 5, 11), 10));

        mvc.perform(get("/api/v1/budgets/{category}", "FOOD"))
                .andExpect(jsonPath("$.progress.spent").value(85))
                .andExpect(jsonPath("$.progress.percent").value(85))
                .andExpect(jsonPath("$.progress.status").value("NEAR"));
        mvc.perform(get("/api/v1/budgets"))
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].category").value("FOOD"))
                .andExpect(jsonPath("$[1].category").value("GROCERIES"))
                .andExpect(jsonPath("$[1].limit").doesNotExist())
                .andExpect(jsonPath("$[1].progress").doesNotExist());
    }

    @Test
    void exatamenteNoLimiteEAvisoEAcimaEEstouro() throws Exception {
        LocalDate monday = LocalDate.of(2039, 5, 16);
        CLOCK.set(at(monday, 9));
        putBudget("LEISURE", "{\"period\": \"WEEKLY\", \"limit\": 100}").andExpect(status().isOk());
        create("Gastei 100 reais no cinema", at(monday, 8));
        mvc.perform(get("/api/v1/budgets/{category}", "LEISURE"))
                .andExpect(jsonPath("$.progress.percent").value(100))
                .andExpect(jsonPath("$.progress.status").value("NEAR"));

        create("Gastei 2 reais no bar", at(monday, 8).plusSeconds(60));
        mvc.perform(get("/api/v1/budgets/{category}", "LEISURE"))
                .andExpect(jsonPath("$.progress.percent").value(102))
                .andExpect(jsonPath("$.progress.status").value("OVER"));
    }

    /**
     * Com a semana começando no domingo, o orçamento semanal vai de domingo a sábado: o gasto de
     * domingo conta e o de sábado fica na semana anterior.
     */
    @Test
    void orcamentoSemanalSegueODomingoComoInicio() throws Exception {
        mvc.perform(put("/api/v1/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstDayOfWeek\": \"SUNDAY\"}")).andExpect(status().isOk());
        CLOCK.set(at(LocalDate.of(2039, 5, 25), 12));
        putBudget("TRANSPORT", "{\"period\": \"WEEKLY\", \"limit\": 50}").andExpect(status().isOk());
        create("Gastei 30 reais de uber", at(LocalDate.of(2039, 5, 21), 12));
        create("Gastei 20 reais de uber", at(LocalDate.of(2039, 5, 22), 12));

        mvc.perform(get("/api/v1/budgets/{category}", "TRANSPORT"))
                .andExpect(jsonPath("$.progress.periodStart").value("2039-05-22"))
                .andExpect(jsonPath("$.progress.periodEnd").value("2039-05-28"))
                .andExpect(jsonPath("$.progress.spent").value(20))
                .andExpect(jsonPath("$.progress.percent").value(40));
    }

    /**
     * Mês civil em São Paulo: um gasto em 30/11 às 23:30 (01/12 02:30Z) conta em novembro, e o
     * orçamento de dezembro começa do zero.
     */
    @Test
    void orcamentoMensalNaViradaDoMesEmSaoPaulo() throws Exception {
        CLOCK.set("2039-11-20T15:00:00Z");
        putBudget("LEISURE", "{\"period\": \"MONTHLY\", \"limit\": 100}").andExpect(status().isOk());
        create("Gastei 90 reais no cinema", Instant.parse("2039-12-01T02:30:00Z"));
        create("Gastei 10 reais no bar", Instant.parse("2039-12-01T15:00:00Z"));

        // 01/12 02:45Z ainda é 30/11 às 23:45 em São Paulo: o mês atual é novembro.
        CLOCK.set("2039-12-01T02:45:00Z");
        mvc.perform(get("/api/v1/budgets/{category}", "LEISURE"))
                .andExpect(jsonPath("$.progress.periodStart").value("2039-11-01"))
                .andExpect(jsonPath("$.progress.periodEnd").value("2039-11-30"))
                .andExpect(jsonPath("$.progress.spent").value(90))
                .andExpect(jsonPath("$.progress.status").value("NEAR"));

        CLOCK.set("2039-12-01T16:00:00Z");
        mvc.perform(get("/api/v1/budgets/{category}", "LEISURE"))
                .andExpect(jsonPath("$.progress.periodStart").value("2039-12-01"))
                .andExpect(jsonPath("$.progress.periodEnd").value("2039-12-31"))
                .andExpect(jsonPath("$.progress.spent").value(10))
                .andExpect(jsonPath("$.progress.status").value("OK"));
    }

    @Test
    void validFromPertoDaMeiaNoiteESegundaGravacaoNoMesmoDia() throws Exception {
        CLOCK.set("2039-08-02T02:59:59Z");
        putBudget("PHARMACY", "{\"period\": \"MONTHLY\", \"limit\": 100}")
                .andExpect(jsonPath("$.validFrom").value("2039-08-01"));
        putBudget("PHARMACY", "{\"period\": \"WEEKLY\", \"limit\": 40}")
                .andExpect(jsonPath("$.validFrom").value("2039-08-01"))
                .andExpect(jsonPath("$.period").value("WEEKLY"));
        assertThat(versions("PHARMACY")).isEqualTo(1);

        CLOCK.set("2039-08-02T03:00:00Z");
        putBudget("PHARMACY", "{\"period\": \"WEEKLY\", \"limit\": 60}")
                .andExpect(jsonPath("$.validFrom").value("2039-08-02"));
        assertThat(versions("PHARMACY")).isEqualTo(2);
    }

    @Test
    void tirarOLimiteEIdempotente() throws Exception {
        mvc.perform(delete("/api/v1/budgets/{category}", "OTHER")).andExpect(status().isNoContent());
        assertThat(versions("OTHER")).isZero();

        CLOCK.set("2039-05-10T15:00:00Z");
        putBudget("OTHER", "{\"period\": \"WEEKLY\", \"limit\": 30}").andExpect(status().isOk());
        CLOCK.set("2039-05-11T15:00:00Z");
        mvc.perform(delete("/api/v1/budgets/{category}", "OTHER")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/budgets/{category}", "OTHER")).andExpect(status().isNoContent());

        assertThat(versions("OTHER")).isEqualTo(2);
        mvc.perform(get("/api/v1/budgets/{category}", "OTHER"))
                .andExpect(jsonPath("$.limit").doesNotExist())
                .andExpect(jsonPath("$.progress").doesNotExist());
    }

    @ParameterizedTest(name = "categoria {0}")
    @ValueSource(strings = {"CAFE", "food", "Food"})
    void categoriaDesconhecidaDevolve404(String category) throws Exception {
        mvc.perform(get("/api/v1/budgets/{category}", category))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        putBudget(category, "{\"period\": \"WEEKLY\", \"limit\": 30}").andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/budgets/{category}", category)).andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "corpo {0}")
    @ValueSource(strings = {"{\"period\": \"WEEKLY\", \"limit\": 0}", "{\"period\": \"WEEKLY\", \"limit\": -5}",
            "{\"period\": \"WEEKLY\", \"limit\": 10.555}", "{\"limit\": 100}", "{\"period\": \"WEEKLY\"}",
            "{\"period\": \"DAILY\", \"limit\": 100}", "não é json"})
    void corpoInvalidoDevolve400(String body) throws Exception {
        putBudget("FOOD", body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(versions("FOOD")).isZero();
    }

    @Test
    void swaggerDocumentaOsEndpointsNovos() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/goals/habits/{habitId}")))
                .andExpect(content().string(containsString("/api/v1/budgets/{category}")))
                .andExpect(content().string(containsString("/api/v1/preferences")))
                .andExpect(content().string(containsString("Academia 4 vezes por semana")));
    }

    private ResultActions putBudget(String category, String body) throws Exception {
        return mvc.perform(put("/api/v1/budgets/{category}", category).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long versions(String category) {
        return jdbc.sql("SELECT count(*) FROM budget WHERE category = :category").param("category", category)
                .query(Long.class).single();
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
