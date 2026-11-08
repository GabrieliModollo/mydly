package com.mydly.integrations.internal.openfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mydly.MutableClock;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * O cliente do Pluggy contra um servidor HTTP simulado (ADR 0015): autenticação, connect token,
 * contas paginadas e transações pelo cursor de {@code /v2/transactions}.
 */
class PluggyClientTests {

    private static final String BASE = "https://api.pluggy.test";

    private MockRestServiceServer server;
    private PluggyClient client;
    private MutableClock clock;

    @BeforeEach
    void client() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(java.time.Instant.parse("2026-11-07T12:00:00Z"));
        client = new PluggyClient(builder.baseUrl(BASE).build(),
                new OpenFinanceProperties("id-do-time", "segredo", BASE, Duration.ofSeconds(5), 90), clock);
    }

    private void expectAuth() {
        server.expect(once(), requestTo(BASE + "/auth"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"clientId\": \"id-do-time\", \"clientSecret\": \"segredo\"}"))
                .andRespond(withSuccess("{\"apiKey\": \"chave-1\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void autenticaUmaVezEReaproveitaAChave() {
        expectAuth();
        server.expect(requestTo(BASE + "/items/item-1")).andExpect(header("X-API-KEY", "chave-1"))
                .andRespond(withSuccess("""
                        {"id": "item-1", "status": "UPDATED", "executionStatus": "SUCCESS", "clientUserId": "u1",
                         "connector": {"id": 2, "name": "Pluggy Bank", "isSandbox": true, "outro": 1}, "novo": "x"}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/items/item-2")).andExpect(header("X-API-KEY", "chave-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        PluggyModels.Item item = client.item("item-1").orElseThrow();

        assertThat(item.status()).isEqualTo("UPDATED");
        assertThat(item.connector().isSandbox()).isTrue();
        assertThat(item.connector().name()).isEqualTo("Pluggy Bank");
        assertThat(item.clientUserId()).isEqualTo("u1");
        assertThat(client.item("item-2")).isEmpty();
        server.verify();
    }

    @Test
    void renovaAChaveDepoisDoPrazo() {
        expectAuth();
        server.expect(once(), requestTo(BASE + "/auth"))
                .andRespond(withSuccess("{\"apiKey\": \"chave-2\"}", MediaType.APPLICATION_JSON));

        assertThat(client.apiKey()).isEqualTo("chave-1");
        clock.set(clock.instant().plus(PluggyClient.API_KEY_REUSE).plusSeconds(1));
        assertThat(client.apiKey()).isEqualTo("chave-2");
        server.verify();
    }

    @Test
    void connectTokenLevaOUsuarioDentroDeOptions() {
        expectAuth();
        server.expect(requestTo(BASE + "/connect_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"options\": {\"clientUserId\": \"u1\", \"avoidDuplicates\": true}}"))
                .andRespond(withSuccess("{\"accessToken\": \"token-de-30-minutos\"}", MediaType.APPLICATION_JSON));

        assertThat(client.connectToken("u1")).isEqualTo("token-de-30-minutos");
    }

    @Test
    void contasPaginadas() {
        expectAuth();
        server.expect(requestTo(BASE + "/accounts?itemId=item-1&page=1")).andRespond(withSuccess("""
                {"page": 1, "total": 2, "totalPages": 2, "results": [{"id": "conta-1", "type": "BANK", "name": "Corrente"}]}""",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/accounts?itemId=item-1&page=2")).andRespond(withSuccess("""
                {"page": 2, "total": 2, "totalPages": 2, "results": [{"id": "conta-2", "type": "CREDIT", "name": "Cartão"}]}""",
                MediaType.APPLICATION_JSON));

        assertThat(client.accounts("item-1")).extracting(PluggyModels.Account::id).containsExactly("conta-1", "conta-2");
    }

    @Test
    void transacoesSeguemOCursorSemCodificarDeNovo() {
        expectAuth();
        server.expect(requestTo(BASE + "/v2/transactions?accountId=conta-1&dateFrom=2026-08-09&dateTo=2026-11-07"))
                .andRespond(withSuccess("""
                        {"results": [{"id": "t1", "accountId": "conta-1", "date": "2026-11-05T00:00:00.000Z",
                          "description": "DROGASIL", "type": "DEBIT", "amount": -50, "currencyCode": "BRL",
                          "status": "POSTED"}],
                         "next": "?accountId=conta-1&after=eyJpZCI6InQxIn0%3D"}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URI.create(BASE + "/v2/transactions?accountId=conta-1&after=eyJpZCI6InQxIn0%3D")))
                .andRespond(withSuccess("""
                        {"results": [{"id": "t2", "accountId": "conta-1", "date": "2026-11-06T00:00:00.000Z",
                          "description": "SALARIO", "type": "CREDIT", "amount": 3000, "currencyCode": "BRL"}],
                         "next": null}""", MediaType.APPLICATION_JSON));

        List<PluggyModels.Transaction> transactions = client.transactions("conta-1", LocalDate.of(2026, 8, 9),
                LocalDate.of(2026, 11, 7));

        assertThat(transactions).extracting(PluggyModels.Transaction::id).containsExactly("t1", "t2");
        server.verify();
    }

    @Test
    void apagarItemQueJaNaoExisteNaoEErro() {
        expectAuth();
        server.expect(requestTo(BASE + "/items/item-1")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        client.deleteItem("item-1");
        server.verify();
    }

    @Test
    void credenciaisRecusadas() {
        server.expect(requestTo(BASE + "/auth")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatExceptionOfType(OpenFinanceException.class).isThrownBy(() -> client.connectToken("u1"))
                .withMessage("O Pluggy recusou as credenciais do MyDly.");
    }
}
