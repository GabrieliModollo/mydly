package com.mydly.entries.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Fluxo completo de um lançamento: HTTP → parser → PostgreSQL real (Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EntryApiIntegrationTests {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void criaLancamentoFinanceiro() throws Exception {
        UUID id = UUID.randomUUID();

        postEntry("""
                {"id": "%s", "text": "Gastei 50 reais na farmácia"}""".formatted(id))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/entries/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.text").value("Gastei 50 reais na farmácia"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.needsConfirmation").value(false))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("FINANCE"))
                .andExpect(jsonPath("$.items[0].direction").value("EXPENSE"))
                .andExpect(jsonPath("$.items[0].amount").value(50));

        Map<String, Object> row = row(id);
        assertThat(row.get("raw_text")).isEqualTo("Gastei 50 reais na farmácia");
        assertThat(row.get("detected_type")).isEqualTo("FINANCE");
        assertThat((BigDecimal) row.get("confidence")).isEqualByComparingTo("0.95");
        assertThat(row.get("status")).isEqualTo("CONFIRMED");
        assertThat(row.get("item_type")).isEqualTo("FINANCE");
        assertThat(row.get("item_amount")).isEqualTo("50");
    }

    @Test
    void criaLancamentoDeRotina() throws Exception {
        UUID id = UUID.randomUUID();

        postEntry("""
                {"id": "%s", "text": "Bebi 2 litros de água"}""".formatted(id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].type").value("ROUTINE"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unit").value("L"))
                .andExpect(jsonPath("$.items[0].direction").doesNotExist());

        assertThat(row(id)).containsEntry("detected_type", "ROUTINE").containsEntry("status", "CONFIRMED");
    }

    @Test
    void habitoNaoCumpridoChegaComoMissedEFicaNoBanco() throws Exception {
        UUID id = UUID.randomUUID();

        postEntry("""
                {"id": "%s", "text": "Não fui à academia"}""".formatted(id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].type").value("ROUTINE"))
                .andExpect(jsonPath("$.items[0].outcome").value("MISSED"));

        assertThat(jdbc.queryForObject("SELECT parsed_json -> 0 ->> 'outcome' FROM entry WHERE id = ?",
                String.class, id)).isEqualTo("MISSED");
    }

    @Test
    void lancamentoDeBaixaConfiancaPrecisaDeConfirmacao() throws Exception {
        UUID id = UUID.randomUUID();

        postEntry("""
                {"id": "%s", "text": "Comprei pão"}""".formatted(id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEEDS_INPUT"))
                .andExpect(jsonPath("$.needsConfirmation").value(true))
                .andExpect(jsonPath("$.items[0].amount").doesNotExist());

        Map<String, Object> row = row(id);
        assertThat(row.get("status")).isEqualTo("NEEDS_INPUT");
        assertThat((BigDecimal) row.get("confidence")).isEqualByComparingTo("0.40");
    }

    @Test
    void lancamentoDesconhecidoFicaSemTipoNoBanco() throws Exception {
        UUID id = UUID.randomUUID();

        postEntry("""
                {"id": "%s", "text": "?!"}""".formatted(id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].type").value("UNKNOWN"));

        assertThat(row(id)).containsEntry("detected_type", null).containsEntry("status", "NEEDS_INPUT");
    }

    @Test
    void reenvioDoMesmoLancamentoDevolveOOriginalSemDuplicar() throws Exception {
        UUID id = UUID.randomUUID();
        postEntry("""
                {"id": "%s", "text": "Gastei 30 no açaí", "occurredAt": "2026-11-02T12:00:00Z"}""".formatted(id))
                .andExpect(status().isCreated());

        // Espaços extras não tornam o reenvio diferente.
        postEntry("""
                {"id": "%s", "text": "  Gastei 30   no açaí ", "occurredAt": "2026-11-02T12:00:00Z"}""".formatted(id))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.text").value("Gastei 30 no açaí"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM entry WHERE id = ?", Integer.class, id)).isOne();
    }

    @Test
    void reenvioSemDataNaoConflitaComADataGravada() throws Exception {
        UUID id = UUID.randomUUID();
        postEntry("""
                {"id": "%s", "text": "Dormi bem", "occurredAt": "2026-11-02T07:00:00Z"}""".formatted(id))
                .andExpect(status().isCreated());

        postEntry("""
                {"id": "%s", "text": "Dormi bem"}""".formatted(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurredAt").value("2026-11-02T07:00:00Z"));
    }

    @Test
    void mesmoIdComOutroTextoDevolve409SemAlterarORegistro() throws Exception {
        UUID id = UUID.randomUUID();
        postEntry("""
                {"id": "%s", "text": "Gastei 30 no açaí"}""".formatted(id))
                .andExpect(status().isCreated());

        postEntry("""
                {"id": "%s", "text": "Gastei 40 no açaí"}""".formatted(id))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        assertThat(row(id)).containsEntry("raw_text", "Gastei 30 no açaí").containsEntry("item_amount", "30");
    }

    @Test
    void mesmoIdComOutraDataDevolve409() throws Exception {
        UUID id = UUID.randomUUID();
        postEntry("""
                {"id": "%s", "text": "Dormi bem", "occurredAt": "2026-11-02T07:00:00Z"}""".formatted(id))
                .andExpect(status().isCreated());

        postEntry("""
                {"id": "%s", "text": "Dormi bem", "occurredAt": "2026-11-03T07:00:00Z"}""".formatted(id))
                .andExpect(status().isConflict());
    }

    /**
     * Lê um {@code parsed_json} no formato gravado pela primeira versão. Se uma mudança no
     * {@code ParsedEntry} impedir a leitura de dados já existentes, este teste quebra no CI.
     */
    @Test
    void leLancamentosGravadosNoFormatoV1DoParsedJson() throws Exception {
        UUID id = UUID.randomUUID();
        String fixture = new ClassPathResource("fixtures/parsed-json-v1.json").getContentAsString(StandardCharsets.UTF_8);
        jdbc.update("""
                INSERT INTO entry (user_id, id, raw_text, occurred_at, detected_type, parsed_json, confidence, status)
                VALUES ('d3d3d3d3-0000-4000-8000-000000000001', ?, 'lançamento antigo', '2026-11-01T10:00:00Z', NULL, ?::jsonb, 0.30, 'NEEDS_INPUT')""",
                id, fixture);

        mvc.perform(get("/api/v1/entries/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_INPUT"))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].type").value("FINANCE"))
                .andExpect(jsonPath("$.items[0].direction").value("EXPENSE"))
                .andExpect(jsonPath("$.items[0].amount").value(50))
                .andExpect(jsonPath("$.items[0].confidence").value(0.95))
                .andExpect(jsonPath("$.items[1].direction").value("INCOME"))
                .andExpect(jsonPath("$.items[1].amount").value(3000))
                .andExpect(jsonPath("$.items[0].outcome").doesNotExist())
                .andExpect(jsonPath("$.items[2].type").value("ROUTINE"))
                .andExpect(jsonPath("$.items[2].outcome").value("DONE"))
                .andExpect(jsonPath("$.items[2].quantity").value(2))
                .andExpect(jsonPath("$.items[2].unit").value("L"))
                .andExpect(jsonPath("$.items[3].quantity").value(30))
                .andExpect(jsonPath("$.items[3].unit").doesNotExist())
                .andExpect(jsonPath("$.items[4].type").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[4].description").value("Acordei as 6"));
    }

    @Test
    void guardaADataEnviadaPeloApp() throws Exception {
        UUID id = UUID.randomUUID();

        postEntry("""
                {"id": "%s", "text": "Dormi bem", "occurredAt": "2026-11-01T07:15:00Z"}""".formatted(id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occurredAt").value("2026-11-01T07:15:00Z"));
    }

    @Test
    void semIdNemDataOServidorGeraOsDois() throws Exception {
        String location = postEntry("""
                {"text": "Fui à academia"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.occurredAt").isNotEmpty())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Fui à academia"))
                .andExpect(jsonPath("$.items[0].type").value("ROUTINE"));
    }

    /**
     * O dia é o de São Paulo (UTC-3): 02:30Z do dia 15 ainda é 23:30 do dia 14 lá,
     * e 03:30Z já é 00:30 do dia 15.
     */
    @Test
    void listaOsLancamentosDoDiaNoFusoDeSaoPauloDoMaisRecenteParaOMaisAntigo() throws Exception {
        postEntry("""
                {"text": "Dormi bem", "occurredAt": "2025-10-15T02:30:00Z"}""").andExpect(status().isCreated());
        postEntry("""
                {"text": "Bebi 2 litros de água", "occurredAt": "2025-10-15T03:30:00Z"}""").andExpect(status().isCreated());
        postEntry("""
                {"text": "Gastei 50 reais", "occurredAt": "2025-10-16T02:59:59Z"}""").andExpect(status().isCreated());
        postEntry("""
                {"text": "Fui à academia", "occurredAt": "2025-10-16T03:00:00Z"}""").andExpect(status().isCreated());

        mvc.perform(get("/api/v1/entries").param("date", "2025-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("Gastei 50 reais"))
                .andExpect(jsonPath("$[1].text").value("Bebi 2 litros de água"));

        mvc.perform(get("/api/v1/entries").param("date", "2025-10-14"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("Dormi bem"));
    }

    @Test
    void diaSemLancamentosDevolveListaVazia() throws Exception {
        mvc.perform(get("/api/v1/entries").param("date", "2001-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void semDataListaOsLancamentosDeHoje() throws Exception {
        String id = UUID.randomUUID().toString();
        postEntry("""
                {"id": "%s", "text": "Meditei 15 minutos"}""".formatted(id)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(id)).exists());
    }

    @Test
    void dataInvalidaNaListagemDevolve400() throws Exception {
        mvc.perform(get("/api/v1/entries").param("date", "15/10/2025"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void buscaInexistenteDevolve404ComProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/entries/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void idInvalidoNaUrlDevolve400() throws Exception {
        mvc.perform(get("/api/v1/entries/nao-e-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void textoVazioDevolve400ComProblemDetail() throws Exception {
        postEntry("""
                {"text": "   "}""")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void textoAusenteDevolve400() throws Exception {
        postEntry("{}").andExpect(status().isBadRequest());
    }

    @Test
    void textoAcimaDoLimiteDevolve400() throws Exception {
        String longText = "a".repeat(CreateEntryRequest.MAX_TEXT_LENGTH + 1);

        postEntry("""
                {"text": "%s"}""".formatted(longText))
                .andExpect(status().isBadRequest());
    }

    @Test
    void textoNoLimiteEAceito() throws Exception {
        String text = "a".repeat(CreateEntryRequest.MAX_TEXT_LENGTH);

        postEntry("""
                {"text": "%s"}""".formatted(text))
                .andExpect(status().isCreated());
    }

    @Test
    void jsonMalformadoDevolve400() throws Exception {
        postEntry("{\"text\": ").andExpect(status().isBadRequest());
    }

    @Test
    void documentacaoOpenApiPublicaOEndpoint() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MyDly API"))
                .andExpect(jsonPath("$.paths['/api/v1/entries'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/entries/{id}'].get").exists());
    }

    private ResultActions postEntry(String json) throws Exception {
        return mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("""
                SELECT raw_text, detected_type, confidence, status,
                       parsed_json -> 0 ->> 'type'   AS item_type,
                       parsed_json -> 0 ->> 'amount' AS item_amount
                FROM entry WHERE id = ?""", id);
    }
}
