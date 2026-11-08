package com.mydly.integrations.internal.openfinance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydly.TestUsers;
import com.mydly.TestcontainersConfiguration;
import com.mydly.accounts.internal.AccountRepository;
import com.mydly.integrations.internal.openfinance.PluggyModels.Account;
import com.mydly.integrations.internal.openfinance.PluggyModels.Connector;
import com.mydly.integrations.internal.openfinance.PluggyModels.Item;
import com.mydly.integrations.internal.openfinance.PluggyModels.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.ResourceAccessException;

/**
 * Open Finance pela API (etapa 12, ADR 0015), com o Pluggy simulado e um PostgreSQL real. As
 * transações entram pela mesma importação e reconciliação do extrato (ADR 0014).
 */
@SpringBootTest(properties = {
        "mydly.open-finance.client-id=id-de-teste",
        "mydly.open-finance.client-secret=segredo-de-teste"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenFinanceIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private PluggyClient pluggy;

    private UUID userId;
    private RequestPostProcessor user;
    private String itemId;
    private LocalDate today;

    @BeforeEach
    void freshUserAndItem() {
        userId = accounts.create("of-" + UUID.randomUUID().toString().substring(0, 8), "x").id();
        user = TestUsers.as(userId);
        itemId = UUID.randomUUID().toString();
        today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(pluggy.item(itemId)).thenReturn(Optional.of(item("UPDATED", true, userId.toString())));
        when(pluggy.accounts(itemId)).thenReturn(List.of(new Account("conta-" + itemId, "BANK", "Corrente")));
        when(pluggy.transactions(eq("conta-" + itemId), any(), any())).thenReturn(List.of(
                transaction("a-" + itemId, today.minusDays(2), "DEBIT", "-50"),
                transaction("b-" + itemId, today.minusDays(1), "CREDIT", "1200"),
                new Transaction("p-" + itemId, "conta", today + "T00:00:00.000Z", "PENDENTE", "DEBIT",
                        BigDecimal.TEN, null, "BRL", "PENDING")));
    }

    @Test
    void conectaOBancoDeTesteEImportaAsTransacoes() throws Exception {
        connect().andExpect(status().isOk())
                .andExpect(jsonPath("$.connection.institution").value("Pluggy Bank"))
                .andExpect(jsonPath("$.connection.status").value("UPDATED"))
                .andExpect(jsonPath("$.connection.lastSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.imported.format").value("OPEN_FINANCE"))
                .andExpect(jsonPath("$.imported.read").value(2))
                .andExpect(jsonPath("$.imported.added").value(2));
        // A primeira sincronização volta 90 dias.
        verify(pluggy).transactions("conta-" + itemId, today.minusDays(90), today);

        mvc.perform(get("/api/v1/open-finance").with(user))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.connections.length()").value(1));
        mvc.perform(get("/api/v1/days/{date}/summary", today.minusDays(2)).with(user))
                .andExpect(jsonPath("$.spent").value(50));
        mvc.perform(get("/api/v1/imports/transactions").with(user))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void sincronizarDeNovoNaoDuplicaEVoltaUmaSemana() throws Exception {
        String connection = json.readTree(connect().andReturn().getResponse().getContentAsString())
                .at("/connection/id").asText();

        mvc.perform(post("/api/v1/open-finance/connections/{id}/sync", connection).with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported.added").value(0))
                .andExpect(jsonPath("$.imported.duplicates").value(2));
        verify(pluggy).transactions("conta-" + itemId, today.minusDays(7), today);
    }

    @Test
    void lancamentoDoAppCasaComATransacaoDoBanco() throws Exception {
        mvc.perform(post("/api/v1/entries").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"" + UUID.randomUUID() + "\", \"text\": \"Gastei 50 reais na farmácia\", "
                                + "\"occurredAt\": \"" + today.minusDays(2) + "T15:00:00Z\"}"))
                .andExpect(status().isCreated());

        connect().andExpect(jsonPath("$.imported.matched").value(1)).andExpect(jsonPath("$.imported.added").value(1));
        mvc.perform(get("/api/v1/days/{date}/summary", today.minusDays(2)).with(user))
                .andExpect(jsonPath("$.spent").value(50));
    }

    @Test
    void bancoQueNaoESandboxERecusadoEDesfeito() throws Exception {
        when(pluggy.item(itemId)).thenReturn(Optional.of(item("UPDATED", false, userId.toString())));

        connect().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O MyDly só aceita bancos de teste (sandbox). A conexão foi desfeita."));
        verify(pluggy).deleteItem(itemId);
        verify(pluggy, never()).transactions(anyString(), any(), any());
    }

    @Test
    void conexaoCriadaParaOutroUsuarioERecusada() throws Exception {
        when(pluggy.item(itemId)).thenReturn(Optional.of(item("UPDATED", true, UUID.randomUUID().toString())));

        connect().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Esta conexão foi criada para outra conta."));
        verify(pluggy, never()).deleteItem(anyString());
    }

    @Test
    void conexaoJaLigadaAOutraContaEConflito() throws Exception {
        connect().andExpect(status().isOk());
        UUID otherId = accounts.create("of2-" + UUID.randomUUID().toString().substring(0, 8), "x").id();
        when(pluggy.item(itemId)).thenReturn(Optional.of(item("UPDATED", true, null)));

        mvc.perform(post("/api/v1/open-finance/connections").with(TestUsers.as(otherId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\": \"" + itemId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void bancoAindaAtualizandoSoGuardaOStatus() throws Exception {
        when(pluggy.item(itemId)).thenReturn(Optional.of(item("UPDATING", true, userId.toString())));

        connect().andExpect(status().isOk())
                .andExpect(jsonPath("$.connection.status").value("UPDATING"))
                .andExpect(jsonPath("$.imported").doesNotExist());
        verify(pluggy, never()).accounts(anyString());
    }

    @Test
    void desconectarApagaNoPluggyEMantemAsTransacoes() throws Exception {
        String connection = json.readTree(connect().andReturn().getResponse().getContentAsString())
                .at("/connection/id").asText();

        mvc.perform(delete("/api/v1/open-finance/connections/{id}", connection).with(user))
                .andExpect(status().isNoContent());

        verify(pluggy).deleteItem(itemId);
        mvc.perform(get("/api/v1/open-finance").with(user)).andExpect(jsonPath("$.connections.length()").value(0));
        mvc.perform(get("/api/v1/imports/transactions").with(user)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void outroUsuarioNaoSincronizaNemDesconecta() throws Exception {
        String connection = json.readTree(connect().andReturn().getResponse().getContentAsString())
                .at("/connection/id").asText();
        RequestPostProcessor other = TestUsers.as(UUID.randomUUID());

        mvc.perform(post("/api/v1/open-finance/connections/{id}/sync", connection).with(other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/open-finance/connections/{id}", connection).with(other))
                .andExpect(status().isNotFound());
    }

    @Test
    void pluggyForDoArE502() throws Exception {
        when(pluggy.connectToken(userId.toString())).thenThrow(new ResourceAccessException("tempo esgotado"));

        mvc.perform(post("/api/v1/open-finance/connect-token").with(user))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("O Pluggy não respondeu. Tente de novo em instantes."));
    }

    @Test
    void connectTokenLigadoAoUsuario() throws Exception {
        when(pluggy.connectToken(userId.toString())).thenReturn("token-de-teste");

        mvc.perform(post("/api/v1/open-finance/connect-token").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectToken").value("token-de-teste"));
    }

    private ResultActions connect() throws Exception {
        return mvc.perform(post("/api/v1/open-finance/connections").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": \"" + itemId + "\"}"));
    }

    private Item item(String status, boolean sandbox, String clientUserId) {
        return new Item(itemId, status, "SUCCESS", new Connector(2L, "Pluggy Bank", sandbox), clientUserId);
    }

    private static Transaction transaction(String id, LocalDate date, String type, String amount) {
        return new Transaction(id, "conta", date + "T00:00:00.000Z", "LOJA " + id, type, new BigDecimal(amount), null,
                "BRL", "POSTED");
    }
}
