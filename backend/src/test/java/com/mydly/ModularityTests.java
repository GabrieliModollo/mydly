package com.mydly;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Garante as fronteiras do monólito modular (ADR 0002) e gera a documentação
 * dos módulos em {@code target/spring-modulith-docs}.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(MyDlyApplication.class);

    @Test
    void verificaEstruturaModular() {
        modules.verify();
    }

    @Test
    void geraDocumentacaoDosModulos() {
        new Documenter(modules).writeDocumentation();
    }
}
