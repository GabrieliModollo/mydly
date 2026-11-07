package com.mydly.routines.internal;

import com.mydly.routines.Measure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Conversão das unidades das metas diárias, sempre em {@link BigDecimal}. As contas são feitas na
 * unidade base de cada dimensão ({@code mL} no volume e {@code min} na duração), em que toda conversão
 * é exata; só a exibição na unidade da meta arredonda.
 */
public final class Quantities {

    /** Unidades aceitas numa meta diária. */
    public static final Set<String> GOAL_UNITS = Set.of("L", "mL", "h", "min");

    private record UnitInfo(Measure measure, BigDecimal toBase) {
    }

    private static final Map<String, UnitInfo> UNITS = Map.of(
            "L", new UnitInfo(Measure.VOLUME, new BigDecimal("1000")),
            "mL", new UnitInfo(Measure.VOLUME, BigDecimal.ONE),
            "h", new UnitInfo(Measure.DURATION, new BigDecimal("60")),
            "min", new UnitInfo(Measure.DURATION, BigDecimal.ONE));

    private Quantities() {
    }

    /** A dimensão de uma unidade, ou {@link Measure#NONE} para as que não entram em meta ({@code km}, {@code copo}). */
    public static Measure measureOf(String unit) {
        UnitInfo info = unit == null ? null : UNITS.get(unit);
        return info == null ? Measure.NONE : info.measure();
    }

    /**
     * A quantidade na unidade base da dimensão, ou vazio quando ela não pode somar numa meta dessa
     * dimensão: sem quantidade, sem unidade (uma contagem) ou com unidade de outra dimensão, como
     * {@code 3 copos} numa meta em litros.
     */
    public static Optional<BigDecimal> toBase(BigDecimal quantity, String unit, Measure measure) {
        if (quantity == null || unit == null || measure == Measure.NONE) {
            return Optional.empty();
        }
        UnitInfo info = UNITS.get(unit);
        if (info == null || info.measure() != measure) {
            return Optional.empty();
        }
        return Optional.of(quantity.multiply(info.toBase()));
    }

    /**
     * Uma quantidade na unidade base convertida para {@code unit}, arredondada para baixo em duas
     * casas: 50 min em horas é 0,83, e 1999 mL em litros é 1,99, nunca 2.
     */
    public static BigDecimal fromBase(BigDecimal base, String unit) {
        return base.divide(UNITS.get(unit).toBase(), 2, RoundingMode.DOWN);
    }
}
