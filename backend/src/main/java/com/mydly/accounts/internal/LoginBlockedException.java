package com.mydly.accounts.internal;

import java.time.Duration;

/** Senhas erradas demais em pouco tempo: o login fica bloqueado por {@code retryAfter} (ADR 0017). */
public class LoginBlockedException extends RuntimeException {

    private final transient Duration retryAfter;

    LoginBlockedException(Duration retryAfter) {
        super("Muitas tentativas de login.");
        this.retryAfter = retryAfter;
    }

    /** Quanto falta para poder tentar de novo. */
    public Duration retryAfter() {
        return retryAfter;
    }

    /** Os segundos para o header {@code Retry-After}, arredondados para cima e nunca zero. */
    public long retryAfterSeconds() {
        long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
