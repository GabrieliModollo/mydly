package com.mydly.integrations.internal;

import com.mydly.entries.Direction;
import com.mydly.finance.TransactionOrigin;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** As transações externas ({@code external_transaction}), sempre de um usuário. */
@Repository
class ImportedTransactionRepository {

    private static final String COLUMNS = """
            id, user_id, source, external_id, occurred_on, amount, direction, description, status, matched_entry_id,
            matched_item_index, revision""";

    private final JdbcClient jdbc;

    ImportedTransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava a transação nova; {@code false} quando ela já tinha sido importada antes. */
    boolean insertIfAbsent(ImportedTransaction transaction) {
        return jdbc.sql("""
                        INSERT INTO external_transaction
                            (id, user_id, source, external_id, occurred_on, amount, direction, description, status,
                             matched_entry_id, matched_item_index, revision)
                        VALUES (:id, :userId, :source, :externalId, :date, :amount, :direction, :description, :status,
                                :entryId, :itemIndex, :revision)
                        ON CONFLICT (user_id, source, external_id) DO NOTHING""")
                .param("id", transaction.id())
                .param("userId", transaction.userId())
                .param("source", transaction.source().name())
                .param("externalId", transaction.externalId())
                .param("date", transaction.date())
                .param("amount", transaction.amount())
                .param("direction", transaction.direction().name())
                .param("description", transaction.description())
                .param("status", transaction.status().name())
                .param("entryId", transaction.matchedEntryId())
                .param("itemIndex", transaction.matchedItemIndex())
                .param("revision", transaction.revision())
                .update() == 1;
    }

    void update(ImportedTransaction transaction) {
        jdbc.sql("""
                        UPDATE external_transaction
                        SET status = :status, matched_entry_id = :entryId, matched_item_index = :itemIndex,
                            revision = :revision, updated_at = now()
                        WHERE id = :id""")
                .param("status", transaction.status().name())
                .param("entryId", transaction.matchedEntryId())
                .param("itemIndex", transaction.matchedItemIndex())
                .param("revision", transaction.revision())
                .param("id", transaction.id())
                .update();
    }

    /** A transação do usuário, travada até o fim da transação do banco para duas mudanças não se cruzarem. */
    Optional<ImportedTransaction> findForUpdate(UUID userId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM external_transaction WHERE id = :id AND user_id = :userId FOR UPDATE")
                .param("id", id)
                .param("userId", userId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    /** A transação ligada a um item de lançamento, travada. */
    Optional<ImportedTransaction> findMatchedTo(UUID entryId, int itemIndex) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM external_transaction
                        WHERE status = 'MATCHED' AND matched_entry_id = :entryId AND matched_item_index = :itemIndex
                        FOR UPDATE""")
                .param("entryId", entryId)
                .param("itemIndex", itemIndex)
                .query((rs, n) -> map(rs))
                .optional();
    }

    /** As transações que contam sozinhas, com esse valor e direção, nos dias pedidos, da mais antiga à mais nova. */
    List<ImportedTransaction> unmatched(UUID userId, Direction direction, BigDecimal amount, LocalDate from,
                                        LocalDate to) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM external_transaction
                        WHERE user_id = :userId AND status = 'IMPORTED' AND direction = :direction AND amount = :amount
                          AND occurred_on BETWEEN :from AND :to
                        ORDER BY occurred_on, imported_at, id
                        FOR UPDATE""")
                .param("userId", userId)
                .param("direction", direction.name())
                .param("amount", amount)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> map(rs))
                .list();
    }

    /** Os itens de lançamento do usuário que já estão ligados a uma transação externa. */
    Set<String> matchedItems(UUID userId) {
        return new HashSet<>(jdbc.sql("""
                        SELECT matched_entry_id || ':' || matched_item_index FROM external_transaction
                        WHERE user_id = :userId AND status = 'MATCHED'""")
                .param("userId", userId)
                .query(String.class)
                .list());
    }

    /** As transações do usuário nos dias pedidos, da mais nova à mais antiga. */
    List<ImportedTransaction> list(UUID userId, LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM external_transaction
                        WHERE user_id = :userId AND occurred_on BETWEEN :from AND :to
                        ORDER BY occurred_on DESC, imported_at DESC, id""")
                .param("userId", userId)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> map(rs))
                .list();
    }

    static String itemKey(UUID entryId, int itemIndex) {
        return entryId + ":" + itemIndex;
    }

    private static ImportedTransaction map(ResultSet rs) throws SQLException {
        int index = rs.getInt("matched_item_index");
        Integer itemIndex = rs.wasNull() ? null : index;
        return new ImportedTransaction(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                TransactionOrigin.valueOf(rs.getString("source")), rs.getString("external_id"),
                rs.getObject("occurred_on", LocalDate.class), rs.getBigDecimal("amount"),
                Direction.valueOf(rs.getString("direction")), rs.getString("description"),
                ImportedTransaction.Status.valueOf(rs.getString("status")), rs.getObject("matched_entry_id", UUID.class),
                itemIndex, rs.getLong("revision"));
    }
}
