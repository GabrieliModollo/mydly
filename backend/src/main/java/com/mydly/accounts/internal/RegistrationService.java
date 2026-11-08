package com.mydly.accounts.internal;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de uma conta nova (ADR 0018): confere o nome de login e a senha, guarda só o hash BCrypt e
 * já devolve o token, igual ao do login, para a pessoa entrar sem digitar tudo de novo. Nada aqui
 * registra em log o nome de login nem a senha.
 */
@Service
public class RegistrationService {

    /** Menor senha aceita. */
    public static final int MIN_PASSWORD_LENGTH = 8;
    /** O BCrypt só usa os primeiros 72 bytes da senha; uma senha maior seria cortada sem aviso. */
    public static final int MAX_PASSWORD_BYTES = 72;
    /** De 3 a 40 letras sem acento, números, ponto, hífen ou sublinhado, começando por letra ou número. */
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,39}");

    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final LoginService logins;

    RegistrationService(AccountRepository accounts, PasswordEncoder passwords, LoginService logins) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.logins = logins;
    }

    /**
     * Cria a conta e devolve o token dela. O nome de login é guardado sem espaços nas pontas e em
     * minúsculas, como o login o procura.
     *
     * @throws RegistrationException com o motivo quando o nome ou a senha não servem
     */
    @Transactional
    public LoginService.Token register(String username, String password) {
        String normalized = AccountRepository.normalize(username);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_USERNAME);
        }
        if (password == null || password.codePointCount(0, password.length()) < MIN_PASSWORD_LENGTH) {
            throw new RegistrationException(RegistrationException.Reason.PASSWORD_TOO_SHORT);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new RegistrationException(RegistrationException.Reason.PASSWORD_TOO_LONG);
        }
        if (accounts.findByUsername(normalized).isPresent()) {
            throw new RegistrationException(RegistrationException.Reason.USERNAME_TAKEN);
        }
        try {
            return logins.issue(accounts.create(normalized, passwords.encode(password)));
        } catch (DuplicateKeyException sameNameAtTheSameTime) {
            // Duas pessoas pediram o mesmo nome ao mesmo tempo: a restrição única do banco decide.
            throw new RegistrationException(RegistrationException.Reason.USERNAME_TAKEN);
        }
    }
}
