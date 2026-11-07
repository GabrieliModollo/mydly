package com.mydly;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Apaga metas, orçamentos e preferências de todos os usuários, para cada teste de integração começar do
 * mesmo ponto: sem metas, sem limites e com a semana começando na segunda (o padrão de quem não
 * escolheu). Só os testes mexem direto nas tabelas.
 */
public final class GoalsTestData {

    private GoalsTestData() {
    }

    public static void reset(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM habit_goal").update();
        jdbc.sql("DELETE FROM budget").update();
        jdbc.sql("DELETE FROM preferences").update();
    }
}
