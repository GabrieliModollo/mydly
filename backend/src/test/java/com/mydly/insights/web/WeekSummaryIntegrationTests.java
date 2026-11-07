package com.mydly.insights.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Resumo da semana pela API (etapa 6), com o relógio parado numa quarta-feira, 11 de junho de 2036,
 * ao meio-dia em São Paulo. A semana atual começa na segunda, 9 de junho.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WeekSummaryIntegrationTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @TestBean
    private Clock clock;

    @Autowired
    private MockMvc mvc;

    static Clock clock() {
        return Clock.fixed(Instant.parse("2036-06-11T15:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void semanaAtualEParcialEOsDiasQueNaoChegaramFicamParaDepois() throws Exception {
        create("Bebi 2 litros de água", noon(LocalDate.of(2036, 6, 9)));
        create("Bebi 2 litros de água", noon(LocalDate.of(2036, 6, 10)));

        mvc.perform(get("/api/v1/weeks/current/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2036-06-09"))
                .andExpect(jsonPath("$.weekEnd").value("2036-06-15"))
                .andExpect(jsonPath("$.daysElapsed").value(3))
                .andExpect(jsonPath("$.tone").value("direct"))
                .andExpect(jsonPath("$.habits[0].code").value("HYDRATION"))
                .andExpect(jsonPath("$.habits[0].days[0]").value("ACHIEVED"))
                .andExpect(jsonPath("$.habits[0].days[1]").value("ACHIEVED"))
                .andExpect(jsonPath("$.habits[0].days[2]").value("NONE"))
                .andExpect(jsonPath("$.habits[0].days[3]").value("FUTURE"))
                .andExpect(jsonPath("$.habits[0].days[6]").value("FUTURE"))
                .andExpect(jsonPath("$.habits[0].score.achieved").value(2))
                .andExpect(jsonPath("$.habits[0].score.elapsedDays").value(3))
                .andExpect(jsonPath("$.habits[0].score.target").isEmpty())
                .andExpect(jsonPath("$.insights[0].kind").value("OVERVIEW"));
    }

    /**
     * O smoke test da especificação: três dias de sono ruim com gasto maior e quatro de sono bom. O
     * cruzamento aparece com os números certos.
     */
    @Test
    void cruzamentoDeSonoEGastoApareceComOsNumerosDaSemana() throws Exception {
        LocalDate monday = LocalDate.of(2036, 5, 26);
        for (int day = 0; day < 7; day++) {
            boolean badSleep = day < 3;
            create(badSleep ? "Dormi mal" : "Dormi bem", noon(monday.plusDays(day)).minusSeconds(3600));
            create(badSleep ? "Gastei 60 reais" : "Gastei 30 reais", noon(monday.plusDays(day)));
        }

        week(monday)
                .andExpect(jsonPath("$.daysElapsed").value(7))
                .andExpect(jsonPath("$.money.spent").value(300))
                .andExpect(jsonPath("$.previous").doesNotExist())
                .andExpect(jsonPath("$.insights[?(@.kind == 'SLEEP_AND_SPENDING')].numbers.badDays").value(3))
                .andExpect(jsonPath("$.insights[?(@.kind == 'SLEEP_AND_SPENDING')].numbers.restDays").value(4))
                .andExpect(jsonPath("$.insights[?(@.kind == 'SLEEP_AND_SPENDING')].numbers.avgBad").value(60.0))
                .andExpect(jsonPath("$.insights[?(@.kind == 'SLEEP_AND_SPENDING')].numbers.avgRest").value(30.0))
                .andExpect(jsonPath("$.insights[?(@.kind == 'SLEEP_AND_SPENDING')].text")
                        .value(hasItem(containsString("R$ 60,00"))));
    }

    @Test
    void comSoDoisDiasDeSonoRuimOCruzamentoNaoAparece() throws Exception {
        LocalDate monday = LocalDate.of(2036, 5, 12);
        for (int day = 0; day < 7; day++) {
            boolean badSleep = day < 2;
            create(badSleep ? "Dormi mal" : "Dormi bem", noon(monday.plusDays(day)).minusSeconds(3600));
            create(badSleep ? "Gastei 60 reais" : "Gastei 30 reais", noon(monday.plusDays(day)));
        }

        week(monday).andExpect(jsonPath("$.insights[?(@.kind == 'SLEEP_AND_SPENDING')]").isEmpty());
    }

    @Test
    void domingoANoiteContaNaSemanaDoDomingoNoFusoDeSaoPaulo() throws Exception {
        // 02:30Z de segunda, 5 de maio, ainda é domingo, 4 de maio, 23:30 em São Paulo.
        create("Gastei 40 reais", Instant.parse("2036-05-05T02:30:00Z"));

        week(LocalDate.of(2036, 4, 28)).andExpect(jsonPath("$.money.spent").value(40));
        week(LocalDate.of(2036, 5, 5)).andExpect(jsonPath("$.money.spent").value(0));
    }

    @Test
    void semanaSemDadosRecebeBoasVindas() throws Exception {
        week(LocalDate.of(2036, 4, 14))
                .andExpect(jsonPath("$.habits").isEmpty())
                .andExpect(jsonPath("$.money.spent").value(0))
                .andExpect(jsonPath("$.previous").doesNotExist())
                .andExpect(jsonPath("$.insights.length()").value(1))
                .andExpect(jsonPath("$.insights[0].kind").value("WELCOME"));
    }

    @Test
    void gastoAcimaDaSemanaAnteriorComparaComOsNumerosDela() throws Exception {
        create("Gastei 100 reais", noon(LocalDate.of(2036, 3, 25)));
        create("Gastei 150 reais", noon(LocalDate.of(2036, 4, 1)));

        mvc.perform(get("/api/v1/weeks/{monday}/summary", "2036-03-31").param("tone", "scold"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tone").value("scold"))
                .andExpect(jsonPath("$.previous.spent").value(100))
                .andExpect(jsonPath("$.insights[0].kind").value("SPENDING_UP"))
                .andExpect(jsonPath("$.insights[0].numbers.percent").value(50))
                .andExpect(jsonPath("$.insights[0].text").value(containsString("Segura a carteira")));
    }

    @Test
    void dataQueNaoESegundaDevolve400() throws Exception {
        mvc.perform(get("/api/v1/weeks/{monday}/summary", "2036-06-10")).andExpect(status().isBadRequest());
    }

    @Test
    void tomDesconhecidoDevolve400() throws Exception {
        mvc.perform(get("/api/v1/weeks/current/summary").param("tone", "grosso")).andExpect(status().isBadRequest());
    }

    private static Instant noon(LocalDate day) {
        return day.atTime(12, 0).atZone(ZONE).toInstant();
    }

    private void create(String text, Instant occurredAt) throws Exception {
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": "%s", "text": "%s", "occurredAt": "%s"}""".formatted(UUID.randomUUID(), text, occurredAt)))
                .andExpect(status().isCreated());
    }

    private ResultActions week(LocalDate monday) throws Exception {
        return mvc.perform(get("/api/v1/weeks/{monday}/summary", monday)).andExpect(status().isOk());
    }
}
