package com.mydly.insights.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Fluxo completo da etapa 5 pela API: o lançamento vira transação ou hábito pelos eventos, e o
 * resumo do dia reflete o {@code Não foi isso} e a correção (ADR 0008).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DaySummaryIntegrationTests {

    private static final AtomicInteger DAYS = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    private LocalDate day;

    /** Um dia só de cada teste, depois de 2019: sem horário de verão, São Paulo é sempre UTC-3. */
    @BeforeEach
    void freshDay() {
        day = LocalDate.of(2033, 1, 1).plusDays(DAYS.incrementAndGet() * 2L);
    }

    @Test
    void naoFoiIssoTiraOGastoDoResumoEACorrecaoVolta() throws Exception {
        UUID id = create("Gastei 50 reais na farmácia", day + "T15:00:00Z");
        summary().andExpect(jsonPath("$.spent").value(50))
                .andExpect(jsonPath("$.spentByCategory[0].category").value("PHARMACY"));

        resolve(id, 0, 0, "{\"action\": \"REOPEN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_INPUT"))
                .andExpect(jsonPath("$.items[0].state").value("PENDING"))
                .andExpect(jsonPath("$.items[0].revision").value(2))
                .andExpect(jsonPath("$.items[0].needsConfirmation").value(true))
                .andExpect(jsonPath("$.items[0].canReopen").value(false));
        summary().andExpect(jsonPath("$.spent").value(0));

        resolve(id, 0, 1, "{\"action\": \"CONFIRM_FINANCE\", \"amount\": 35, \"category\": \"PHARMACY\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].state").value("ACCEPTED_BY_USER"))
                .andExpect(jsonPath("$.items[0].revision").value(3))
                .andExpect(jsonPath("$.items[0].canReopen").value(true));
        summary().andExpect(jsonPath("$.spent").value(35));
    }

    @Test
    void habitosCumpridosContamHabitoRuimEvitadoEDeslize() throws Exception {
        create("Bebi 2 litros de água", day + "T12:00:00Z");
        create("Não comi besteira", day + "T13:00:00Z");
        UUID slip = create("Tomei 2 energéticos", day + "T14:00:00Z");
        create("Meditei", day + "T15:00:00Z");

        summary().andExpect(jsonPath("$.habitsAchieved").value(2))
                .andExpect(jsonPath("$.slips").value(1))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[2].entryId").value(slip.toString()))
                .andExpect(jsonPath("$.items[2].habitName").value("Energético"))
                .andExpect(jsonPath("$.items[2].status").value("SLIPPED"))
                .andExpect(jsonPath("$.items[3].habit").doesNotExist());
    }

    @Test
    void itemPendenteSoEntraNoResumoDepoisDeConfirmado() throws Exception {
        UUID id = create("Fui à academia e comprei pão", day + "T12:00:00Z");
        summary().andExpect(jsonPath("$.spent").value(0)).andExpect(jsonPath("$.habitsAchieved").value(1));

        resolve(id, 1, 0, "{\"action\": \"CONFIRM_FINANCE\", \"amount\": 8.50}").andExpect(status().isOk());

        summary().andExpect(jsonPath("$.spent").value(8.5));
    }

    @Test
    void descarteDesfeitoVoltaAPedirConfirmacao() throws Exception {
        UUID id = create("Comprei pão", day + "T12:00:00Z");
        resolve(id, 0, 0, "{\"action\": \"DISMISS\"}")
                .andExpect(jsonPath("$.items[0].state").value("DISMISSED"))
                .andExpect(jsonPath("$.items[0].canReopen").value(true));

        resolve(id, 0, 1, "{\"action\": \"REOPEN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].state").value("PENDING"));
        resolve(id, 0, 2, "{\"action\": \"CONFIRM_FINANCE\", \"amount\": 7}").andExpect(status().isOk());

        summary().andExpect(jsonPath("$.spent").value(7));
    }

    @Test
    void reabrirItemPendenteDevolve409() throws Exception {
        UUID id = create("Comprei pão", day + "T12:00:00Z");

        resolve(id, 0, 0, "{\"action\": \"REOPEN\"}").andExpect(status().isConflict());
    }

    @Test
    void resumoUsaODiaDeSaoPaulo() throws Exception {
        // 02:30Z do dia seguinte ainda são 23:30 deste dia em São Paulo.
        create("Gastei 20 reais no uber", day.plusDays(1) + "T02:30:00Z");

        summary().andExpect(jsonPath("$.spent").value(20));
        mvc.perform(get("/api/v1/days/{date}/summary", day.plusDays(1))).andExpect(jsonPath("$.spent").value(0));
    }

    @Test
    void dataInvalidaDevolve400() throws Exception {
        mvc.perform(get("/api/v1/days/{date}/summary", "ontem")).andExpect(status().isBadRequest());
    }

    private UUID create(String text, String occurredAt) throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": "%s", "text": "%s", "occurredAt": "%s"}""".formatted(id, text, occurredAt)))
                .andExpect(status().isCreated());
        return id;
    }

    private ResultActions resolve(UUID id, int index, long version, String json) throws Exception {
        return mvc.perform(post("/api/v1/entries/{id}/items/{index}/resolution", id, index)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"" + version + "\"")
                .content(json));
    }

    private ResultActions summary() throws Exception {
        return mvc.perform(get("/api/v1/days/{date}/summary", day)).andExpect(status().isOk());
    }
}
