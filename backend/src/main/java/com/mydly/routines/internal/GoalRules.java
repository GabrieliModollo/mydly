package com.mydly.routines.internal;

import com.mydly.entries.RoutineOutcome;
import com.mydly.routines.DailyGoalProgress;
import com.mydly.routines.GoalKind;
import com.mydly.routines.HabitGoal;
import com.mydly.routines.HabitStatus;
import com.mydly.routines.Measure;
import com.mydly.routines.Polarity;
import com.mydly.routines.WeekGoalScore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regras das metas de hábito (ADR 0010), em funções puras: qual versão vale num dia, se uma meta
 * combina com o hábito, se um dia cumpriu a meta diária e o placar da semana. Quem carrega os dados
 * do banco é {@link HabitGoalService} e a API pública {@code HabitGoals}.
 */
public final class GoalRules {

    /** Maior quantidade aceita numa meta diária; a coluna guarda até 9 dígitos inteiros. */
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("1000000");

    private GoalRules() {
    }

    /** Um registro ativo do hábito num dia, com a quantidade quando a frase trouxe uma. */
    public record DayLog(RoutineOutcome outcome, BigDecimal quantity, String unit) {
    }

    /**
     * A versão vigente em {@code day}: a de maior {@code validFrom} até esse dia, ou vazio quando o
     * hábito ainda não tinha nenhuma versão. A versão {@link GoalKind#NONE} é devolvida como qualquer
     * outra; quem chama decide que ela quer dizer sem meta.
     */
    public static Optional<HabitGoal> versionOn(List<HabitGoal> versions, LocalDate day) {
        return versions.stream()
                .filter(version -> !version.validFrom().isAfter(day))
                .max(Comparator.comparing(HabitGoal::validFrom));
    }

    /** A meta com efeito em {@code day}: a versão vigente, se ela não for a de remoção. */
    public static Optional<HabitGoal> goalOn(List<HabitGoal> versions, LocalDate day) {
        return versionOn(versions, day).filter(HabitGoal::active);
    }

    /**
     * Confere se a meta combina com o hábito.
     *
     * <ul>
     *   <li>{@link GoalKind#DAILY_QUANTITY}: só em hábito bom com medida, com unidade da mesma dimensão
     *       e quantidade maior que zero com até duas casas.</li>
     *   <li>{@link GoalKind#WEEKLY_FREQUENCY}: de 1 a 7 dias num hábito bom; num hábito ruim é o limite e
     *       vai de 0 a 7.</li>
     * </ul>
     *
     * @throws InvalidGoalException com a mensagem para o usuário
     */
    public static void validate(HabitCatalog.Habit habit, GoalKind kind, BigDecimal targetQuantity, String unit,
                                Integer daysPerWeek) {
        if (kind == null) {
            throw new InvalidGoalException("Informe o tipo da meta: DAILY_QUANTITY ou WEEKLY_FREQUENCY.");
        }
        switch (kind) {
            case NONE -> throw new InvalidGoalException("Para tirar a meta, use DELETE.");
            case DAILY_QUANTITY -> validateDaily(habit, targetQuantity, unit, daysPerWeek);
            case WEEKLY_FREQUENCY -> validateWeekly(habit, targetQuantity, unit, daysPerWeek);
        }
    }

    private static void validateDaily(HabitCatalog.Habit habit, BigDecimal targetQuantity, String unit,
                                      Integer daysPerWeek) {
        if (habit.polarity() == Polarity.BAD) {
            throw new InvalidGoalException(
                    "Hábito ruim aceita só limite de dias por semana (WEEKLY_FREQUENCY), não quantidade por dia.");
        }
        if (habit.measure() == Measure.NONE) {
            throw new InvalidGoalException(habit.name() + " não tem medida; use dias por semana (WEEKLY_FREQUENCY).");
        }
        if (daysPerWeek != null) {
            throw new InvalidGoalException("Meta por dia não leva dias por semana.");
        }
        if (unit == null || !Quantities.GOAL_UNITS.contains(unit)) {
            throw new InvalidGoalException("Unidade da meta: L, mL, h ou min.");
        }
        if (Quantities.measureOf(unit) != habit.measure()) {
            String allowed = habit.measure() == Measure.VOLUME ? "L ou mL" : "h ou min";
            throw new InvalidGoalException("A meta de " + habit.name() + " é medida em " + allowed + ".");
        }
        if (targetQuantity == null || targetQuantity.signum() <= 0 || targetQuantity.compareTo(MAX_QUANTITY) > 0
                || targetQuantity.stripTrailingZeros().scale() > 2) {
            throw new InvalidGoalException("A quantidade precisa ser maior que zero, com até duas casas decimais.");
        }
    }

