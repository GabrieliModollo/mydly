package com.mydly.insights;

import com.mydly.insights.internal.Tone;
import com.mydly.insights.internal.WeekSummaries;
import com.mydly.insights.internal.WeekSummary;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública do módulo insights: a mensagem principal da semana, a mesma que abre o Resumo (ADR 0009).
 * O resumo da semana por notificação (módulo {@code notifications}) usa esta mensagem.
 */
@Service
public class WeekHeadline {

    private final WeekSummaries weeks;

    WeekHeadline(WeekSummaries weeks) {
        this.weeks = weeks;
    }

    /**
     * A primeira mensagem da semana que começa em {@code weekStart}, no tom pedido ({@code gentle},
     * {@code direct} ou {@code scold}; outro valor vira o padrão). Vazio quando a semana não tem dados
     * suficientes para uma mensagem.
     */
    @Transactional(readOnly = true)
    public Optional<String> of(UUID userId, LocalDate weekStart, String tone) {
        Tone chosen = Tone.fromParameter(tone).orElse(Tone.DIRECT);
        return weeks.of(userId, weekStart, chosen).insights().stream()
                .findFirst()
                .map(WeekSummary.Insight::text);
    }
}
