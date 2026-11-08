package com.mydly.integrations.internal;

import com.mydly.accounts.DemoReset;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A parte de {@code integrations} no reset da demonstração (ADR 0017): apaga os extratos importados e
 * a conexão de Open Finance do usuário de demonstração. Não há extrato de exemplo: quem visita importa
 * o seu ou conecta o banco de teste do sandbox.
 */
@Component
class DemoImports {

    private final JdbcClient jdbc;

    DemoImports(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void on(DemoReset reset) {
        jdbc.sql("DELETE FROM external_transaction WHERE user_id = :userId").param("userId", reset.userId()).update();
        jdbc.sql("DELETE FROM open_finance_connection WHERE user_id = :userId").param("userId", reset.userId())
                .update();
    }
}
