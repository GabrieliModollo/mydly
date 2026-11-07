package com.mydly.preferences;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * API pública do módulo preferences: o calendário do usuário. Todo {@code hoje} do sistema (a data
 * de uma meta nova e a semana atual) sai daqui, sempre no fuso do usuário e a partir do relógio
 * injetável, para os testes controlarem o tempo. Nunca use {@code LocalDate.now()} sem fuso.
 */
@Service
public class UserCalendar {

    /** Fuso do usuário até existir fuso por usuário; o mesmo da listagem de lançamentos. */
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;

    UserCalendar(Clock clock) {
        this.clock = clock;
    }

    public ZoneId zone() {
        return ZONE;
    }

    /** O dia de hoje no fuso do usuário. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
