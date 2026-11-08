package com.mydly.preferences.internal;

import com.mydly.accounts.DemoReset;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** A parte de {@code preferences} no reset da demonstração (ADR 0017): a semana volta a começar na segunda. */
@Component
class DemoPreferences {

    private final JdbcClient jdbc;

    DemoPreferences(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void on(DemoReset reset) {
        jdbc.sql("DELETE FROM preferences WHERE user_id = :userId").param("userId", reset.userId()).update();
    }
}
