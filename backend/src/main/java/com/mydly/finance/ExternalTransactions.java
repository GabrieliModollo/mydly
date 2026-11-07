package com.mydly.finance;

import com.mydly.entries.Direction;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do módulo finance para as transações que vêm de fora do app (ADR 0014): grava as que
 * devem contar nos números e mostra os lançamentos manuais com que uma delas pode casar.
 *
 * <p>Quem chama é o módulo {@code integrations}, que decide a reconciliação. Uma transação externa
 * vira uma linha de {@code finance_transaction} com o id dela em {@code entry_id} e {@code item_index}
 * zero, com a mesma regra de revisão e lápide dos lançamentos (ADR 0008): só uma revisão maior muda a
 * linha, e deixar de contar é {@code active = false}, nunca {@code DELETE}.
 */
@Service
public class ExternalTransactions {

    private static final String UPSERT = """
            INSERT INTO finance_transaction
                (entry_id, item_index, user_id, revision, active, amount, direction, category, occurred_at, source,
                 updated_at)
            VALUES (:id, 0, :userId, :revision, :active, :amount, :direction, NULL, :occurredAt, :source, now())
            ON CONFLICT (entry_id, item_index) DO UPDATE SET
                revision = EXCLUDED.revision,
                active = EXCLUDED.active,
                updated_at = now()
            WHERE finance_transaction.revision < EXCLUDED.revision
              AND finance_transaction.user_id = EXCLUDED.user_id
            """;

    private final JdbcClient jdbc;

    ExternalTransactions(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Uma transação externa como ela deve ficar nos números. */
    public record Change(UUID userId, UUID id, long revision, boolean counts, BigDecimal amount, Direction direction,
                         Instant occurredAt, TransactionOrigin origin) {

        public Change {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (origin == TransactionOrigin.MANUAL) {
                throw new IllegalArgumentException("transação externa não pode ser MANUAL");
            }
        }
    }

    /**
     * Faz a transação contar ou deixar de contar. Roda na transação de quem chama: a mudança no
     * {@code integrations} e nos números acontecem juntas.
     */
    @Transactional
    public void apply(Change change) {
        jdbc.sql(UPSERT)
                .param("id", change.id())
                .param("userId", change.userId())
                .param("revision", change.revision())
                .param("active", change.counts())
                .param("amount", change.amount())
                .param("direction", change.direction().name())
                .param("occurredAt", Timestamp.from(change.occurredAt()))
                .param("source", change.origin().name())
                .update();
    }

    /** Um item de lançamento manual que conta nos números. */
    public record ManualTransaction(UUID entryId, int itemIndex, Instant occurredAt) {
    }

    /**
     * Os lançamentos manuais do usuário com esse valor e direção, com {@code from <= occurredAt < to},
     * do mais antigo ao mais novo.
     */
    @Transactional(readOnly = true)
    public List<ManualTransaction> manual(UUID userId, Direction direction, BigDecimal amount, Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT entry_id, item_index, occurred_at FROM finance_transaction
                        WHERE user_id = :userId AND source = 'MANUAL' AND active AND direction = :direction
                          AND amount = :amount AND occurred_at >= :from AND occurred_at < :to
                        ORDER BY occurred_at, entry_id, item_index""")
                .param("userId", userId)
                .param("direction", direction.name())
                .param("amount", amount)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, n) -> new ManualTransaction(rs.getObject("entry_id", UUID.class), rs.getInt("item_index"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }
}
