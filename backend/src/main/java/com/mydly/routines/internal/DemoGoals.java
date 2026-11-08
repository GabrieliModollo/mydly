package com.mydly.routines.internal;

import com.mydly.accounts.DemoReset;
import com.mydly.routines.GoalKind;
import com.mydly.routines.HabitGoal;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A parte de {@code routines} no reset da demonstração (ADR 0017): apaga os hábitos registrados e as
 * metas do usuário de demonstração e grava as metas de exemplo. Os hábitos voltam pelos eventos dos
 * lançamentos de exemplo, depois do commit.
 */
@Component
class DemoGoals {

    private final JdbcClient jdbc;
    private final HabitGoalRepository goals;

    DemoGoals(JdbcClient jdbc, HabitGoalRepository goals) {
        this.jdbc = jdbc;
        this.goals = goals;
    }

    @EventListener
    void on(DemoReset reset) {
        jdbc.sql("DELETE FROM habit_log WHERE user_id = :userId").param("userId", reset.userId()).update();
        jdbc.sql("DELETE FROM habit_goal WHERE user_id = :userId").param("userId", reset.userId()).update();
        // As metas valem desde antes das três semanas de exemplo, para todos os dias delas terem meta.
        LocalDate validFrom = reset.today().minusDays(60);
        goals.upsert(reset.userId(), new HabitGoal("HYDRATION", validFrom, GoalKind.DAILY_QUANTITY,
                new BigDecimal("2"), "L", null));
        goals.upsert(reset.userId(), new HabitGoal("GYM", validFrom, GoalKind.WEEKLY_FREQUENCY, null, null, 4));
        goals.upsert(reset.userId(), new HabitGoal("DELIVERY", validFrom, GoalKind.WEEKLY_FREQUENCY, null, null, 2));
    }
}
