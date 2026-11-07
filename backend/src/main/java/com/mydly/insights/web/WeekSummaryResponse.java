package com.mydly.insights.web;

import com.mydly.entries.Category;
import com.mydly.insights.internal.InsightKind;
import com.mydly.insights.internal.Tone;
import com.mydly.insights.internal.WeekSummary;
import com.mydly.routines.Polarity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Resumo da semana como a API devolve.
 */
@Schema(description = "Resumo da semana")
record WeekSummaryResponse(
        @Schema(description = "O primeiro dia da semana") LocalDate weekStart,
        @Schema(description = "O último dia da semana") LocalDate weekEnd,
        @Schema(description = "Dias que já começaram: 7 numa semana passada, de 1 a 7 na atual") int daysElapsed,
        @Schema(description = "Tom usado nas mensagens") String tone,
        @Schema(description = "Só os hábitos com algum registro na semana") List<HabitRow> habits,
        Money money,
        @Schema(description = "A semana anterior nos mesmos dias decorridos; ausente quando ela não tem dados")
        PreviousWeek previous,
        @Schema(description = "No máximo três mensagens; a primeira é a principal") List<InsightView> insights) {

    static WeekSummaryResponse of(WeekSummary summary, Tone tone) {
        return new WeekSummaryResponse(summary.weekStart(), summary.weekEnd(), summary.daysElapsed(), tone.parameter(),
                summary.habits().stream().map(HabitRow::of).toList(),
                new Money(summary.money().spent(), summary.money().received(),
                        summary.money().spentByCategory().stream()
                                .map(total -> new CategoryAmount(total.category(), total.amount())).toList()),
                summary.previous() == null ? null
                        : new PreviousWeek(summary.previous().spent(), summary.previous().habitsAchieved(),
                                summary.previous().comparedDays()),
                summary.insights().stream()
                        .map(insight -> new InsightView(insight.kind(), insight.text(), insight.numbers())).toList());
    }

    @Schema(description = "Um hábito na semana, com os 7 dias a partir do primeiro dia da semana")
    record HabitRow(String code, String name, Polarity polarity,
                    @Schema(description = "ACHIEVED, SLIPPED, MISSED, NONE (sem registro) ou FUTURE")
                    List<WeekSummary.DayMark> days,
                    Score score) {

        static HabitRow of(WeekSummary.HabitWeek habit) {
            return new HabitRow(habit.code(), habit.name(), habit.polarity(), habit.days(),
                    new Score(habit.achievedDays(), habit.daysElapsed(), null));
        }
    }

    @Schema(description = "Placar do hábito; o alvo chega com as metas (etapa Metas e preferências)")
    record Score(@Schema(description = "Dias cumpridos (bom cumprido ou ruim evitado)") int achieved,
                 @Schema(description = "Dias decorridos") int elapsedDays,
                 @Schema(description = "Meta de dias na semana; ausente enquanto não há metas") Integer target) {
    }

    @Schema(description = "Gastos e entradas da semana")
    record Money(BigDecimal spent, BigDecimal received, List<CategoryAmount> spentByCategory) {
    }

    @Schema(description = "Saídas de uma categoria; categoria ausente = sem categoria")
    record CategoryAmount(Category category, BigDecimal amount) {
    }

    @Schema(description = "Totais da semana anterior nos mesmos dias decorridos, para a comparação")
    record PreviousWeek(BigDecimal spent, long habitsAchieved,
                        @Schema(description = "Dias comparados a partir do início (7 numa semana completa)")
                        int comparedDays) {
    }

    @Schema(description = "Uma mensagem da semana e os números que a sustentam")
    record InsightView(InsightKind kind, String text, Map<String, Object> numbers) {
    }
}
