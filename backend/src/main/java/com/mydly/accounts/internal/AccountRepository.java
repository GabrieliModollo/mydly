package com.mydly.accounts.internal;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Os usuários ({@code app_user}). */
@Repository
public class AccountRepository {

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** O usuário pelo nome de login, sem diferença entre maiúsculas e minúsculas. */
    public Optional<Account> findByUsername(String username) {
        return jdbc.sql("SELECT id, username, password_hash FROM app_user WHERE username = :username")
                .param("username", normalize(username))
                .query((rs, n) -> new Account(rs.getObject("id", UUID.class), rs.getString("username"),
                        rs.getString("password_hash")))
                .optional();
    }

    public Optional<Account> findById(UUID id) {
        return jdbc.sql("SELECT id, username, password_hash FROM app_user WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Account(rs.getObject("id", UUID.class), rs.getString("username"),
                        rs.getString("password_hash")))
                .optional();
    }

    /** Cria um usuário. Ainda não há cadastro pela API; os testes usam para ter um segundo usuário. */
    public Account create(String username, String passwordHash) {
        Account account = new Account(UUID.randomUUID(), normalize(username), passwordHash);
        jdbc.sql("INSERT INTO app_user (id, username, password_hash) VALUES (:id, :username, :hash)")
                .param("id", account.id())
                .param("username", account.username())
                .param("hash", account.passwordHash())
                .update();
        return account;
    }

    static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
