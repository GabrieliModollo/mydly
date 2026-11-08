package com.mydly.accounts;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Os dados do usuário de demonstração voltam ao exemplo (ADR 0017). Cada módulo escuta este evento
 * de forma síncrona, na mesma transação, apaga os dados dele desse usuário e, quando tem, grava a sua
 * parte da semana de exemplo. Nenhum módulo mexe nas tabelas de outro.
 *
 * <p>Os eventos que a semana de exemplo gera (os lançamentos aceitos) são entregues depois do commit,
 * então chegam a {@code finance} e {@code routines} já com as tabelas limpas.
 *
 * @param userId o usuário de demonstração
 * @param today  o dia de hoje no fuso do usuário, a partir do qual a semana de exemplo é montada
 */
public record DemoReset(UUID userId, LocalDate today) {

    public DemoReset {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(today, "today");
    }
}
