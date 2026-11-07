package com.mydly.routines.internal;

import com.mydly.preferences.UserCalendar;
import com.mydly.routines.GoalKind;
import com.mydly.routines.HabitGoal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura e gravação das metas de hábito (ADR 0010). Mudar a meta grava uma versão nova a partir de
 * hoje, no fuso do usuário; remover grava a versão sem meta. Duas gravações no mesmo dia ficam numa
 * versão só, a última, pelo upsert da chave.
 */
@Service
public class HabitGoalService {

    private final HabitCatalog catalog;
    private final HabitGoalRepository repository;
    private final UserCalendar calendar;

    HabitGoalService(HabitCatalog catalog, HabitGoalRepository repository, UserCalendar calendar) {
        this.catalog = catalog;
        this.repository = repository;
        this.calendar = calendar;
    }

    /** Um hábito do catálogo e a meta com efeito hoje, se houver. */
    public record CurrentGoal(HabitCatalog.Habit habit, Optional<HabitGoal> goal) {
    }

    /** Todos os hábitos do catálogo com a meta de hoje. */
    @Transactional(readOnly = true)
    public List<CurrentGoal> all() {
        LocalDate today = calendar.today();
        Map<String, List<HabitGoal>> versions = repository.allVersions(today);
        return catalog.all().stream()
                .map(habit -> new CurrentGoal(habit,
                        GoalRules.goalOn(versions.getOrDefault(habit.code(), List.of()), today)))
                .toList();
    }

    /** O hábito e a meta de hoje, ou vazio quando o código não é do catálogo. */
    @Transactional(readOnly = true)
    public Optional<CurrentGoal> find(String habitCode) {
        LocalDate today = calendar.today();
        return catalog.find(habitCode).map(habit -> new CurrentGoal(habit,
                GoalRules.goalOn(repository.versions(habit.code(), today), today)));
    }

    /**
     * Define a meta a partir de hoje. Uma meta igual à que já vale não grava versão nova.
     *
     * @return vazio quando o código não é do catálogo
     * @throws InvalidGoalException quando a meta não combina com o hábito
     */
    @Transactional
    public Optional<CurrentGoal> set(String habitCode, GoalKind kind, BigDecimal targetQuantity, String unit,
                                     Integer daysPerWeek) {
        Optional<HabitCatalog.Habit> found = catalog.find(habitCode);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        HabitCatalog.Habit habit = found.get();
        GoalRules.validate(habit, kind, targetQuantity, unit, daysPerWeek);
        LocalDate today = calendar.today();
        HabitGoal goal = new HabitGoal(habit.code(), today, kind, targetQuantity, unit, daysPerWeek);
        Optional<HabitGoal> current = GoalRules.goalOn(repository.versions(habit.code(), today), today);
        if (current.isEmpty() || !sameGoal(current.get(), goal)) {
            repository.upsert(goal);
        }
        return find(habit.code());
    }

    /**
     * Tira a meta a partir de hoje, gravando a versão sem meta. Sem meta vigente, não faz nada.
     *
     * @return {@code false} quando o código não é do catálogo
     */
    @Transactional
    public boolean remove(String habitCode) {
        Optional<HabitCatalog.Habit> found = catalog.find(habitCode);
        if (found.isEmpty()) {
            return false;
        }
        LocalDate today = calendar.today();
        if (GoalRules.goalOn(repository.versions(habitCode, today), today).isPresent()) {
            repository.upsert(new HabitGoal(habitCode, today, GoalKind.NONE, null, null, null));
        }
        return true;
    }

    private static boolean sameGoal(HabitGoal current, HabitGoal wanted) {
        return current.kind() == wanted.kind()
                && Objects.equals(current.unit(), wanted.unit())
                && Objects.equals(current.daysPerWeek(), wanted.daysPerWeek())
                && (current.targetQuantity() == null
                        ? wanted.targetQuantity() == null
                        : wanted.targetQuantity() != null && current.targetQuantity().compareTo(wanted.targetQuantity()) == 0);
    }
}
