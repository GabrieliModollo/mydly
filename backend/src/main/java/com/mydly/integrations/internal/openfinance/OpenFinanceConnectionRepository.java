package com.mydly.integrations.internal.openfinance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** As conexões de Open Finance ({@code open_finance_connection}), sempre de um usuário. */
@Repository
class OpenFinanceConnectionRepository {

    private static final String COLUMNS = "id, user_id, item_id, institution, status, last_synced_at";

    private final JdbcClient jdbc;

    OpenFinanceConnectionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava a conexão nova; {@code false} quando o item já está ligado a alguma conta. */
    boolean insertIfAbsent(OpenFinanceConnection connection) {
        return jdbc.sql("""
                        INSERT INTO open_finance_connection (id, user_id, item_id, institution, status)
                        VALUES (:id, :userId, :itemId, :institution, :status)
                        ON CONFLICT (item_id) DO NOTHING""")
                .param("id", connection.id())
                .param("userId", connection.userId())
                .param("itemId", connection.itemId())
                .param("institution", connection.institution())
                .param("status", connection.status())
                .update() == 1;
    }

    Optional<OpenFinanceConnection> findByItem(String itemId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM open_finance_connection WHERE item_id = :itemId")
                .param("itemId", itemId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    Optional<OpenFinanceConnection> find(UUID userId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM open_finance_connection WHERE id = :id AND user_id = :userId")
                .param("id", id)
                .param("userId", userId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    List<OpenFinanceConnection> list(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM open_finance_connection WHERE user_id = :userId ORDER BY created_at")
                .param("userId", userId)
                .query((rs, n) -> map(rs))
                .list();
    }

    void updateStatus(UUID id, String status, String institution, Instant syncedAt) {
        jdbc.sql("""
                        UPDATE open_finance_connection
                        SET status = :status, institution = COALESCE(:institution, institution),
                            last_synced_at = COALESCE(:syncedAt, last_synced_at), updated_at = now()
                        WHERE id = :id""")
                .param("status", status)
                .param("institution", institution)
                .param("syncedAt", syncedAt == null ? null : Timestamp.from(syncedAt))
                .param("id", id)
                .update();
    }

    void delete(UUID id) {
        jdbc.sql("DELETE FROM open_finance_connection WHERE id = :id").param("id", id).update();
    }

    private static OpenFinanceConnection map(ResultSet rs) throws SQLException {
        Timestamp synced = rs.getTimestamp("last_synced_at");
        return new OpenFinanceConnection(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("item_id"), rs.getString("institution"), rs.getString("status"),
                synced == null ? null : synced.toInstant());
    }
}
