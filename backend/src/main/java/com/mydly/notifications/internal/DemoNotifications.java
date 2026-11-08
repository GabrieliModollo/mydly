package com.mydly.notifications.internal;

import com.mydly.accounts.DemoReset;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A parte de {@code notifications} no reset da demonstração (ADR 0017): apaga os aparelhos, as
 * escolhas e o histórico de envio do usuário de demonstração. Assim o celular de um visitante não
 * recebe os avisos da demonstração no dia seguinte.
 */
@Component
class DemoNotifications {

    private final JdbcClient jdbc;

    DemoNotifications(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void on(DemoReset reset) {
        for (String table : new String[] {"notification_sent", "notification_settings", "notification_device"}) {
            jdbc.sql("DELETE FROM " + table + " WHERE user_id = :userId").param("userId", reset.userId()).update();
        }
    }
}
