package com.mydly.finance.internal;

import com.mydly.entries.EntryItemAccepted;
import com.mydly.entries.EntryItemRetracted;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Registra em {@code finance_transaction} os itens de finança aceitos em {@code entries} e marca
 * como inativos os retirados (ADR 0008).
 *
 * <p>O consumidor é idempotente pela revisão: a gravação só acontece quando a revisão do evento é
 * maior que a da linha, numa única instrução ({@code ON CONFLICT ... WHERE}), então um evento
 * repetido ou atrasado não muda nada. Um item aceito que não é finança com valor (virou rotina
 * depois de reaberto, por exemplo) também vira lápide.
 *
 * <p>Roda logo depois da transação que publicou o evento, numa transação própria. Se falhar, o
 * registro de publicação do Spring Modulith reenvia o evento quando a aplicação reiniciar.
 */
@Component
class TransactionRecorder {

    private static final String UPSERT = """
            INSERT INTO finance_transaction
                (entry_id, item_index, revision, active, amount, direction, category, occurred_at, updated_at)
            VALUES (:entryId, :itemIndex, :revision, :active, :amount, :direction, :category, :occurredAt, now())
            ON CONFLICT (entry_id, item_index) DO UPDATE SET
                revision = EXCLUDED.revision,
                active = EXCLUDED.active,
                amount = COALESCE(EXCLUDED.amount, finance_transaction.amount),
                direction = COALESCE(EXCLUDED.direction, finance_transaction.direction),
                category = CASE WHEN EXCLUDED.active THEN EXCLUDED.category ELSE finance_transaction.category END,
                occurred_at = COALESCE(EXCLUDED.occurred_at, finance_transaction.occurred_at),
                updated_at = now()
            WHERE finance_transaction.revision < EXCLUDED.revision
            """;

    private final JdbcClient jdbc;

    TransactionRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(EntryItemAccepted event) {
        ParsedEntry item = event.item();
        boolean transaction = item.type() == EntryType.FINANCE && item.amount() != null && item.amount().signum() > 0;
        jdbc.sql(UPSERT)
                .param("entryId", event.entryId())
                .param("itemIndex", event.itemIndex())
                .param("revision", event.revision())
                .param("active", transaction)
                .param("amount", transaction ? item.amount() : null)
                .param("direction", transaction ? item.direction().name() : null)
                .param("category", transaction && item.category() != null ? item.category().name() : null)
                .param("occurredAt", transaction ? Timestamp.from(event.occurredAt()) : null)
                .update();
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(EntryItemRetracted event) {
        jdbc.sql(UPSERT)
                .param("entryId", event.entryId())
                .param("itemIndex", event.itemIndex())
                .param("revision", event.revision())
                .param("active", false)
                .param("amount", null)
                .param("direction", null)
                .param("category", null)
                .param("occurredAt", null)
                .update();
    }
}
