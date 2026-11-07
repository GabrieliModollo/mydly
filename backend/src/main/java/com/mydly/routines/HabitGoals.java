package com.mydly.routines;

import com.mydly.routines.internal.GoalRules;
import com.mydly.routines.internal.HabitCatalog;
import com.mydly.routines.internal.HabitGoalRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do módulo routines: o progresso das metas de hábito (ADR 0010). Os períodos chegam
 * explícitos (o dia ou o início e o fim da semana); quem decide qual é a semana é quem chama, pelas
 * preferências. Assim as regras ficam puras e testáveis.
 */
@Service
public class HabitGoals {

    private final HabitCatalog catalog;
    private final HabitGoalRepository repository;

    HabitGoals(HabitCatalog catalog, HabitGoalRepository repository) {
        this.catalog = catalog;
        this.repository = repository;
    }

    /**
     * O progresso de cada meta diária de quantidade vigente em {@code day}, inclusive as que ainda
     * não tiveram registro no dia, na ordem do catálogo.
     */
    @Transactional(readOnly = true)
    public List<DailyGoalProgress> daily(UUID userId, LocalDate day, ZoneId zone) {
        Map<String, List<HabitGoal>> versions = repository.allVersions(userId, day);
        Map<String, Map<LocalDate, List<GoalRules.DayLog>>> logs = repository.logs(userId, day, day.plusDays(1), zone);
        List<DailyGoalProgress> progress = new ArrayList<>();
        for (HabitCatalog.Habit habit : catalog.all()) {
            GoalRules.goalOn(versions.getOrDefault(habit.code(), List.of()), day)
                    .filter(goal -> goal.kind() == GoalKind.DAILY_QUANTITY)
                    .ifPresent(goal -> progress.add(GoalRules.daily(habit, goal,
                            logs.getOrDefault(habit.code(), Map.of()).getOrDefault(day, List.of()))));
        }
        return progress;
    }

    /**
     * O placar de cada hábito com meta na semana de {@code start} a {@code end}, inclusive. A meta da
     * semana é a vigente em {@code referenceDay}; hábito sem meta nesse dia fica de fora.
     */
    @Transactional(readOnly = true)
    public List<WeekGoalScore> week(UUID userId, LocalDate start, LocalDate end, LocalDate referenceDay,
                                    ZoneId zone) {
        LocalDate until = end.isAfter(referenceDay) ? end : referenceDay;
        Map<String, List<HabitGoal>> versions = repository.allVersions(userId, until);
        Map<String, Map<LocalDate, List<GoalRules.DayLog>>> logs = repository.logs(userId, start, end.plusDays(1),
                zone);
        List<LocalDate> days = start.datesUntil(end.plusDays(1)).toList();
        List<WeekGoalScore> scores = new ArrayList<>();
        for (HabitCatalog.Habit habit : catalog.all()) {
            GoalRules.week(habit, versions.getOrDefault(habit.code(), List.of()), days, referenceDay,
                    logs.getOrDefault(habit.code(), Map.of())).ifPresent(scores::add);
        }
        return scores;
    }
}
