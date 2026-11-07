package com.mydly;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Volta metas, orçamentos e preferências ao estado da migração, para cada teste de integração
 * começar do mesmo ponto. Só os testes mexem direto nas tabelas.
 */
public final class GoalsTestData {

    private GoalsTestData() {
    }

    public static void reset(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM habit_goal").update();
        jdbc.sql("DELETE FROM budget").update();
        jdbc.sql("UPDATE preferences SET first_day_of_week = 'MONDAY' WHERE id = 1").update();
    }
}
