package com.mydly.entries.internal;

import com.mydly.entries.Direction;
import com.mydly.entries.EntryParser;
import com.mydly.entries.ParsedEntry;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parser por regras locais (ADR 0003, item 1). Funciona sem rede e sem chave de LLM.
 * A tabela completa de regras e confianças está em {@code docs/parser.md}.
 *
 * <p>Ordem de decisão, da regra mais forte para a mais fraca:
 * <ol>
 *   <li>texto vazio, sem letras nem números ou só com horário → desconhecido, confiança zero;</li>
 *   <li>número com marcador monetário ({@code R$ 50}, {@code 50 reais}) → finança;</li>
 *   <li>número com unidade de medida ({@code 2 litros}, {@code 500ml}, {@code 2h}) → rotina;</li>
 *   <li>número sozinho ({@code 50}, {@code 1.250,90}) → finança;</li>
 *   <li>número com verbo de finança: valor se vier seguido de preposição ou do fim da frase
 *       ({@code paguei 12,50 no estacionamento}); finança sem valor se vier seguido de substantivo,
 *       porque aí é quantidade ({@code comprei 2 pães});</li>
 *   <li>número seguido de substantivo, sem sinal de dinheiro ({@code fiz 30 flexões}) → rotina com contagem;</li>
 *   <li>outro número sem contexto → desconhecido;</li>
 *   <li>sinal de dinheiro sem número ({@code comprei pão}, {@code cinquenta reais}) → finança sem valor;</li>
 *   <li>frase sem número ({@code dormi bem}) → rotina.</li>
 * </ol>
 *
 * <p>Horários ({@code acordei às 6h}) são ignorados antes das regras: indicam quando algo
 * aconteceu, não uma duração.
 *
 * <p>Por privacidade (LGPD), esta classe não registra em log o texto nem os valores.
 */
@Component
class RuleBasedEntryParser implements EntryParser {

    // Nos Javadocs, números com vírgula vão em {@code ...} e não entre aspas: o BasicJsonParser
    // que o Spring Modulith usa para gerar a documentação dos módulos quebra com "1,5" escapado.

    static final double CONFIDENCE_MONEY_MARKER = 0.95;
    static final double CONFIDENCE_UNIT = 0.90;
    static final double CONFIDENCE_NUMBER_ALONE = 0.80;
    static final double CONFIDENCE_FINANCE_VERB_WITH_AMOUNT = 0.70;
    static final double CONFIDENCE_ROUTINE_WITHOUT_NUMBER = 0.60;
    static final double CONFIDENCE_ROUTINE_COUNT = 0.50;
    static final double CONFIDENCE_FINANCE_WITHOUT_AMOUNT = 0.40;
    static final double CONFIDENCE_NUMBER_WITHOUT_CONTEXT = 0.30;
    static final double CONFIDENCE_EMPTY = 0.0;

    /**
     * Número em formato brasileiro ({@code 1.250,90}, {@code 50,90}, {@code 50}) ou com ponto
     * decimal ({@code 2.5}). As alternativas vêm da mais específica para a mais genérica.
     */
    private static final String NUMBER = "(?<![\\w.,])(\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?|\\d+,\\d+|\\d+\\.\\d+|\\d+)(?!\\d)";

    private static final String MONEY_WORD = "(?:reais|real|contos?)";
    private static final Pattern MONEY_BEFORE = Pattern.compile("r\\$\\s*" + NUMBER);
    private static final Pattern MONEY_AFTER = Pattern.compile(NUMBER + "\\s*" + MONEY_WORD + "\\b");
    private static final Pattern MONEY_WITHOUT_NUMBER = Pattern.compile("r\\$|\\b" + MONEY_WORD + "\\b");

    /** Alternativas mais longas primeiro, para "ml" não virar "l" e "horas" não virar "h". */
    private static final Pattern QUANTITY_WITH_UNIT = Pattern.compile(
            NUMBER + "\\s*(mililitros?|litros?|ml|l|horas?|hrs?|h|minutos?|min|quilometros?|km|copos?)\\b");

    /** Horário do relógio: {@code às 6h}, {@code das 7 horas}, {@code pelas 22h30}. */
    private static final Pattern CLOCK_TIME = Pattern.compile(
            "\\b(?:as|das|pelas)\\s+" + NUMBER + "\\s*(?:horas?|hrs?|h)(?:\\d{1,2})?\\b");

    private static final Pattern ANY_NUMBER = Pattern.compile(NUMBER);
    private static final Pattern ONLY_NUMBER = Pattern.compile("^" + NUMBER + "$");
    private static final Pattern NEXT_WORD = Pattern.compile("^\\s*(\\p{L}+)");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private static final Pattern EXPENSE_SIGNAL = Pattern.compile(
            "\\b(?:gastei|gastou|gasto|paguei|pagou|comprei|comprou|custou|torrei)\\b");
    /** Sinais de entrada que, sozinhos, já indicam finança. */
    private static final Pattern INCOME_SIGNAL = Pattern.compile(
            "\\b(?:recebi|recebeu|recebido|recebida|ganhei|salario|reembolso|reembolsado)\\b");
    /** "caiu" só indica entrada junto de um número: "o celular caiu" não é finança. */
    private static final Pattern INCOME_SIGNAL_WITH_NUMBER = Pattern.compile("\\bcaiu\\b");

