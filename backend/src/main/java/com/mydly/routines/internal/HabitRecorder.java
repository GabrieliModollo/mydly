package com.mydly.routines.internal;

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
 * Registra em {@code habit_log} os itens de rotina aceitos em {@code entries}, já com o hábito do
 * catálogo, e marca como inativos os retirados (ADR 0008). Segue a mesma regra do módulo finance:
 * só grava revisão maior que a da linha, e item retirado ou que deixou de ser rotina vira lápide.
 */
@Component
class HabitRecorder {

    private static final String UPSERT = """
            INSERT INTO habit_log
                (entry_id, item_index, revision, active, habit_code, outcome, quantity, unit, occurred_at, updated_at)
            VALUES (:entryId, :itemIndex, :revision, :active, :habitCode, :outcome, :quantity, :unit, :occurredAt, now())
            ON CONFLICT (entry_id, item_index) DO UPDATE SET
                revision = EXCLUDED.revision,
                active = EXCLUDED.active,
                habit_code = CASE WHEN EXCLUDED.active THEN EXCLUDED.habit_code ELSE habit_log.habit_code END,
                outcome = COALESCE(EXCLUDED.outcome, habit_log.outcome),
                quantity = CASE WHEN EXCLUDED.active THEN EXCLUDED.quantity ELSE habit_log.quantity END,
                unit = CASE WHEN EXCLUDED.active THEN EXCLUDED.unit ELSE habit_log.unit END,
                occurred_at = COALESCE(EXCLUDED.occurred_at, habit_log.occurred_at),
                updated_at = now()
            WHERE habit_log.revision < EXCLUDED.revision
            """;

    private final JdbcClient jdbc;
    private final HabitCatalog catalog;

    HabitRecorder(JdbcClient jdbc, HabitCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void on(EntryItemAccepted event) {
        ParsedEntry item = event.item();
        boolean routine = item.type() == EntryType.ROUTINE;
        String habitCode = routine
                ? catalog.match(item.description(), item.unit()).map(HabitCatalog.Habit::code).orElse(null)
                : null;
        jdbc.sql(UPSERT)
                .param("entryId", event.entryId())
                .param("itemIndex", event.itemIndex())
                .param("revision", event.revision())
                .param("active", routine)
                .param("habitCode", habitCode)
                .param("outcome", routine ? item.outcome().name() : null)
                .param("quantity", routine ? item.quantity() : null)
                .param("unit", routine ? item.unit() : null)
                .param("occurredAt", routine ? Timestamp.from(event.occurredAt()) : null)
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
                .param("habitCode", null)
                .param("outcome", null)
                .param("quantity", null)
                .param("unit", null)
                .param("occurredAt", null)
                .update();
    }
}
