package com.mydly.accounts.internal;

import java.util.UUID;

/**
 * Um usuário. O hash da senha (BCrypt) nunca sai deste módulo.
 *
 * @param id           o id que vai no token e na coluna {@code user_id} dos dados
 * @param username     o nome de login, em minúsculas
 * @param passwordHash o hash BCrypt da senha
 */
public record Account(UUID id, String username, String passwordHash) {

    @Override
    public String toString() {
        // O hash não aparece em log nem em mensagem de erro.
        return "Account[id=" + id + "]";
    }
}
