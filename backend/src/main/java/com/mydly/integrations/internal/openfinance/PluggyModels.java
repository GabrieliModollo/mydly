package com.mydly.integrations.internal.openfinance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * O que o MyDly lê da API do Pluggy, só os campos usados. Os nomes seguem a API e o tipo
 * {@code pluggy-sdk}; campos desconhecidos são ignorados, para uma versão nova da API não quebrar a leitura.
 */
final class PluggyModels {

    private PluggyModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthResponse(String apiKey) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConnectTokenResponse(String accessToken) {
    }

    /** Um item: a conexão de um usuário com uma instituição. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String id, String status, String executionStatus, Connector connector, String clientUserId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Connector(Long id, String name, Boolean isSandbox) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(String id, String type, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccountPage(List<Account> results, Integer page, Integer totalPages) {
    }

    /**
     * Uma transação. {@code date} vem em ISO 8601 ({@code 2026-11-05T00:00:00.000Z}); {@code type} é
     * {@code DEBIT} ou {@code CREDIT}; {@code status} é {@code PENDING} ou {@code POSTED}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Transaction(String id, String accountId, String date, String description, String type, BigDecimal amount,
                       BigDecimal amountInAccountCurrency, String currencyCode, String status) {
    }

    /** Página de {@code GET /v2/transactions}: {@code next} é a query da próxima página, ou nulo na última. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransactionPage(List<Transaction> results, String next) {
    }
}
