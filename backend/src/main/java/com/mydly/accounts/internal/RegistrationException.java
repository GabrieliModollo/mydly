package com.mydly.accounts.internal;

/** Um cadastro recusado, com o motivo que o app mostra para a pessoa (ADR 0018). */
public class RegistrationException extends RuntimeException {

    /** Por que o cadastro foi recusado. */
    public enum Reason {
        INVALID_USERNAME("Use de 3 a 40 letras sem acento, números, ponto, hífen ou sublinhado no usuário."),
        PASSWORD_TOO_SHORT("A senha precisa de pelo menos " + RegistrationService.MIN_PASSWORD_LENGTH
                + " caracteres."),
        PASSWORD_TOO_LONG("A senha é longa demais."),
        USERNAME_TAKEN("Esse usuário já existe. Escolha outro.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public RegistrationException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
