package com.mydly;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Relógio que o teste acerta quando quiser, para testar a virada do dia (meia-noite em São Paulo)
 * dentro do mesmo contexto Spring.
 */
public final class MutableClock extends Clock {

    private volatile Instant instant;

    public MutableClock(Instant instant) {
        this.instant = instant;
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void set(String instant) {
        set(Instant.parse(instant));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock parent = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId other) {
                return parent.withZone(other);
            }

            @Override
            public Instant instant() {
                return parent.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
