package com.mydly.accounts;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * API pública do módulo accounts: o usuário do pedido HTTP atual, tirado do token validado.
 *
 * <p>Só os controladores chamam. Daí para baixo o id vai como parâmetro, porque eventos reenviados e
 * tarefas agendadas rodam sem pedido e sem contexto de segurança.
 */
@Component
public class CurrentUser {

    /**
     * O id do usuário autenticado (o {@code sub} do token).
     *
     * @throws AuthenticationCredentialsNotFoundException sem usuário autenticado, o que vira 401
     */
    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return UUID.fromString(token.getToken().getSubject());
        }
        throw new AuthenticationCredentialsNotFoundException("Faça login para continuar.");
    }
}
