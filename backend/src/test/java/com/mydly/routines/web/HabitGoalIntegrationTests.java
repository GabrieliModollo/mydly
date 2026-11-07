package com.mydly.routines.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.GoalsTestData;
import com.mydly.MutableClock;
import com.mydly.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * Metas de hábito pela API (etapa 7, ADR 0010): catálogo, validação contra o hábito, versão do mesmo
 * dia e {@code valid_from} no fuso de São Paulo, com um relógio que cada teste acerta. O progresso nos
 * resumos fica em {@code GoalProgressIntegrationTests}, no módulo insights.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HabitGoalIntegrationTests {

    private static final MutableClock CLOCK = new MutableClock(Instant.parse("2039-03-09T15:00:00Z"));

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
        CLOCK.set("2039-03-09T15:00:00Z");
        GoalsTestData.reset(jdbc);
    }

    @Test
    void listaOCatalogoComMedidaTiposAceitosESemMeta() throws Exception {
        mvc.perform(get("/api/v1/goals/habits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[?(@.habit == 'HYDRATION')].measure").value("VOLUME"))
                .andExpect(jsonPath("$[?(@.habit == 'HYDRATION')].allowedUnits[*]").value(hasItem("mL")))
                .andExpect(jsonPath("$[?(@.habit == 'GYM')].measure").value("DURATION"))
                .andExpect(jsonPath("$[?(@.habit == 'DIET')].measure").value("NONE"))
                .andExpect(jsonPath("$[?(@.habit == 'DELIVERY')].polarity").value("BAD"))
                .andExpect(jsonPath("$[?(@.habit == 'DELIVERY')].allowedKinds[*]").value(hasItem("WEEKLY_FREQUENCY")))
                .andExpect(jsonPath("$[?(@.habit == 'DELIVERY')].allowedUnits[*]").isEmpty());
        mvc.perform(get("/api/v1/goals/habits/{habit}", "HYDRATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hidratação"))
                .andExpect(jsonPath("$.goal").doesNotExist());
    }

    @ParameterizedTest(name = "{0} {1}: {2}")
    @CsvSource(delimiter = '|', value = {
            "HYDRATION | {\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 2, \"unit\": \"h\"} | L ou mL",
            "STUDY     | {\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 2, \"unit\": \"L\"} | h ou min",
            "HYDRATION | {\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 3, \"unit\": \"copo\"} | L, mL, h ou min",
            "DIET      | {\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 1, \"unit\": \"L\"} | não tem medida",
            "DELIVERY  | {\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 1, \"unit\": \"L\"} | Hábito ruim",
            "GYM       | {\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 8} | 1 a 7",
            "GYM       | {\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 0} | 1 a 7",
            "DELIVERY  | {\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": -1} | 0 a 7",
            "HYDRATION | {\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 0, \"unit\": \"L\"} | maior que zero",
            "GYM       | {\"kind\": \"NONE\"} | DELETE",
    })
    void metaQueNaoCombinaComOHabitoDevolve400(String habit, String body, String message) throws Exception {
        putGoal(habit, body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(containsString(message)));
        assertThat(versions(habit)).isZero();
    }

    @ParameterizedTest(name = "corpo {0}")
    @ValueSource(strings = {"{\"kind\": \"FOO\"}", "{}", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": \"x\"}",
            "não é json"})
    void corpoInvalidoDevolve400(String body) throws Exception {
        putGoal("GYM", body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void habitoForaDoCatalogoDevolve404() throws Exception {
        mvc.perform(get("/api/v1/goals/habits/{habit}", "YOGA")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        putGoal("YOGA", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 3}").andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/goals/habits/{habit}", "YOGA")).andExpect(status().isNotFound());
    }

    @Test
    void segundaGravacaoNoMesmoDiaSubstituiAPrimeira() throws Exception {
        putGoal("GYM", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 3}").andExpect(status().isOk());
        putGoal("GYM", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 5}")
                .andExpect(jsonPath("$.goal.daysPerWeek").value(5));

        assertThat(versions("GYM")).isEqualTo(1);
        mvc.perform(get("/api/v1/goals/habits/{habit}", "GYM")).andExpect(jsonPath("$.goal.daysPerWeek").value(5));
    }

    @Test
    void tirarAMetaEIdempotente() throws Exception {
        mvc.perform(delete("/api/v1/goals/habits/{habit}", "GYM")).andExpect(status().isNoContent());
        assertThat(versions("GYM")).as("sem meta vigente, não grava nada").isZero();

        CLOCK.set("2039-03-08T15:00:00Z");
        putGoal("GYM", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 3}").andExpect(status().isOk());
        CLOCK.set("2039-03-09T15:00:00Z");
        mvc.perform(delete("/api/v1/goals/habits/{habit}", "GYM")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/goals/habits/{habit}", "GYM")).andExpect(status().isNoContent());

        assertThat(versions("GYM")).as("a meta de ontem e a remoção de hoje").isEqualTo(2);
        mvc.perform(get("/api/v1/goals/habits/{habit}", "GYM")).andExpect(jsonPath("$.goal").doesNotExist());
    }

    /**
     * {@code valid_from} é o dia em São Paulo: 02:59:59Z ainda é 23:59:59 do dia anterior, e 03:00Z já é
     * o dia seguinte.
     */
    @Test
    void validFromPertoDaMeiaNoiteSegueOFusoDeSaoPaulo() throws Exception {
        CLOCK.set("2039-03-15T02:59:59Z");
        putGoal("READING", "{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 30, \"unit\": \"min\"}")
                .andExpect(jsonPath("$.goal.validFrom").value("2039-03-14"));

        CLOCK.set("2039-03-15T03:00:00Z");
        putGoal("READING", "{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 45, \"unit\": \"min\"}")
                .andExpect(jsonPath("$.goal.validFrom").value("2039-03-15"));

        assertThat(versions("READING")).isEqualTo(2);
        mvc.perform(get("/api/v1/goals/habits/{habit}", "READING"))
                .andExpect(jsonPath("$.goal.targetQuantity").value(45))
                .andExpect(jsonPath("$.goal.validFrom").value("2039-03-15"));
    }

    private ResultActions putGoal(String habit, String body) throws Exception {
        return mvc.perform(put("/api/v1/goals/habits/{habit}", habit).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long versions(String habit) {
        return jdbc.sql("SELECT count(*) FROM habit_goal WHERE habit_code = :habit").param("habit", habit)
                .query(Long.class).single();
    }
}
