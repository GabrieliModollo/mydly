package com.mydly.preferences;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Uma semana de 7 dias, do primeiro ao último dia, inclusive.
 *
 * @param start o primeiro dia
 * @param end   o último dia, 6 dias depois do primeiro
 */
public record Week(LocalDate start, LocalDate end) {

    public Week {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.equals(start.plusDays(6))) {
            throw new IllegalArgumentException("uma semana tem 7 dias: " + start + " a " + end);
        }
    }

    /** A semana que começa em {@code start}. */
    public static Week startingOn(LocalDate start) {
        return new Week(start, start.plusDays(6));
    }

    /** A semana que contém {@code day} quando ela começa em {@code firstDay}. */
    public static Week containing(LocalDate day, DayOfWeek firstDay) {
        return startingOn(day.with(TemporalAdjusters.previousOrSame(firstDay)));
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(start) && !day.isAfter(end);
    }
}
