package com.mydly.routines.internal;

import com.mydly.routines.Polarity;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O catálogo fixo de hábitos (tabela {@code habit}) e a busca do hábito de um item pelas palavras-chave.
 * A busca vive aqui, e não no parser: o parser diz o que aconteceu, e o módulo routines diz qual
 * hábito é e se ele é bom ou ruim.
 */
@Component
class HabitCatalog {

    private final JdbcClient jdbc;
    private volatile List<Habit> habits;

    HabitCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * O hábito cuja palavra-chave aparece primeiro no texto. Em {@code bebi água, não refrigerante}
     * a água vem antes, então é Hidratação.
     */
    Optional<Habit> match(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String normalized = normalize(text);
        Habit best = null;
        int bestPosition = Integer.MAX_VALUE;
        for (Habit habit : all()) {
            Matcher matcher = habit.pattern().matcher(normalized);
            if (matcher.find() && matcher.start() < bestPosition) {
                best = habit;
                bestPosition = matcher.start();
            }
        }
        return Optional.ofNullable(best);
    }

    List<Habit> all() {
        List<Habit> loaded = habits;
        if (loaded == null) {
            loaded = jdbc.sql("SELECT code, name, polarity, keywords FROM habit ORDER BY code")
                    .query((rs, n) -> Habit.of(rs.getString("code"), rs.getString("name"),
                            Polarity.valueOf(rs.getString("polarity")),
                            Arrays.asList((String[]) rs.getArray("keywords").getArray())))
                    .list();
            habits = loaded;
        }
        return loaded;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Um hábito do catálogo, com as palavras-chave já prontas para a busca. */
    record Habit(String code, String name, Polarity polarity, Pattern pattern) {

        static Habit of(String code, String name, Polarity polarity, List<String> keywords) {
            String alternatives = String.join("|", keywords.stream().map(Pattern::quote).toList());
            return new Habit(code, name, polarity, Pattern.compile("\\b(?:" + alternatives + ")\\b"));
        }
    }
}
