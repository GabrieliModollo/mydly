package com.mydly.insights.web;

import com.mydly.finance.BudgetStatus;
import com.mydly.finance.BudgetWindow;
import com.mydly.finance.Budgets;
import com.mydly.finance.FinanceTotals;
import com.mydly.finance.PeriodTotals;
import com.mydly.preferences.UserCalendar;
import com.mydly.preferences.WeekPreferences;
import com.mydly.routines.DayHabits;
import com.mydly.routines.HabitGoals;
import com.mydly.routines.HabitOutcomes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resumo de um dia para os cartões da tela Hoje. Só lê as APIs públicas de {@code finance},
 * {@code routines} e {@code preferences}; não toca em tabela de nenhum módulo.
 *
 * <p>É o único lugar da API com o alerta de orçamento: a tela Hoje já pede este resumo depois de cada
 * lançamento, então o alerta aparece logo depois do gasto que o provocou, sem outra chamada.
 */
@RestController
@RequestMapping("/api/v1/days")
@Tag(name = "Resumo do dia")
class DaySummaryController {

    /** Fuso do dia até existir fuso por usuário; o mesmo da listagem de lançamentos. */
    static final ZoneId USER_ZONE = UserCalendar.ZONE;

    private final FinanceTotals finance;
    private final Budgets budgets;
    private final HabitOutcomes habits;
    private final HabitGoals goals;
    private final WeekPreferences weeks;

    DaySummaryController(FinanceTotals finance, Budgets budgets, HabitOutcomes habits, HabitGoals goals,
                         WeekPreferences weeks) {
        this.finance = finance;
        this.budgets = budgets;
        this.habits = habits;
        this.goals = goals;
        this.weeks = weeks;
    }

    @GetMapping("/{date}/summary")
    @Operation(summary = "Resumo do dia",
            description = "Gasto, entrada, hábitos cumpridos (bons cumpridos mais ruins evitados) e deslizes do "
                    + "dia no fuso America/Sao_Paulo. Itens retirados com Não foi isso não contam. Traz também o "
                    + "progresso das metas diárias de quantidade vigentes no dia e os alertas das categorias que "
                    + "chegaram a 80% do limite no período que contém o dia (a semana das preferências ou o mês civil).")
    DaySummaryResponse summary(
            @Parameter(description = "Dia no formato AAAA-MM-DD", example = "2026-11-06")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PeriodTotals totals = finance.on(date, USER_ZONE);
        DayHabits day = habits.on(date, USER_ZONE);
        BudgetWindow window = BudgetWindow.wholePeriods(date, weeks.weekContaining(date).start());
        return DaySummaryResponse.of(date, totals, day, goals.daily(date, USER_ZONE),
                budgets.progress(window, USER_ZONE).stream()
                        .filter(progress -> progress.status() != BudgetStatus.OK)
                        .toList());
    }
}