    /** Palavras que, logo depois do número, mostram que ele é valor e não quantidade. */
    private static final Set<String> CONNECTORS = Set.of(
            "no", "na", "nos", "nas", "num", "numa", "em", "de", "do", "da", "dos", "das",
            "pra", "pro", "para", "com", "por", "pelo", "pela", "e", "a", "o", "ao", "aos");

    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("mililitro", "mL"), Map.entry("mililitros", "mL"), Map.entry("ml", "mL"),
            Map.entry("litro", "L"), Map.entry("litros", "L"), Map.entry("l", "L"),
            Map.entry("hora", "h"), Map.entry("horas", "h"), Map.entry("hr", "h"), Map.entry("hrs", "h"),
            Map.entry("h", "h"),
            Map.entry("minuto", "min"), Map.entry("minutos", "min"), Map.entry("min", "min"),
            Map.entry("quilometro", "km"), Map.entry("quilometros", "km"), Map.entry("km", "km"),
            Map.entry("copo", "copo"), Map.entry("copos", "copo"));

    @Override
    public List<ParsedEntry> parse(String text) {
        return List.of(parseSingle(text));
    }

    private ParsedEntry parseSingle(String text) {
        String original = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        String normalized = CLOCK_TIME.matcher(normalize(original)).replaceAll(" ").strip();

        if (!LETTER.matcher(normalized).find() && !ANY_NUMBER.matcher(normalized).find()) {
            return ParsedEntry.unknown(null, CONFIDENCE_EMPTY);
        }

        Direction direction = direction(normalized);

        Matcher money = findFirst(normalized, MONEY_BEFORE, MONEY_AFTER);
        if (money != null) {
            return ParsedEntry.finance(direction, toDecimal(money.group(1)),
                    describe(original, normalized, money), CONFIDENCE_MONEY_MARKER);
        }

        Matcher quantity = QUANTITY_WITH_UNIT.matcher(normalized);
        if (quantity.find()) {
            return ParsedEntry.routine(toDecimal(quantity.group(1)), UNITS.get(quantity.group(2)),
                    describe(original, normalized, quantity), CONFIDENCE_UNIT);
        }

        Matcher alone = ONLY_NUMBER.matcher(normalized);
        if (alone.matches()) {
            return ParsedEntry.finance(direction, toDecimal(alone.group(1)), null, CONFIDENCE_NUMBER_ALONE);
        }

        Matcher number = ANY_NUMBER.matcher(normalized);
        if (number.find()) {
            boolean followedByNoun = isFollowedByNoun(normalized, number.end());
            if (hasFinanceSignal(normalized) || INCOME_SIGNAL_WITH_NUMBER.matcher(normalized).find()) {
                return followedByNoun
                        ? ParsedEntry.finance(direction, null, original, CONFIDENCE_FINANCE_WITHOUT_AMOUNT)
                        : ParsedEntry.finance(direction, toDecimal(number.group(1)), original,
                                CONFIDENCE_FINANCE_VERB_WITH_AMOUNT);
            }
            return followedByNoun
                    ? ParsedEntry.routine(toDecimal(number.group(1)), null, original, CONFIDENCE_ROUTINE_COUNT)
                    : ParsedEntry.unknown(original, CONFIDENCE_NUMBER_WITHOUT_CONTEXT);
        }

        if (hasFinanceSignal(normalized) || MONEY_WITHOUT_NUMBER.matcher(normalized).find()) {
            return ParsedEntry.finance(direction, null, original, CONFIDENCE_FINANCE_WITHOUT_AMOUNT);
        }
        return ParsedEntry.routine(null, null, original, CONFIDENCE_ROUTINE_WITHOUT_NUMBER);
    }

    /** Minúsculas e sem acentos, para as regras não dependerem de como o usuário digitou. */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean hasFinanceSignal(String text) {
        return EXPENSE_SIGNAL.matcher(text).find() || INCOME_SIGNAL.matcher(text).find();
    }

    private static Direction direction(String text) {
        return INCOME_SIGNAL.matcher(text).find() || INCOME_SIGNAL_WITH_NUMBER.matcher(text).find()
                ? Direction.INCOME
                : Direction.EXPENSE;
    }

    /**
     * Número seguido de substantivo é quantidade ({@code 2 pães}); seguido de preposição,
     * pontuação ou fim da frase é valor ({@code 30 no açaí}).
     */
    private static boolean isFollowedByNoun(String text, int numberEnd) {
        Matcher next = NEXT_WORD.matcher(text.substring(numberEnd));
        return next.find() && !CONNECTORS.contains(next.group(1));
    }

    private static Matcher findFirst(String text, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher;
            }
        }
        return null;
    }

    /**
     * Devolve o texto original como descrição, ou {@code null} quando, tirando o valor
     * reconhecido, não sobra nenhuma palavra (ex.: {@code 50 reais}, {@code R$ 1.250,90}).
     */
    private static String describe(String original, String normalized, Matcher match) {
        String rest = normalized.substring(0, match.start()) + normalized.substring(match.end());
        return LETTER.matcher(rest).find() ? original : null;
    }

    /**
     * Converte {@code 1.250,90} → 1250.90, {@code 50,90} → 50.90, {@code 2.5} → 2.5 e
     * {@code 50} → 50. Ponto seguido de exatamente três dígitos é separador de milhar, como no
     * padrão brasileiro.
     */
    static BigDecimal toDecimal(String number) {
        if (number.contains(",")) {
            return new BigDecimal(number.replace(".", "").replace(',', '.'));
        }
        if (number.matches("\\d{1,3}(?:\\.\\d{3})+")) {
            return new BigDecimal(number.replace(".", ""));
        }
        return new BigDecimal(number);
    }
}
