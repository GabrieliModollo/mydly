package com.mydly.insights.web;

import com.mydly.insights.internal.Tone;
import com.mydly.insights.internal.WeekSummaries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resumo da semana para a tela Resumo: hábitos dia a dia, gastos por categoria, comparação com a
 * semana anterior e as mensagens no tom escolhido. Até existir usuário, o tom vem do aparelho.
 */
@RestController
@RequestMapping("/api/v1/weeks")
@Tag(name = "Resumo da semana")
class WeekSummaryController {

    private final WeekSummaries weeks;

    WeekSummaryController(WeekSummaries weeks) {
        this.weeks = weeks;
    }

    @GetMapping("/current/summary")
    @Operation(summary = "Resumo da semana atual",
            description = "Semana de 7 dias que contém hoje, a partir do dia de início das preferências "
                    + "(segunda por padrão), no fuso America/Sao_Paulo. Depois de trocar o dia de início, é por "
                    + "aqui que o app acha a semana atual.")
    WeekSummaryResponse current(@ToneParameter @RequestParam(required = false) String tone) {
        return summary(weeks.currentWeekStart(), tone);
    }

    @GetMapping("/{start}/summary")
    @Operation(summary = "Resumo de uma semana",
            description = "A data precisa cair no dia de início da semana das preferências atuais; outra data "
                    + "devolve 400, inclusive um início antigo depois de o dia mudar. As metas e os orçamentos são "
                    + "avaliados no dia de referência: o último dia da semana, ou hoje se ela está em curso. As "
                    + "mensagens seguem regras locais (sem LLM) e falam de comportamento, nunca de corpo, peso ou "
                    + "aparência.")
    WeekSummaryResponse week(
            @Parameter(description = "O primeiro dia da semana, no formato AAAA-MM-DD", example = "2026-11-02")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @ToneParameter @RequestParam(required = false) String tone) {
        DayOfWeek firstDay = weeks.firstDayOfWeek();
        if (start.getDayOfWeek() != firstDay) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A semana começa em " + firstDay + ".");
        }
        return summary(start, tone);
    }

    private WeekSummaryResponse summary(LocalDate start, String toneParameter) {
        Tone tone = Tone.fromParameter(toneParameter).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Tom desconhecido; use gentle, direct ou scold."));
        return WeekSummaryResponse.of(weeks.of(start, tone), tone);
    }

    /** Documentação comum do parâmetro {@code tone}. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Parameter(description = "Tom das mensagens: gentle, direct (padrão) ou scold")
    @interface ToneParameter {
    }
}
