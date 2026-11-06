package com.mydly;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.EntryParser;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Sobe a aplicação inteira contra um PostgreSQL real (Testcontainers),
 * validando contexto Spring e migrações do Flyway.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyDlyApplicationTests {

    @Autowired
    private EntryParser entryParser;

    @Test
    void contextoCarrega() {
    }

    @Test
    void parserDeLancamentosEstaRegistradoComoBean() {
        assertThat(entryParser.parse("50 reais")).singleElement()
                .extracting(ParsedEntry::type).isEqualTo(EntryType.FINANCE);
    }
}
