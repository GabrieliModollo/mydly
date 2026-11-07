package com.mydly.integrations;

import com.mydly.entries.Direction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Uma transação lida de uma fonte externa, antes de ser importada.
 *
 * @param externalId  id estável na fonte (o FITID do OFX, ou um hash da linha do CSV)
 * @param date        o dia da transação na fonte
 * @param amount      o valor, sempre positivo
 * @param direction   saída ou entrada
 * @param description o texto do banco ({@code DROGASIL 1234}), ou {@code null}
 */
public record ExternalTransaction(String externalId, LocalDate date, BigDecimal amount, Direction direction,
                                  String description) {

    /** O tamanho guardado da descrição. */
    public static final int MAX_DESCRIPTION = 200;

    public ExternalTransaction {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(direction, "direction");
        if (externalId.isBlank()) {
            throw new IllegalArgumentException("externalId vazio");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("o valor precisa ser positivo");
        }
        if (description != null) {
            description = description.strip().replaceAll("\\s+", " ");
            if (description.isEmpty()) {
                description = null;
            } else if (description.length() > MAX_DESCRIPTION) {
                description = description.substring(0, MAX_DESCRIPTION);
            }
        }
    }
}
