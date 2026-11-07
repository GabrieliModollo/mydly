package com.mydly.insights.internal;

import java.util.Locale;
import java.util.Optional;

/**
 * Tom das mensagens da semana, escolhido pelo usuário no app. Em nenhum tom a mensagem fala de corpo,
 * peso, aparência ou quantidade de comida: só de comportamento.
 */
public enum Tone {
    GENTLE,
    DIRECT,
    SCOLD;

    /** O tom pelo nome usado na API ({@code gentle}, {@code direct} ou {@code scold}). */
    public static Optional<Tone> fromParameter(String value) {
        if (value == null) {
            return Optional.of(DIRECT);
        }
        for (Tone tone : values()) {
            if (tone.parameter().equals(value.toLowerCase(Locale.ROOT))) {
                return Optional.of(tone);
            }
        }
        return Optional.empty();
    }

    public String parameter() {
        return name().toLowerCase(Locale.ROOT);
    }
}
