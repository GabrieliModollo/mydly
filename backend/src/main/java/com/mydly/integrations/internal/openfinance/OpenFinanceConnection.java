package com.mydly.integrations.internal.openfinance;

import java.time.Instant;
import java.util.UUID;

/**
 * Um banco conectado pelo Pluggy ({@code open_finance_connection}).
 *
 * @param itemId       o id do item no Pluggy
 * @param institution  o nome da instituição, para a tela
 * @param status       o status do item na última leitura ({@code UPDATED}, {@code UPDATING}, {@code LOGIN_ERROR}...)
 * @param lastSyncedAt quando as transações foram importadas pela última vez, ou nulo
 */
public record OpenFinanceConnection(UUID id, UUID userId, String itemId, String institution, String status,
                                    Instant lastSyncedAt) {

    /** Só um item atualizado tem transações prontas para ler. */
    boolean ready() {
        return "UPDATED".equals(status);
    }
}
