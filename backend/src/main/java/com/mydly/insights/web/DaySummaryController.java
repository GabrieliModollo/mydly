package com.mydly.insights.web;

import com.mydly.finance.FinanceTotals;
import com.mydly.finance.PeriodTotals;
import com.mydly.routines.DayHabits;
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
 * Resumo de um dia para os cartões da tela Hoje. Só lê as APIs públicas de {@code finance} e
 * {@code routines}; não toca em tabela de nenhum módulo.
 */
@RestController
@RequestMapping("/api/v1/days")
@Tag(name = "Resumo do dia")
class DaySummaryController {

    /** Fuso do dia até existir fuso por usuário; o mesmo da listagem de lançamentos. */
    static final ZoneId USER_ZONE = ZoneId.of("America/Sao_Paulo");

    private final FinanceTotals finance;
    private final HabitOutcomes habits;

    DaySummaryController(FinanceTotals finance, HabitOutcomes habits) {
        this.finance = finance;
        this.habits = habits;
    }

    @GetMapping("/{date}/summary")
    @Operation(summary = "Resumo do dia",
            description = "Gasto, entrada, hábitos cumpridos (bons cumpridos mais ruins evitados) e deslizes do "
                    + "dia no fuso America/Sao_Paulo. Itens retirados com Não foi isso não contam.")
    DaySummaryResponse summary(
            @Parameter(description = "Dia no formato AAAA-MM-DD", example = "2026-11-06")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PeriodTotals totals = finance.on(date, USER_ZONE);
        DayHabits day = habits.on(date, USER_ZONE);
        return DaySummaryResponse.of(date, totals, day);
    }
}
