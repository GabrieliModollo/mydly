package com.mydly;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Sobe a aplicação inteira contra um PostgreSQL real (Testcontainers),
 * validando contexto Spring e migrações do Flyway.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyDlyApplicationTests {

    @Test
    void contextoCarrega() {
    }
}