    private static void validateWeekly(HabitCatalog.Habit habit, BigDecimal targetQuantity, String unit,
                                       Integer daysPerWeek) {
        if (targetQuantity != null || unit != null) {
            throw new InvalidGoalException("Meta de dias por semana não leva quantidade nem unidade.");
        }
        int min = habit.polarity() == Polarity.BAD ? 0 : 1;
        if (daysPerWeek == null || daysPerWeek < min || daysPerWeek > 7) {
            throw new InvalidGoalException(habit.polarity() == Polarity.BAD
                    ? "O limite vai de 0 a 7 dias por semana."
                    : "A meta vai de 1 a 7 dias por semana.");
        }
    }

    /**
     * A soma do dia na unidade base da meta: só registros cumpridos ({@code DONE}) com quantidade na
     * mesma dimensão. Registros sem quantidade, contagens e unidades de outra dimensão não somam.
     */
    public static BigDecimal doneBase(HabitGoal goal, List<DayLog> logs) {
        Measure measure = Quantities.measureOf(goal.unit());
        return logs.stream()
                .filter(log -> log.outcome() == RoutineOutcome.DONE)
                .map(log -> Quantities.toBase(log.quantity(), log.unit(), measure))
                .flatMap(Optional::stream)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** O dia cumpriu a meta diária: a soma convertida chega ao alvo, em comparação exata. */
    public static boolean dayMet(HabitGoal goal, List<DayLog> logs) {
        BigDecimal target = Quantities.toBase(goal.targetQuantity(), goal.unit(), Quantities.measureOf(goal.unit()))
                .orElseThrow();
        return doneBase(goal, logs).compareTo(target) >= 0;
    }

    /** O progresso de uma meta diária num dia, como 1,5 de 2 L. */
    public static DailyGoalProgress daily(HabitCatalog.Habit habit, HabitGoal goal, List<DayLog> logs) {
        return new DailyGoalProgress(habit.code(), habit.name(), goal.targetQuantity(), goal.unit(),
                Quantities.fromBase(doneBase(goal, logs), goal.unit()), dayMet(goal, logs));
    }

    /**
     * Como o hábito ficou num dia, com a polaridade: um bom é cumprido se algum registro cumpriu, e um
     * ruim é deslize se algum registro o consumiu. Vazio quando o dia não teve registro.
     */
    public static Optional<HabitStatus> dayStatus(Polarity polarity, List<DayLog> logs) {
        if (logs.isEmpty()) {
            return Optional.empty();
        }
        boolean happened = logs.stream().anyMatch(log -> log.outcome() == RoutineOutcome.DONE);
        return Optional.of(HabitStatus.of(polarity, happened ? RoutineOutcome.DONE : RoutineOutcome.MISSED));
    }

    /**
     * O placar da semana de um hábito, ou vazio quando a semana não tem meta. O tipo da meta é o da
     * versão vigente no dia de referência (o último dia da semana, ou hoje se ela está em curso).
     * Numa meta diária, cada dia é avaliado pela versão vigente naquele dia.
     *
     * @param days         os dias da semana, do primeiro ao último
     * @param referenceDay o dia que decide a meta da semana
     * @param logs         os registros ativos de cada dia; dia ausente é dia sem registro
     */
    public static Optional<WeekGoalScore> week(HabitCatalog.Habit habit, List<HabitGoal> versions,
                                               List<LocalDate> days, LocalDate referenceDay,
                                               Map<LocalDate, List<DayLog>> logs) {
        Optional<HabitGoal> reference = goalOn(versions, referenceDay);
        if (reference.isEmpty()) {
            return Optional.empty();
        }
        HabitGoal goal = reference.get();
        if (goal.kind() == GoalKind.WEEKLY_FREQUENCY) {
            int achieved = 0;
            int slips = 0;
            for (LocalDate day : days) {
                Optional<HabitStatus> status = dayStatus(habit.polarity(), logs.getOrDefault(day, List.of()));
                if (status.filter(HabitStatus.ACHIEVED::equals).isPresent()) {
                    achieved++;
                }
                if (status.filter(HabitStatus.SLIPPED::equals).isPresent()) {
                    slips++;
                }
            }
            return Optional.of(habit.polarity() == Polarity.BAD
                    ? new WeekGoalScore(habit.code(), habit.name(), habit.polarity(), goal.kind(), achieved, null,
                            goal.daysPerWeek(), slips)
                    : new WeekGoalScore(habit.code(), habit.name(), habit.polarity(), goal.kind(), achieved,
                            goal.daysPerWeek(), null, null));
        }
        int target = 0;
        int achieved = 0;
        for (LocalDate day : days) {
            Optional<HabitGoal> onDay = goalOn(versions, day).filter(version -> version.kind() == GoalKind.DAILY_QUANTITY);
            if (onDay.isPresent()) {
                target++;
                if (dayMet(onDay.get(), logs.getOrDefault(day, List.of()))) {
                    achieved++;
                }
            }
        }
        return Optional.of(new WeekGoalScore(habit.code(), habit.name(), habit.polarity(), goal.kind(), achieved,
                target, null, null));
    }
}
