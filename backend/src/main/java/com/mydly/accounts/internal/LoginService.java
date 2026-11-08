package com.mydly.accounts.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confere usuário e senha e emite o token de acesso (ADR 0012). Por privacidade e segurança, nada
 * aqui registra em log o nome de login nem a senha.
 */
@Service
public class LoginService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;
    /**
     * Hash de uma senha qualquer, conferido quando o usuário não existe: a resposta demora o mesmo
     * tanto com usuário inexistente e com senha errada, e o tempo não revela quem tem conta.
     */
    private final String unknownUserHash;

    LoginService(AccountRepository accounts, PasswordEncoder passwords, JwtEncoder encoder, AuthProperties properties,
                 Clock clock) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.unknownUserHash = passwords.encode(UUID.randomUUID().toString());
    }

    /** Um token emitido: o valor para o header {@code Authorization} e até quando vale. */
    public record Token(String value, Instant expiresAt, Account account) {
    }

    /** O token do usuário, ou vazio quando o usuário não existe ou a senha não confere. */
    @Transactional(readOnly = true)
    public Optional<Token> login(String username, String password) {
        Optional<Account> account = accounts.findByUsername(username);
        String hash = account.map(Account::passwordHash).orElse(unknownUserHash);
        boolean matches = password != null && passwords.matches(password, hash);
        return account.filter(found -> matches).map(this::issue);
    }

    @Transactional(readOnly = true)
    public Optional<Account> find(UUID id) {
        return accounts.findById(id);
    }

    /** Emite o token de uma conta; o cadastro usa para a pessoa já entrar. */
    Token issue(Account account) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.tokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(AuthProperties.ISSUER)
                .subject(account.id().toString())
                .claim("preferred_username", account.username())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Token(value, expiresAt, account);
    }
}
