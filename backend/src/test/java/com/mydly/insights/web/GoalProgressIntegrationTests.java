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
 * Progresso das metas de hábito nos resumos do dia e da semana (etapa 7, ADR 0010): soma das
 * quantidades com conversão de unidade, placar contra a meta, troca da meta no meio da semana, limite
 * de hábito ruim e meta em risco. Cada teste usa dias próprios de 2039, com um relógio que ele acerta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GoalProgressIntegrationTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
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

    /** O smoke test da especificação: 2 L por dia, 1 litro e depois 500 mL dão 1,5 de 2 L. */
    @Test
    void metaDiariaDeAguaSomaLitrosEMililitros() throws Exception {
        LocalDate day = LocalDate.of(2039, 3, 9);
        putGoal("HYDRATION", "{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 2, \"unit\": \"L\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal.kind").value("DAILY_QUANTITY"))
                .andExpect(jsonPath("$.goal.targetQuantity").value(2))
                .andExpect(jsonPath("$.goal.unit").value("L"))
                .andExpect(jsonPath("$.goal.validFrom").value("2039-03-09"));

        create("Bebi 1 litro de água", at(day, 9));
        daySummary(day)
                .andExpect(jsonPath("$.dailyGoals[0].habit").value("HYDRATION"))
                .andExpect(jsonPath("$.dailyGoals[0].habitName").value("Hidratação"))
                .andExpect(jsonPath("$.dailyGoals[0].done").value(1.0))
                .andExpect(jsonPath("$.dailyGoals[0].target").value(2))
                .andExpect(jsonPath("$.dailyGoals[0].unit").value("L"))
                .andExpect(jsonPath("$.dailyGoals[0].met").value(false));

        create("Bebi 500 ml de água", at(day, 10));
        daySummary(day).andExpect(jsonPath("$.dailyGoals[0].done").value(1.5));

        // Copos não são volume em litros: o registro conta como hábito do dia, mas não soma.
        create("Bebi 3 copos de água", at(day, 11));
        daySummary(day)
                .andExpect(jsonPath("$.dailyGoals[0].done").value(1.5))
                .andExpect(jsonPath("$.items.length()").value(3));

        create("Bebi 500ml de água", at(day, 12));
        daySummary(day)
                .andExpect(jsonPath("$.dailyGoals[0].done").value(2.0))
                .andExpect(jsonPath("$.dailyGoals[0].met").value(true));
    }

    /** As frases do roteiro da autora, sem dizer que é água: "bebi 1 litro" e "bebi 500 ml". */
    @Test
    void bebiUmLitroSemDizerOQueEAguaContaNaMeta() throws Exception {
        LocalDate day = LocalDate.of(2039, 3, 10);
        CLOCK.set(at(day, 8));
        putGoal("HYDRATION", "{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 2, \"unit\": \"L\"}")
                .andExpect(status().isOk());

        create("bebi 1 litro", at(day, 9));
        daySummary(day)
                .andExpect(jsonPath("$.items[0].habit").value("HYDRATION"))
                .andExpect(jsonPath("$.dailyGoals[0].done").value(1.0));

        create("bebi 500 ml", at(day, 10));
        // Uma bebida dita não é água e não soma.
        create("bebi 1 litro de cerveja", at(day, 11));
        daySummary(day)
                .andExpect(jsonPath("$.dailyGoals[0].done").value(1.5))
                .andExpect(jsonPath("$.dailyGoals[0].met").value(false))
                .andExpect(jsonPath("$.items[2].habit").doesNotExist());
    }

    @Test
    void metaDiariaSemRegistroApareceZerada() throws Exception {
        putGoal("READING", "{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 30, \"unit\": \"min\"}")
                .andExpect(status().isOk());

        daySummary(LocalDate.of(2039, 3, 9))
                .andExpect(jsonPath("$.dailyGoals.length()").value(1))
                .andExpect(jsonPath("$.dailyGoals[0].habit").value("READING"))
                .andExpect(jsonPath("$.dailyGoals[0].done").value(0));
        // Antes da meta existir, o dia não tem meta diária.
        daySummary(LocalDate.of(2039, 3, 8)).andExpect(jsonPath("$.dailyGoals").isEmpty());
    }

    /** Placar contra a meta no resumo da semana: 3 de 4 na quinta, sem mensagem de risco. */
    @Test
    void metaSemanalPreencheOPlacarDoResumo() throws Exception {
        LocalDate monday = LocalDate.of(2039, 3, 21);
        CLOCK.set(at(monday, 8));
        putGoal("GYM", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 4}").andExpect(status().isOk());
        for (int day = 0; day < 3; day++) {
            create("Fui à academia", at(monday.plusDays(day), 18));
        }
        CLOCK.set(at(monday.plusDays(3), 12));

        currentWeek()
                .andExpect(jsonPath("$.habits[0].code").value("GYM"))
                .andExpect(jsonPath("$.habits[0].score.achieved").value(3))
                .andExpect(jsonPath("$.habits[0].score.elapsedDays").value(4))
                .andExpect(jsonPath("$.habits[0].score.target").value(4))
                .andExpect(jsonPath("$.habits[0].score.goal").value("WEEKLY_FREQUENCY"))
                .andExpect(jsonPath("$.insights[?(@.kind == 'GOAL_AT_RISK')]").isEmpty());
    }

    /**
     * Meta semanal trocada na quinta, de 4 para 2 dias: na quarta a semana ainda vale 4; depois da troca,
     * e na semana encerrada, vale a versão do dia de referência, 2.
     */
    @Test
    void metaSemanalTrocadaNoMeioDaSemana() throws Exception {
        LocalDate monday = LocalDate.of(2039, 3, 28);
        CLOCK.set(at(monday, 8));
        putGoal("GYM", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 4}").andExpect(status().isOk());
        create("Fui à academia", at(monday, 18));
        create("Fui à academia", at(monday.plusDays(1), 18));

        CLOCK.set(at(monday.plusDays(2), 12));
        currentWeek().andExpect(jsonPath("$.habits[0].score.target").value(4));

        CLOCK.set(at(monday.plusDays(3), 12));
        putGoal("GYM", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 2}").andExpect(status().isOk());
        currentWeek()
                .andExpect(jsonPath("$.habits[0].score.target").value(2))
                .andExpect(jsonPath("$.insights[?(@.kind == 'GOAL_MET')].numbers.habit").value(hasItem("GYM")));

        CLOCK.set(at(monday.plusDays(7), 12));
        week(monday).andExpect(jsonPath("$.habits[0].score.target").value(2));
    }

    /**
     * Meta diária trocada na quinta, de 2 L para 3 L, bebendo 2 L todo dia: cada dia é avaliado pela meta
     * dele, então só segunda a quarta cumprem.
     */
    @Test
    void metaDiariaTrocadaNoMeioDaSemana() throws Exception {
        LocalDate monday = LocalDate.of(2039, 4, 4);
        CLOCK.set(at(monday, 8));
        putGoal("HYDRATION", "{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 2, \"unit\": \"L\"}");
        for (int day = 0; day < 7; day++) {
            create("Bebi 2 litros de água", at(monday.plusDays(day), 20));
        }
        CLOCK.set(at(monday.plusDays(3), 8));
        putGoal("HYDRATION", "{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 3, \"unit\": \"L\"}");

        CLOCK.set(at(monday.plusDays(7), 12));
        week(monday)
                .andExpect(jsonPath("$.habits[0].code").value("HYDRATION"))
                .andExpect(jsonPath("$.habits[0].score.goal").value("DAILY_QUANTITY"))
                .andExpect(jsonPath("$.habits[0].score.achieved").value(3))
                .andExpect(jsonPath("$.habits[0].score.target").value(7));
        daySummary(monday.plusDays(1))
                .andExpect(jsonPath("$.dailyGoals[0].target").value(2))
                .andExpect(jsonPath("$.dailyGoals[0].met").value(true));
        daySummary(monday.plusDays(4))
                .andExpect(jsonPath("$.dailyGoals[0].target").value(3))
                .andExpect(jsonPath("$.dailyGoals[0].met").value(false));
    }

    /** Hábito com meta e sem nenhum registro aparece no resumo; na quarta, 7 dias já não fecham. */
    @Test
    void habitoComMetaSemRegistroApareceEmRisco() throws Exception {
        LocalDate monday = LocalDate.of(2039, 4, 11);
        CLOCK.set(at(monday, 8));
        putGoal("STUDY", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 7}").andExpect(status().isOk());
        CLOCK.set(at(monday.plusDays(2), 12));

        currentWeek()
                .andExpect(jsonPath("$.habits[0].code").value("STUDY"))
                .andExpect(jsonPath("$.habits[0].days[0]").value("NONE"))
                .andExpect(jsonPath("$.habits[0].days[3]").value("FUTURE"))
                .andExpect(jsonPath("$.habits[0].score.achieved").value(0))
                .andExpect(jsonPath("$.habits[0].score.target").value(7))
                .andExpect(jsonPath("$.insights[0].kind").value("GOAL_AT_RISK"))
                .andExpect(jsonPath("$.insights[0].numbers.remaining").value(5));
    }

    /** Limite de 1 dia de energético: dois dias de deslize passam do limite na semana em curso. */
    @Test
    void limiteDeHabitoRuimNoResumo() throws Exception {
        LocalDate monday = LocalDate.of(2039, 4, 18);
        CLOCK.set(at(monday, 8));
        putGoal("ENERGY_DRINK", "{\"kind\": \"WEEKLY_FREQUENCY\", \"daysPerWeek\": 1}").andExpect(status().isOk());
        create("Tomei 2 energéticos", at(monday, 15));
        create("Tomei 1 energético", at(monday.plusDays(1), 15));
        CLOCK.set(at(monday.plusDays(2), 12));

        currentWeek()
                .andExpect(jsonPath("$.habits[0].code").value("ENERGY_DRINK"))
                .andExpect(jsonPath("$.habits[0].score.limit").value(1))
                .andExpect(jsonPath("$.habits[0].score.slips").value(2))
                .andExpect(jsonPath("$.habits[0].score.target").isEmpty())
                .andExpect(jsonPath("$.insights[0].kind").value("GOAL_AT_RISK"))
                .andExpect(jsonPath("$.insights[0].numbers.slips").value(2))
                .andExpect(jsonPath("$.insights[0].numbers.limit").value(1));
    }

    private ResultActions putGoal(String habit, String body) throws Exception {
        return mvc.perform(put("/api/v1/goals/habits/{habit}", habit).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions daySummary(LocalDate day) throws Exception {
        return mvc.perform(get("/api/v1/days/{date}/summary", day)).andExpect(status().isOk());
    }

    private ResultActions currentWeek() throws Exception {
        return mvc.perform(get("/api/v1/weeks/current/summary")).andExpect(status().isOk());
    }

    private ResultActions week(LocalDate start) throws Exception {
        return mvc.perform(get("/api/v1/weeks/{start}/summary", start)).andExpect(status().isOk());
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
