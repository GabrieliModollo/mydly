package com.mydly.notifications.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Aparelhos, preferências e o registro do que já foi enviado (ADR 0016). */
@Repository
public class NotificationRepository {

    /** O que é lembrado sobre um tipo de notificação já enviado. */
    public enum Kind { BUDGET_ALERT, DAILY_REMINDER, WEEKLY_SUMMARY }

    /** As escolhas de um usuário; sem linha, valem os padrões. */
    public record Settings(boolean budgetAlerts, boolean dailyReminder, boolean weeklySummary, String tone) {

        public static final Settings DEFAULTS = new Settings(true, true, true, "direct");
    }

    private final JdbcClient jdbc;

    NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Liga o aparelho ao usuário. Se o token já era de outro usuário, ele passa para este. */
    public void saveDevice(UUID userId, String token, String platform) {
        jdbc.sql("""
                        INSERT INTO notification_device (token, user_id, platform) VALUES (:token, :userId, :platform)
                        ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform,
                            last_seen_at = now()""")
                .param("token", token)
                .param("userId", userId)
                .param("platform", platform)
                .update();
    }

    /** Tira o aparelho do usuário (saiu da conta ou desligou as notificações). */
    public void removeDevice(UUID userId, String token) {
        jdbc.sql("DELETE FROM notification_device WHERE token = :token AND user_id = :userId")
                .param("token", token)
                .param("userId", userId)
                .update();
    }

    /** Tira um token que o Firebase disse que não vale mais. */
    void removeToken(String token) {
        jdbc.sql("DELETE FROM notification_device WHERE token = :token").param("token", token).update();
    }

    List<String> tokens(UUID userId) {
        return jdbc.sql("SELECT token FROM notification_device WHERE user_id = :userId ORDER BY created_at")
                .param("userId", userId)
                .query(String.class)
                .list();
    }

    /** Os usuários com ao menos um aparelho: só eles recebem alguma coisa. */
    List<UUID> usersWithDevices() {
        return jdbc.sql("SELECT DISTINCT user_id FROM notification_device ORDER BY user_id")
                .query(UUID.class)
                .list();
    }

    public Settings settings(UUID userId) {
        return jdbc.sql("""
                        SELECT budget_alerts, daily_reminder, weekly_summary, tone FROM notification_settings
                        WHERE user_id = :userId""")
                .param("userId", userId)
                .query((rs, n) -> new Settings(rs.getBoolean("budget_alerts"), rs.getBoolean("daily_reminder"),
                        rs.getBoolean("weekly_summary"), rs.getString("tone")))
                .optional()
                .orElse(Settings.DEFAULTS);
    }

    public void saveSettings(UUID userId, Settings settings) {
        jdbc.sql("""
                        INSERT INTO notification_settings (user_id, budget_alerts, daily_reminder, weekly_summary, tone)
                        VALUES (:userId, :budget, :daily, :weekly, :tone)
                        ON CONFLICT (user_id) DO UPDATE SET budget_alerts = EXCLUDED.budget_alerts,
                            daily_reminder = EXCLUDED.daily_reminder, weekly_summary = EXCLUDED.weekly_summary,
                            tone = EXCLUDED.tone, updated_at = now()""")
                .param("userId", userId)
                .param("budget", settings.budgetAlerts())
                .param("daily", settings.dailyReminder())
                .param("weekly", settings.weeklySummary())
                .param("tone", settings.tone())
                .update();
    }

    /** Marca como enviada; {@code false} quando já tinha sido (outra rodada ou outra instância chegou antes). */
    boolean markSent(UUID userId, Kind kind, String key) {
        return jdbc.sql("""
                        INSERT INTO notification_sent (user_id, kind, dedup_key) VALUES (:userId, :kind, :key)
                        ON CONFLICT DO NOTHING""")
                .param("userId", userId)
                .param("kind", kind.name())
                .param("key", key)
                .update() == 1;
    }

    /** Desfaz a marca quando nenhum aparelho recebeu, para a próxima rodada tentar de novo. */
    void unmarkSent(UUID userId, Kind kind, String key) {
        jdbc.sql("DELETE FROM notification_sent WHERE user_id = :userId AND kind = :kind AND dedup_key = :key")
                .param("userId", userId)
                .param("kind", kind.name())
                .param("key", key)
                .update();
    }
}
