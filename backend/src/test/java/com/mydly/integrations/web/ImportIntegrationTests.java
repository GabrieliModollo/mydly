package com.mydly.integrations.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydly.TestUsers;
import com.mydly.TestcontainersConfiguration;
import com.mydly.accounts.internal.AccountRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Importação de extrato e reconciliação pela API (etapa 11, ADR 0014), contra um PostgreSQL real.
 * Cada teste tem um usuário próprio, e os números vêm do resumo do dia, como o app vê.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportIntegrationTests {

    private static final AtomicInteger WEEKS = new AtomicInteger();
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private ObjectMapper json;

    private RequestPostProcessor user;
    private LocalDate day;

    @BeforeEach
    void freshUserAndDays() {
        user = TestUsers.as(accounts.create("extrato-" + UUID.randomUUID().toString().substring(0, 8), "x").id());
        day = LocalDate.of(2042, 1, 5).plusWeeks(WEEKS.incrementAndGet());
    }

    @Test
    void importaContaNosNumerosEReimportarNaoDuplica() throws Exception {
        String csv = csv(line(day, "-50,00", "DROGASIL"), line(day.plusDays(1), "3000,00", "SALARIO"));

        upload(csv).andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("CSV"))
                .andExpect(jsonPath("$.read").value(2))
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.matched").value(0))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.transactions[0].status").value("IMPORTED"));
        spent(day, 50);
        summary(day.plusDays(1)).andExpect(jsonPath("$.received").value(3000));

        upload(csv).andExpect(jsonPath("$.added").value(0)).andExpect(jsonPath("$.duplicates").value(2))
                .andExpect(jsonPath("$.transactions.length()").value(0));
        spent(day, 50);
    }

    @Test
    void lancamentoEscritoAntesCasaComOBancoENaoContaDuasVezes() throws Exception {
        UUID entry = create("Gastei 50 reais na farmácia", day);

        upload(csv(line(day.plusDays(1), "-50,00", "DROGASIL 1234")))
                .andExpect(jsonPath("$.matched").value(1))
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.transactions[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.transactions[0].matchedEntryId").value(entry.toString()))
                .andExpect(jsonPath("$.transactions[0].matchedItemIndex").value(0));
        spent(day, 50);
        spent(day.plusDays(1), 0);
    }

    @Test
    void lancamentoEscritoDepoisCasaComOQueJaFoiImportado() throws Exception {
        upload(csv(line(day.plusDays(2), "-30,00", "ACAI DO ZE")));
        spent(day.plusDays(2), 30);

        UUID entry = create("Gastei 30 no açaí", day);

        spent(day.plusDays(2), 0);
        spent(day, 30);
        transactions().andExpect(jsonPath("$[0].status").value("MATCHED"))
                .andExpect(jsonPath("$[0].matchedEntryId").value(entry.toString()));
    }

    @Test
    void naoFoiIssoDesfazALigacaoEOBancoVoltaAContar() throws Exception {
        UUID entry = create("Gastei 50 reais na farmácia", day);
        upload(csv(line(day, "-50,00", "DROGASIL")));
        spent(day, 50);

        mvc.perform(post("/api/v1/entries/{id}/items/0/resolution", entry).with(user).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\": \"REOPEN\"}"))
                .andExpect(status().isOk());

        // O lançamento saiu dos números; a transação do banco voltou a contar sozinha.
        spent(day, 50);
        transactions().andExpect(jsonPath("$[0].status").value("IMPORTED"));
    }

    @Test
    void ignorarTiraDosNumerosEContarDeNovoVolta() throws Exception {
        JsonNode imported = body(upload(csv(line(day, "-200,00", "TRANSFERENCIA PARA MINHA POUPANCA"))));
        String id = imported.at("/transactions/0/id").asText();

        setIgnored(id, true).andExpect(jsonPath("$.status").value("IGNORED"));
        spent(day, 0);
        setIgnored(id, true).andExpect(jsonPath("$.status").value("IGNORED"));

        setIgnored(id, false).andExpect(jsonPath("$.status").value("IMPORTED"));
        spent(day, 200);
    }

    @Test
    void contarDeNovoProcuraUmLancamentoParaCasar() throws Exception {
        String id = body(upload(csv(line(day, "-80,00", "MERCADO")))).at("/transactions/0/id").asText();
        setIgnored(id, true);
        UUID entry = create("Gastei 80 reais no mercado", day);

        setIgnored(id, false).andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.matchedEntryId").value(entry.toString()));
        spent(day, 80);
    }

    @Test
    void doisGastosIguaisCasamUmComCada() throws Exception {
        UUID first = create("Gastei 25 reais no uber", day);
        UUID second = create("Gastei 25 reais no uber", day.plusDays(1));

        upload(csv(line(day, "-25,00", "UBER"), line(day.plusDays(1), "-25,00", "UBER")))
                .andExpect(jsonPath("$.matched").value(2))
                .andExpect(jsonPath("$.transactions[0].matchedEntryId").value(first.toString()))
                .andExpect(jsonPath("$.transactions[1].matchedEntryId").value(second.toString()));
        spent(day, 25);
        spent(day.plusDays(1), 25);
    }

    @Test
    void valorOuDirecaoDiferenteOuForaDaJanelaNaoCasa() throws Exception {
        create("Gastei 50 reais na farmácia", day);

        upload(csv(line(day, "-50,01", "A"), line(day, "50,00", "B"), line(day.plusDays(4), "-50,00", "C"),
                line(day.minusDays(2), "-50,00", "D")))
                .andExpect(jsonPath("$.matched").value(0))
                .andExpect(jsonPath("$.added").value(4));
    }

    @Test
    void outroUsuarioNaoVeNemMudaAsTransacoes() throws Exception {
        String id = body(upload(csv(line(day, "-10,00", "X")))).at("/transactions/0/id").asText();
        RequestPostProcessor other = TestUsers.as(accounts.create("outro-" + UUID.randomUUID().toString()
                .substring(0, 8), "x").id());

        mvc.perform(get("/api/v1/imports/transactions").with(other).param("from", day.toString())
                        .param("to", day.toString()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/v1/imports/transactions/{id}", id).with(other).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ignored\": true}"))
                .andExpect(status().isNotFound());
        spent(day, 10);
    }

    @Test
    void arquivoInvalidoE400ComOMotivo() throws Exception {
        upload("Nome;Idade\nAna;30").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O CSV precisa de uma coluna de data e uma de valor, com cabeçalho."));
        mvc.perform(multipart("/api/v1/imports").file(new MockMultipartFile("file", "x.csv", "text/csv", new byte[0]))
                        .with(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O arquivo está vazio."));
    }

    private static String line(LocalDate date, String amount, String description) {
        return date.format(BR) + ";" + amount + ";" + description;
    }

    private static String csv(String... lines) {
        return "Data;Valor;Descrição\n" + String.join("\n", lines) + "\n";
    }

    private ResultActions upload(String content) throws Exception {
        return mvc.perform(multipart("/api/v1/imports")
                .file(new MockMultipartFile("file", "extrato.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8)))
                .with(user));
    }

    private UUID create(String text, LocalDate date) throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/entries").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"" + id + "\", \"text\": \"" + text + "\", \"occurredAt\": \"" + date
                                + "T15:00:00Z\"}"))
                .andExpect(status().isCreated());
        return id;
    }

    private ResultActions setIgnored(String id, boolean ignored) throws Exception {
        return mvc.perform(put("/api/v1/imports/transactions/{id}", id).with(user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ignored\": " + ignored + "}"))
                .andExpect(status().isOk());
    }

    private ResultActions transactions() throws Exception {
        return mvc.perform(get("/api/v1/imports/transactions").with(user)
                .param("from", day.minusDays(10).toString()).param("to", day.plusDays(10).toString()));
    }

    private ResultActions summary(LocalDate date) throws Exception {
        return mvc.perform(get("/api/v1/days/{date}/summary", date).with(user)).andExpect(status().isOk());
    }

    private void spent(LocalDate date, int amount) throws Exception {
        summary(date).andExpect(jsonPath("$.spent").value(amount));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
