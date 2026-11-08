package com.mydly.integrations.internal.openfinance;

import com.mydly.integrations.internal.openfinance.PluggyModels.Account;
import com.mydly.integrations.internal.openfinance.PluggyModels.AccountPage;
import com.mydly.integrations.internal.openfinance.PluggyModels.AuthResponse;
import com.mydly.integrations.internal.openfinance.PluggyModels.ConnectTokenResponse;
import com.mydly.integrations.internal.openfinance.PluggyModels.Item;
import com.mydly.integrations.internal.openfinance.PluggyModels.Transaction;
import com.mydly.integrations.internal.openfinance.PluggyModels.TransactionPage;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente da API do Pluggy (ADR 0015). Só o servidor fala com o Pluggy: o {@code CLIENT_SECRET}
 * nunca vai para o app, que recebe só o connect token de 30 minutos.
 *
 * <p>A chave de API ({@code POST /auth}) vale 2 horas e é reaproveitada por 100 minutos. Por
 * privacidade, nada aqui registra em log descrições, valores ou ids de conta.
 */
class PluggyClient {

    /** A chave vale 2 horas; renovar antes evita uma chamada falhar no meio. */
    static final Duration API_KEY_REUSE = Duration.ofMinutes(100);

    /** Limite de páginas por conta numa sincronização, para uma resposta inesperada não virar laço sem fim. */
    static final int MAX_PAGES = 50;

    private final RestClient http;
    private final OpenFinanceProperties properties;
    private final Clock clock;

    private String apiKey;
    private Instant apiKeyUntil = Instant.MIN;

    /**
     * @param http um cliente com a URL base do Pluggy e os prazos ({@link #http}); nos testes, ligado a
     *             um servidor simulado
     */
    PluggyClient(RestClient http, OpenFinanceProperties properties, Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    /** O cliente HTTP de verdade: a URL base do Pluggy e o prazo de conexão e de leitura. */
    static RestClient http(RestClient.Builder builder, OpenFinanceProperties properties) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requests.setReadTimeout(properties.timeout());
        return builder.baseUrl(properties.baseUrl()).requestFactory(requests).build();
    }

    /** Um connect token para o widget, ligado ao usuário do MyDly ({@code clientUserId}). */
    String connectToken(String clientUserId) {
        ConnectTokenResponse response = http.post().uri("/connect_token")
                .header("X-API-KEY", apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("options", Map.of("clientUserId", clientUserId, "avoidDuplicates", true)))
                .retrieve()
                .body(ConnectTokenResponse.class);
        return required(response == null ? null : response.accessToken(), "connect token");
    }

    /** O item, ou vazio quando ele não existe (ou é de outro time). */
    Optional<Item> item(String itemId) {
        try {
            return Optional.ofNullable(http.get().uri("/items/{id}", itemId)
                    .header("X-API-KEY", apiKey())
                    .retrieve()
                    .body(Item.class));
        } catch (HttpClientErrorException.NotFound missing) {
            return Optional.empty();
        }
    }

    /** Apaga o item no Pluggy; um item que já não existe conta como apagado. */
    void deleteItem(String itemId) {
        http.delete().uri("/items/{id}", itemId)
                .header("X-API-KEY", apiKey())
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> { })
                .toBodilessEntity();
    }

    List<Account> accounts(String itemId) {
        List<Account> accounts = new ArrayList<>();
        int page = 1;
        int totalPages = 1;
        while (page <= totalPages && page <= MAX_PAGES) {
            int current = page;
            AccountPage response = http.get()
                    .uri(uri -> uri.path("/accounts").queryParam("itemId", itemId).queryParam("page", current).build())
                    .header("X-API-KEY", apiKey())
                    .retrieve()
                    .body(AccountPage.class);
            if (response == null || response.results() == null) {
                break;
            }
            accounts.addAll(response.results());
            totalPages = response.totalPages() == null ? 1 : response.totalPages();
            page++;
        }
        return accounts;
    }

    /**
     * As transações da conta de {@code from} até {@code to}, seguindo o cursor de {@code /v2/transactions}:
     * o {@code next} é anexado ao caminho como veio, sem montar nem decodificar o cursor.
     */
    List<Transaction> transactions(String accountId, LocalDate from, LocalDate to) {
        List<Transaction> transactions = new ArrayList<>();
        TransactionPage response = http.get()
                .uri(uri -> uri.path("/v2/transactions").queryParam("accountId", accountId)
                        .queryParam("dateFrom", from).queryParam("dateTo", to).build())
                .header("X-API-KEY", apiKey())
                .retrieve()
                .body(TransactionPage.class);
        int pages = 1;
        while (response != null) {
            if (response.results() != null) {
                transactions.addAll(response.results());
            }
            String next = response.next();
            if (next == null || next.isBlank() || pages >= MAX_PAGES) {
                break;
            }
            pages++;
            // URI pronto, e não um texto de modelo: o cursor já vem codificado e não pode ser codificado de novo.
            response = http.get().uri(URI.create(properties.baseUrl() + "/v2/transactions" + next))
                    .header("X-API-KEY", apiKey())
                    .retrieve()
                    .body(TransactionPage.class);
        }
        return transactions;
    }

    /** A chave de API do time, renovada quando passa do prazo. */
    synchronized String apiKey() {
        Instant now = clock.instant();
        if (apiKey == null || !now.isBefore(apiKeyUntil)) {
            AuthResponse response = http.post().uri("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("clientId", properties.clientId(), "clientSecret", properties.clientSecret()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, failure) -> {
                        throw new OpenFinanceException("O Pluggy recusou as credenciais do MyDly.");
                    })
                    .body(AuthResponse.class);
            apiKey = required(response == null ? null : response.apiKey(), "api key");
            apiKeyUntil = now.plus(API_KEY_REUSE);
        }
        return apiKey;
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new OpenFinanceException("O Pluggy respondeu sem " + what + ".");
        }
        return value;
    }
}
