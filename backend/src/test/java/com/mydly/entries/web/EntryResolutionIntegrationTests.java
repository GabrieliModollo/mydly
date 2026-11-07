package com.mydly.entries.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Confirmação de itens pelo usuário (etapa 3 do backlog, ADR 0007), contra um PostgreSQL real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EntryResolutionIntegrationTests {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void confirmaGastoSemApagarOQueOParserEntendeu() throws Exception {
        UUID id = create("Comprei pão");

        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_FINANCE", "amount": 8.50, "category": "FOOD"}""")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.needsConfirmation").value(false))
                .andExpect(jsonPath("$.items[0].type").value("FINANCE"))
                .andExpect(jsonPath("$.items[0].direction").value("EXPENSE"))
                .andExpect(jsonPath("$.items[0].amount").value(8.5))
                .andExpect(jsonPath("$.items[0].category").value("FOOD"))
                .andExpect(jsonPath("$.items[0].confidence").value(1.0))
                .andExpect(jsonPath("$.items[0].resolvedByUser").value(true))
                .andExpect(jsonPath("$.items[0].needsConfirmation").value(false))
                .andExpect(jsonPath("$.items[0].dismissed").value(false));

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT status, version,
                       parsed_json -> 0 ->> 'amount'                AS parsed_amount,
                       resolution -> 0 ->> 'action'                 AS action,
                       resolution -> 0 -> 'item' ->> 'amount'       AS resolved_amount
                FROM entry WHERE id = ?""", id);
        assertThat(row.get("status")).isEqualTo("CONFIRMED");
        assertThat(row.get("version")).isEqualTo(1L);
        assertThat(row.get("parsed_amount")).as("o parser continua sem valor").isNull();
        assertThat(row.get("action")).isEqualTo("CONFIRM_FINANCE");
        assertThat(row.get("resolved_amount")).isEqualTo("8.50");
    }

    @Test
    void confirmaEntrada() throws Exception {
        UUID id = create("Não recebi o salário");

        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_FINANCE", "amount": 3000, "direction": "INCOME", "category": "WORK"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].direction").value("INCOME"));
    }

    @Test
    void naoEntendidoPodeVirarRotina() throws Exception {
        UUID id = create("Acordei as 6");

        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_ROUTINE"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("ROUTINE"))
                .andExpect(jsonPath("$.items[0].outcome").value("DONE"))
                .andExpect(jsonPath("$.items[0].quantity").doesNotExist());
    }

    @Test
    void confirmaRotinaComQuantidade() throws Exception {
        UUID id = create("Acordei as 6");

        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_ROUTINE", "quantity": 7, "unit": "h"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(7))
                .andExpect(jsonPath("$.items[0].unit").value("h"));
    }

    @Test
    void naoFoiUmGastoDescartaOItemMantendoOTexto() throws Exception {
        UUID id = create("Comprei pão");

        resolve(id, 0, "\"0\"", """
                {"action": "DISMISS"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.text").value("Comprei pão"))
                .andExpect(jsonPath("$.items[0].dismissed").value(true))
                .andExpect(jsonPath("$.items[0].resolvedByUser").value(true))
                .andExpect(jsonPath("$.items[0].needsConfirmation").value(false));
    }

    @Test
    void versaoDesatualizadaDevolve412() throws Exception {
        UUID id = create("Comprei pão");

        resolve(id, 0, "\"5\"", """
                {"action": "DISMISS"}""")
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void semIfMatchDevolve428() throws Exception {
        UUID id = create("Comprei pão");

        resolve(id, 0, null, """
                {"action": "DISMISS"}""")
                .andExpect(status().isPreconditionRequired());
        resolve(id, 0, " ", """
                {"action": "DISMISS"}""")
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void ifMatchQueNaoEVersaoDevolve412() throws Exception {
        UUID id = create("Comprei pão");

        resolve(id, 0, "\"abc\"", """
                {"action": "DISMISS"}""")
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void itemJaResolvidoDevolve409() throws Exception {
        UUID id = create("Comprei pão");
        resolve(id, 0, "\"0\"", """
                {"action": "DISMISS"}""").andExpect(status().isOk());

        resolve(id, 0, "W/\"1\"", """
                {"action": "DISMISS"}""")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void itemQueNaoPrecisaDeConfirmacaoDevolve409() throws Exception {
        UUID id = create("Gastei 50 reais na farmácia");

        resolve(id, 0, "\"0\"", """
                {"action": "DISMISS"}""")
                .andExpect(status().isConflict());
    }

    @Test
    void indiceInexistenteDevolve404() throws Exception {
        UUID id = create("Comprei pão");

        resolve(id, 3, "\"0\"", """
                {"action": "DISMISS"}""")
                .andExpect(status().isNotFound());
    }

    @Test
    void lancamentoInexistenteDevolve404() throws Exception {
        resolve(UUID.randomUUID(), 0, "\"0\"", """
                {"action": "DISMISS"}""")
                .andExpect(status().isNotFound());
    }

    @Test
    void valorZeroOuNegativoDevolve400() throws Exception {
        UUID id = create("Comprei pão");

        for (String amount : new String[] {"0", "-5"}) {
            resolve(id, 0, "\"0\"", """
                    {"action": "CONFIRM_FINANCE", "amount": %s}""".formatted(amount))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
        }
    }

    @Test
    void gastoSemValorDevolve400() throws Exception {
        UUID id = create("Comprei pão");

        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_FINANCE"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void valorComMaisDeDuasCasasDevolve400() throws Exception {
        UUID id = create("Comprei pão");

        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_FINANCE", "amount": 8.505}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unidadeDesconhecidaOuSemQuantidadeDevolve400() throws Exception {
        UUID id = create("Acordei as 6");

        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_ROUTINE", "quantity": 1, "unit": "galão"}""")
                .andExpect(status().isBadRequest());
        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_ROUTINE", "unit": "L"}""")
                .andExpect(status().isBadRequest());
        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_ROUTINE", "quantity": 0}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void acaoAusenteDevolve400() throws Exception {
        UUID id = create("Comprei pão");

        resolve(id, 0, "\"0\"", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void statusSoViraConfirmedQuandoOUltimoItemPendenteEResolvido() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO entry (user_id, id, raw_text, occurred_at, detected_type, parsed_json, confidence, status)
                VALUES ('d3d3d3d3-0000-4000-8000-000000000001', ?, 'Comprei pão e acordei as 6', now(), NULL, ?::jsonb, 0.30, 'NEEDS_INPUT')""", id, """
                [{"type": "FINANCE", "direction": "EXPENSE", "amount": null, "confidence": 0.4, "description": "Comprei pão"},
                 {"type": "UNKNOWN", "confidence": 0.3, "description": "acordei as 6"}]""");

        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_FINANCE", "amount": 8.50}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_INPUT"))
                .andExpect(jsonPath("$.items[0].needsConfirmation").value(false))
                .andExpect(jsonPath("$.items[1].needsConfirmation").value(true));

        resolve(id, 1, "\"1\"", """
                {"action": "DISMISS"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].amount").value(8.5))
                .andExpect(jsonPath("$.items[1].dismissed").value(true));
    }

    @Test
    void fraseCompostaPedeConfirmacaoSoDoItemAmbiguo() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": "%s", "text": "Fui à academia e comprei pão"}""".formatted(id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEEDS_INPUT"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("ROUTINE"))
                .andExpect(jsonPath("$.items[0].outcome").value("DONE"))
                .andExpect(jsonPath("$.items[0].description").value("Fui à academia"))
                .andExpect(jsonPath("$.items[0].needsConfirmation").value(false))
                .andExpect(jsonPath("$.items[1].type").value("FINANCE"))
                .andExpect(jsonPath("$.items[1].description").value("comprei pão"))
                .andExpect(jsonPath("$.items[1].needsConfirmation").value(true));

        resolve(id, 1, "\"0\"", """
                {"action": "CONFIRM_FINANCE", "amount": 8.50, "category": "FOOD"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.needsConfirmation").value(false))
                .andExpect(jsonPath("$.items[0].resolvedByUser").value(false))
                .andExpect(jsonPath("$.items[1].amount").value(8.5))
                .andExpect(jsonPath("$.items[1].description").value("comprei pão"))
                .andExpect(jsonPath("$.items[1].resolvedByUser").value(true));
    }

    @Test
    void buscaPorIdTrazOEtagDaVersaoAtual() throws Exception {
        UUID id = create("Comprei pão");
        resolve(id, 0, "\"0\"", """
                {"action": "DISMISS"}""").andExpect(status().isOk());

        mvc.perform(get("/api/v1/entries/{id}", id))
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void listagemDoDiaMostraOValorEfetivo() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                {"id": "%s", "text": "Comprei pão", "occurredAt": "2024-02-10T15:00:00Z"}""".formatted(id)))
                .andExpect(status().isCreated());
        resolve(id, 0, "\"0\"", """
                {"action": "CONFIRM_FINANCE", "amount": 8.50}""").andExpect(status().isOk());

        mvc.perform(get("/api/v1/entries").param("date", "2024-02-10"))
                .andExpect(jsonPath("$[0].items[0].amount").value(8.5))
                .andExpect(jsonPath("$[0].items[0].resolvedByUser").value(true));
    }

    private UUID create(String text) throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": "%s", "text": "%s"}""".formatted(id, text)))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""));
        return id;
    }

    private ResultActions resolve(UUID id, int index, String ifMatch, String json) throws Exception {
        var request = post("/api/v1/entries/{id}/items/{index}/resolution", id, index)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mvc.perform(request);
    }
}
