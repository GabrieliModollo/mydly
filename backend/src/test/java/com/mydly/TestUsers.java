package com.mydly;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Usuários dos testes. O demo é criado pela migração V11 e é o dono padrão dos pedidos ({@link TestSecurityConfiguration}). */
public final class TestUsers {

    /** O usuário demo da migração. */
    public static final UUID DEMO = UUID.fromString("d3d3d3d3-0000-4000-8000-000000000001");

    private TestUsers() {
    }

    /** O pedido autenticado como o usuário {@code id}, como se tivesse vindo com um token válido dele. */
    public static RequestPostProcessor as(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()));
    }
}
