package com.mydly.routines.internal;

import com.mydly.routines.Measure;
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
public class HabitCatalog {

    private final JdbcClient jdbc;
    private volatile List<Habit> habits;

    HabitCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * O hábito cuja palavra-chave aparece primeiro no texto. Em {@code bebi água, não refrigerante}
     * a água vem antes, então é Hidratação.
     */
    public Optional<Habit> match(String text) {
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

    /** O hábito de água, para quem escreve só o volume ({@link #isPlainWater}). */
    static final String HYDRATION = "HYDRATION";

    /**
     * {@code bebi}, a quantidade e a unidade de volume, e nada mais além de advérbios de tempo:
     * {@code bebi 1 litro}, {@code bebi 500ml}, {@code bebi 2 litros hoje}. Um complemento depois da
     * quantidade ({@code de cerveja}, {@code do suco}) diz o que foi bebido e tira a frase daqui.
     */
    private static final Pattern PLAIN_WATER = Pattern.compile(
            "^(?:eu\\s+)?bebi\\s+"
                    + "(?:\\d+(?:[.,]\\d+)?|um|uma|dois|duas|tres|meio|meia)\\s*"
                    + "(?:mililitros?|ml|litros?|l)\\b"
                    + "(?:\\s+e\\s+mei[oa])?"
                    + "(?:\\s+(?:hoje|agora|ontem|cedo|hoje cedo|no total|ao todo|ate agora|de manha|pela manha"
                    + "|a tarde|a noite|durante o dia))*"
                    + "\\s*[.!]?$");

    /**
     * O hábito do item: o da palavra-chave que aparece primeiro ({@link #match(String)}) ou, sem
     * nenhuma, Hidratação quando a frase é só {@code bebi} com um volume ({@link #isPlainWater}).
     */
    public Optional<Habit> match(String text, String unit) {
        Optional<Habit> byKeyword = match(text);
        if (byKeyword.isPresent() || !isPlainWater(text, unit)) {
            return byKeyword;
        }
        return find(HYDRATION);
    }

    /**
     * {@code bebi 1 litro} sem dizer o que foi bebido conta como água: a unidade é de volume ({@code L} ou {@code mL}), o
     * verbo é {@code bebi} e não há complemento de bebida depois da quantidade, só advérbios de tempo.
     * {@code bebi 1 litro de cerveja} e {@code bebi 500 ml de suco} ficam de fora.
     */
    static boolean isPlainWater(String text, String unit) {
        if (text == null || !("L".equals(unit) || "mL".equals(unit))) {
            return false;
        }
        return PLAIN_WATER.matcher(normalize(text).trim().replaceAll("\\s+", " ")).matches();
    }

    /** Todos os hábitos do catálogo, na ordem do código. */
    public List<Habit> all() {
        List<Habit> loaded = habits;
        if (loaded == null) {
            loaded = jdbc.sql("SELECT code, name, polarity, measure, keywords FROM habit ORDER BY code")
                    .query((rs, n) -> Habit.of(rs.getString("code"), rs.getString("name"),
                            Polarity.valueOf(rs.getString("polarity")), Measure.valueOf(rs.getString("measure")),
                            Arrays.asList((String[]) rs.getArray("keywords").getArray())))
                    .list();
            habits = loaded;
        }
        return loaded;
    }

    /** O hábito do catálogo pelo código ({@code HYDRATION}). */
    public Optional<Habit> find(String code) {
        return all().stream().filter(habit -> habit.code().equals(code)).findFirst();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Um hábito do catálogo, com a dimensão de medida e as palavras-chave já prontas para a busca. */
    public record Habit(String code, String name, Polarity polarity, Measure measure, Pattern pattern) {

        static Habit of(String code, String name, Polarity polarity, Measure measure, List<String> keywords) {
            String alternatives = String.join("|", keywords.stream().map(Pattern::quote).toList());
            return new Habit(code, name, polarity, measure, Pattern.compile("\\b(?:" + alternatives + ")\\b"));
        }

        /** Um hábito que não casa com nenhum texto; serve às regras de meta, que não usam as palavras-chave. */
        public static Habit of(String code, String name, Polarity polarity, Measure measure) {
            return new Habit(code, name, polarity, measure, Pattern.compile("(?!)"));
        }
    }
}
