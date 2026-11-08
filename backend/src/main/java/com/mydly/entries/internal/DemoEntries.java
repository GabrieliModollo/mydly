package com.mydly.entries.internal;

import com.mydly.accounts.DemoReset;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A parte de {@code entries} no reset da demonstração (ADR 0017): apaga os lançamentos do usuário de
 * demonstração e escreve as três semanas antes de hoje, com frases fictícias. Hoje fica vazio para
 * quem visita escrever o primeiro lançamento. Os lançamentos
 * passam pelo parser como os de qualquer pessoa, e os módulos {@code finance} e {@code routines} se
 * preenchem pelos eventos de sempre.
 */
@Component
class DemoEntries {

    /**
     * Três semanas: a semana anterior fica sempre completa e tem com que se comparar, e o Resumo mostra
     * nela o cruzamento de sono e gasto.
     */
    static final int DAYS = 21;

    private final EntryService entries;
    private final JdbcClient jdbc;

    DemoEntries(EntryService entries, JdbcClient jdbc) {
        this.entries = entries;
        this.jdbc = jdbc;
    }

    /** Um lançamento de exemplo: quantos dias antes de hoje, a hora em São Paulo e o texto. */
    record Line(int daysAgo, LocalTime time, String text) {
    }

    @EventListener
    void on(DemoReset reset) {
        jdbc.sql("DELETE FROM entry WHERE user_id = :userId").param("userId", reset.userId()).update();
        for (Line line : week(DAYS)) {
            Instant occurredAt = reset.today().minusDays(line.daysAgo()).atTime(line.time())
                    .atZone(EntryService.USER_ZONE).toInstant();
            entries.create(reset.userId(), null, line.text(), occurredAt);
        }
    }

    /**
     * Os lançamentos de exemplo, do mais antigo (há {@code days} dias) ao de ontem. Em quaisquer 7 dias seguidos há 3 noites
     * de sono ruim, e nelas o gasto é maior (delivery e uber): é o padrão que o Resumo encontra (ADR 0009).
     * A água às vezes fica em 1 litro, a academia vai 3 vezes por semana contra a meta de 4 e o delivery
     * passa do limite de 2 dias, para as metas aparecerem nos dois sentidos. As semanas gastam quase o
     * mesmo e Alimentação fica longe do limite: assim o cruzamento de sono e gasto fica entre
     * as três mensagens da semana anterior, que só mostra as mais importantes.
     */
    static List<Line> week(int days) {
        List<Line> lines = new ArrayList<>();
        for (int daysAgo = days; daysAgo >= 1; daysAgo--) {
            int slot = daysAgo % 7;
            boolean badNight = slot == 1 || slot == 3 || slot == 5;
            lines.add(new Line(daysAgo, LocalTime.of(7, 30), badNight ? "Dormi mal" : "Dormi bem"));
            if (daysAgo == 1) {
                lines.add(new Line(daysAgo, LocalTime.of(8, 15), "Comprei pão"));
            }
            if (daysAgo == days - 1) {
                lines.add(new Line(daysAgo, LocalTime.of(9, 0), "Recebi 3000 reais de salário"));
            }
            lines.add(new Line(daysAgo, LocalTime.of(10, 0),
                    daysAgo % 3 == 0 ? "Bebi 1 litro de água" : "Bebi 2 litros de água"));
            lines.add(new Line(daysAgo, LocalTime.of(12, 30),
                    badNight ? "Gastei 45 reais no ifood" : "Gastei 28 reais no almoço"));
            if (badNight) {
                lines.add(new Line(daysAgo, LocalTime.of(18, 0), "Paguei 22 reais no uber"));
            }
            if (slot == 0 || slot == 2 || slot == 4) {
                lines.add(new Line(daysAgo, LocalTime.of(19, 0), "Fui à academia"));
            }
            if (badNight) {
                lines.add(new Line(daysAgo, LocalTime.of(20, 0), "Pedi delivery"));
            }
            if (slot == 2 || slot == 6) {
                lines.add(new Line(daysAgo, LocalTime.of(21, 0), "Estudei durante 2 horas"));
            }
            if (daysAgo == 4) {
                lines.add(new Line(daysAgo, LocalTime.of(21, 30), "Gastei 40 reais no cinema"));
            }
        }
        return lines;
    }
}
