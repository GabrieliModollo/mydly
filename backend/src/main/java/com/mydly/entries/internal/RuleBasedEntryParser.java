package com.mydly.entries.internal;

import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import com.mydly.entries.EntryParser;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.RoutineOutcome;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
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
 *   <li>verbo de finança negado ({@code não gastei nada hoje}) → desconhecido, confiança baixa:
 *       nunca vira um gasto ou uma entrada;</li>
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
 * <p>Uma frase composta vira vários itens, até {@link #MAX_ITEMS}. A frase é dividida por vírgula,
 * ponto e vírgula, {@code e}, {@code depois}, {@code também} e {@code aí}, mas só vira item a parte
 * com âncora própria: um verbo de ação, ou um número quando o item anterior já tem valor ou
 * quantidade. Na dúvida, a parte se junta à anterior. Uma parte sem verbo herda o verbo da anterior
 * ({@code gastei 30 no açaí e 20 no uber} são dois gastos). Cada item é interpretado pelas regras
 * abaixo, só com o seu trecho.
 *
 * <p>Toda rotina diz se o hábito foi cumprido. A primeira oração decide: uma negação
 * ({@code não}, {@code nem}, {@code nunca}) ou um verbo de falta ({@code faltei}, {@code pulei},
 * {@code esqueci de}, {@code deixei de}, {@code dormi mal}) torna o hábito não cumprido, e os dois
 * juntos se anulam ({@code não faltei na academia}).
 *
 * <p>Por privacidade (LGPD), esta classe não registra em log o texto nem os valores.
 */
@Component
class RuleBasedEntryParser implements EntryParser {

    // Nos Javadocs do projeto, evite vírgula dentro de aspas ou de colchetes sem par, como em
    // "1,5" ou [a, b): o BasicJsonParser que o Spring Modulith usa para gerar a documentação dos
    // módulos quebra com eles. Use {@code ...} para literais.

    static final double CONFIDENCE_MONEY_MARKER = 0.95;
    static final double CONFIDENCE_UNIT = 0.90;
    static final double CONFIDENCE_NUMBER_ALONE = 0.80;
    static final double CONFIDENCE_FINANCE_VERB_WITH_AMOUNT = 0.70;
    static final double CONFIDENCE_ROUTINE_WITHOUT_NUMBER = 0.60;
    static final double CONFIDENCE_ROUTINE_COUNT = 0.50;
    static final double CONFIDENCE_FINANCE_WITHOUT_AMOUNT = 0.40;
    static final double CONFIDENCE_NUMBER_WITHOUT_CONTEXT = 0.30;
    static final double CONFIDENCE_NEGATED_FINANCE = 0.30;
    static final double CONFIDENCE_EMPTY = 0.0;
    static final double CONFIDENCE_INHERITED_UNIT = 0.70;
    static final double CONFIDENCE_TOO_MANY_ITEMS = 0.30;

    /** Acima disso a frase vira um item só, para o usuário confirmar: pode ser um texto colado por engano. */
    static final int MAX_ITEMS = 5;

    /**
     * Número em formato brasileiro ({@code 1.250,90}, {@code 50,90}, {@code 50}), com ponto
     * decimal ({@code 2.5}) ou no formato americano, com vírgula de milhar e ponto decimal
     * ({@code 1,000.50}). As alternativas vêm da mais específica para a mais genérica.
     */
    private static final String NUMBER = "(?<![\\w.,])(\\d{1,3}(?:,\\d{3})+\\.\\d+|\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?"
            + "|\\d+,\\d+|\\d+\\.\\d+|\\d+)(?!\\d)";

    private static final String MONEY_WORD = "(?:reais|real|contos?)";
    private static final Pattern MONEY_BEFORE = Pattern.compile("r\\$\\s*" + NUMBER);
    private static final Pattern MONEY_AFTER = Pattern.compile(NUMBER + "\\s*" + MONEY_WORD + "\\b");
    private static final Pattern MONEY_WITHOUT_NUMBER = Pattern.compile("r\\$|\\b" + MONEY_WORD + "\\b");

    /**
     * Unidades por extenso ou abreviadas, com as alternativas mais longas primeiro para {@code ml}
     * não virar {@code l} e {@code horas} não virar {@code h}. Aceita o acento de {@code quilômetros}
     * para valer também no texto original.
     */
    private static final String UNIT_WORDS = "mililitros?|litros?|ml|l|horas?|hrs?|h|minutos?|min|quil[oô]metros?|km"
            + "|quilogramas?|quilos?|kg|copos?";

    private static final Pattern QUANTITY_WITH_UNIT = Pattern.compile(NUMBER + "\\s*(" + UNIT_WORDS + ")\\b");

    /** Inteiro de um número composto: {@code 30} em {@code 30 e 50}, {@code 1} em {@code 1 hora e meia}. */
    private static final String INTEGER = "(?<![\\w.,])(\\d{1,3}(?:\\.\\d{3})+|\\d+)(?![\\d,.])";
    private static final String WORD_END = "(?![\\p{L}\\p{M}\\p{N}])";
    private static final int ANY_CASE = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** {@code 1 hora e meia}, {@code 2 litros e meio}. */
    private static final Pattern HALF_UNIT = Pattern.compile(
            INTEGER + "\\s*(" + UNIT_WORDS + ")\\s+e\\s+mei[ao]" + WORD_END, ANY_CASE);
    /** {@code 1 hora e 30 minutos}, {@code 1h e 15min}. */
    private static final Pattern HOURS_AND_MINUTES = Pattern.compile(
            INTEGER + "\\s*(?:horas?|hrs?|h)\\s+e\\s+(\\d{1,2})\\s*(?:minutos?|min)" + WORD_END, ANY_CASE);
    /**
     * {@code 30 e 50} e {@code 10 reais e 50 centavos}. Sem a palavra centavos, só vale com dois dígitos,
     * sem {@code reais} no primeiro número e sem substantivo depois: {@code fiz 30 e 20 abdominais} não
     * é dinheiro, e {@code recebi 100 reais e 50 do freela} são duas entradas.
     */
    private static final Pattern REAIS_AND_CENTS = Pattern.compile(
            INTEGER + "(\\s*(?:reais|real|contos?))?\\s+e\\s+(\\d{1,2})(\\s*centavos?)?" + WORD_END, ANY_CASE);

    /**
     * Intervalo de horário: {@code de 14h às 16h} e {@code das 23h às 7h} viram a duração (2 h e 8 h).
     * Sem isso, {@code 14h} seria lido como 14 horas.
     */
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:de|das)\\s+(\\d{1,2})\\s*h(\\d{2})?\\s+(?:às|as|a|até|ate)\\s+(\\d{1,2})\\s*h(\\d{2})?"
                    + WORD_END, ANY_CASE);
    /** Horas com minutos colados: {@code 7h30} é 7,5 h, e não 7 h. */
    private static final Pattern COMPACT_HOURS = Pattern.compile(
            "(?<![\\p{L}\\p{N}.,])(\\d{1,2})h(\\d{2})" + WORD_END, ANY_CASE);
    /** Faixa de valor: {@code entre 40 e 50} não diz quanto foi, e o app pergunta. */
    private static final Pattern RANGE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])entre\\s+\\d+(?:[.,]\\d+)?(?:\\s*(?:reais|real|contos?))?\\s+e\\s+\\d+(?:[.,]\\d+)?" + WORD_END,
            ANY_CASE);
    /** O que fica no lugar de uma faixa: sem número, mas com o sinal de que havia um. */
    private static final String RANGE_REPLACEMENT = "entre valores";

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

    /** Verbo de finança negado: {@code não gastei nada} não pode virar gasto. */
    private static final Pattern NEGATED_FINANCE = Pattern.compile(
            "\\b(?:nao|nem|nunca)\\s+(?:gastei|gastou|paguei|pagou|comprei|comprou|torrei|recebi|recebeu|ganhei)\\b");

    /**
     * Separadores de item, que também separam orações. A vírgula entre dígitos é decimal
     * ({@code 12,50}) e não separa nada. Valem sobre o texto com acentos, para {@code é} não
     * ser lido como {@code e}.
     */
    private static final String ITEM_SEPARATORS =
            "(?<!\\d),|,(?!\\d)|;|" + wholeWord("e|depois|também|tambem|aí|ai");

    /** Contraste: encerra a oração principal, mas não cria item ({@code fui à academia mas não treinei perna}). */
    private static final String CONTRAST = wholeWord("mas|porém|porem");

    private static final Pattern ITEM_BREAK = Pattern.compile(ITEM_SEPARATORS,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Separadores de item mais o contraste. O contraste só separa itens quando o que vem depois dele é
     * dinheiro: {@code não fui à academia mas gastei 30 no açaí} são dois itens, e
     * {@code fui à academia mas não treinei perna} continua um só.
     */
    private static final Pattern ITEM_OR_CONTRAST = Pattern.compile(ITEM_SEPARATORS + "|(" + CONTRAST + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Separa a oração principal do resto: em {@code fui à academia, não faltei} só a primeira conta. */
    private static final Pattern CLAUSE_BREAK = Pattern.compile(ITEM_SEPARATORS + "|" + CONTRAST,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Verbo de ação no passado em primeira pessoa: termina em {@code ei} ({@code estudei}) ou é um dos
     * irregulares mais comuns. É a âncora que separa {@code fui à academia e comprei pão} em dois itens.
     */
    private static final Pattern ACTION_VERB = Pattern.compile("\\b(?:\\p{L}{2,}ei|fui|fiz|tive|estive|pus|vi|li|dei"
            + "|vim|bebi|comi|dormi|corri|recebi|perdi|vendi|escrevi|aprendi|assisti|subi|desci|sai|abri|parti"
            + "|ouvi|pedi|senti|vesti|segui|consegui|cumpri|atingi|resolvi|devolvi|conheci|esqueci|mantive"
            + "|trouxe)\\b");

    /** Verbo de falta sozinho fala da oração anterior e não é âncora: {@code fui à academia, não faltei}. */
    private static final Pattern BARE_MISS = Pattern.compile(
            "^(?:(?:nao|nem|nunca)\\s+)?(?:faltei|pulei|esqueci|deixei)$");
    private static final Pattern NEGATION = Pattern.compile("\\b(?:nao|nem|nunca)\\b");
    /** Verbos que já dizem que o hábito não aconteceu. {@code pulei corda} é exercício, não falta. */
    private static final Pattern MISS_SIGNAL = Pattern.compile(
            "\\b(?:faltei|pulei(?!\\s+corda)|esqueci\\s+de|deixei\\s+de|dormi\\s+mal)\\b");

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
            Map.entry("quilograma", "kg"), Map.entry("quilogramas", "kg"), Map.entry("quilo", "kg"),
            Map.entry("quilos", "kg"), Map.entry("kg", "kg"),
            Map.entry("copo", "copo"), Map.entry("copos", "copo"));

    /** Palavras-chave de cada categoria, em ordem de prioridade (lugares antes de produtos). */
    private static final List<Map.Entry<Category, Pattern>> CATEGORY_KEYWORDS = List.of(
            keywords(Category.PHARMACY, "farmacia|drogaria|remedios?|drogasil"),
            keywords(Category.GROCERIES, "mercado|supermercado|feira|hortifruti|atacadao|sacolao"),
            keywords(Category.TRANSPORT, "uber|onibus|metro|gasolina|combustivel|estacionamento|pedagio|taxi|passagem"),
            keywords(Category.HOUSING, "aluguel|condominio|luz|internet|iptu|gas"),
            keywords(Category.HEALTH, "medico|consulta|dentista|exames?|academia|terapia|plano de saude"),
            keywords(Category.WORK, "salario|freela|freelance|curso|coworking"),
            keywords(Category.LEISURE, "cinema|show|bar|cerveja|netflix|spotify|viagem|ingresso|jogo"),
            keywords(Category.FOOD, "pao|paes|padaria|acai|lanches?|lanchonete|ifood|restaurante|almoco|jantar"
                    + "|cafe|pizza|delivery|hamburguer|energetico|sorvete|comida"));

    private static Map.Entry<Category, Pattern> keywords(Category category, String alternatives) {
        return Map.entry(category, Pattern.compile("\\b(?:" + alternatives + ")\\b"));
    }

    /** Palavra inteira no texto com acentos: o {@code \b} do Java não trata letras acentuadas como letras. */
    private static String wholeWord(String alternatives) {
        return "(?<![\\p{L}\\p{M}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{M}\\p{N}])";
    }

    @Override
    public List<ParsedEntry> parse(String text) {
        String original = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        List<Group> groups = group(original);
        if (groups.size() > MAX_ITEMS) {
            return List.of(ParsedEntry.unknown(original, CONFIDENCE_TOO_MANY_ITEMS));
        }
        if (groups.size() <= 1) {
            return List.of(parseSingle(original));
        }
        return groups.stream().map(Group::item).toList();
    }

    /**
     * Junta as partes da frase em itens. Uma parte começa um item novo quando tem verbo próprio e o
     * item atual já tem alguma âncora, ou quando traz um número sem verbo e o item atual já tem o seu
     * valor ou quantidade: aí ela herda o verbo dele. Em qualquer outro caso, a parte completa o item
     * atual ({@code comprei pão e leite por 12 reais} é um gasto só).
     */
    private List<Group> group(String original) {
        List<Group> groups = new ArrayList<>();
        for (Span part : split(original)) {
            String normalized = clean(original.substring(part.start(), part.end()));
            boolean verb = hasVerb(normalized);
            boolean number = ANY_NUMBER.matcher(normalized).find();
            Group current = groups.isEmpty() ? null : groups.getLast();
            if (current == null) {
                groups.add(new Group(original, part.start(), part.end(), null));
            } else if (verb && current.hasAnchor()) {
                groups.add(new Group(original, part.start(), part.end(), null));
            } else if (!verb && number && hasOwnMeasure(current.item())) {
                groups.add(new Group(original, part.start(), part.end(), current.item()));
            } else {
                current.extendTo(part.end());
            }
        }
        return groups;
    }

    /** Trechos entre separadores de item, sem espaços nas pontas e sem os que não têm letra nem número. */
    private static List<Span> split(String text) {
        List<Span> parts = new ArrayList<>();
        List<Compound> compounds = findCompounds(text);
        Matcher separator = ITEM_OR_CONTRAST.matcher(text);
        int start = 0;
        boolean more = true;
        while (more) {
            more = separator.find();
            while (more && (insideCompound(compounds, separator.start())
                    || separator.group(1) != null && !moneyFollows(text, separator.end()))) {
                more = separator.find();
            }
            int end = more ? separator.start() : text.length();
            while (start < end && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            String part = text.substring(start, end);
            if (LETTER.matcher(part).find() || ANY_NUMBER.matcher(part).find()) {
                parts.add(new Span(start, end));
            }
            if (more) {
                start = separator.end();
            }
        }
        return parts;
    }

    /** O trecho depois do contraste, até o próximo separador, fala de dinheiro. */
    private static boolean moneyFollows(String text, int from) {
        Matcher next = ITEM_BREAK.matcher(text);
        int end = next.find(from) ? next.start() : text.length();
        String rest = clean(text.substring(from, end));
        return hasFinanceSignal(rest) || findFirst(rest, MONEY_BEFORE, MONEY_AFTER) != null;
    }

    private static boolean insideCompound(List<Compound> compounds, int position) {
        return compounds.stream().anyMatch(compound -> position >= compound.start() && position < compound.end());
    }

    /** Verbo de ação ou sinal de finança; um verbo de falta sozinho não conta. */
    private static boolean hasVerb(String normalized) {
        if (BARE_MISS.matcher(normalized).matches()) {
            return false;
        }
        return ACTION_VERB.matcher(normalized).find() || hasFinanceSignal(normalized)
                || INCOME_SIGNAL_WITH_NUMBER.matcher(normalized).find() && ANY_NUMBER.matcher(normalized).find();
    }

    /** O item já tem o seu valor ou a sua quantidade; um número a mais é outro item. */
    private static boolean hasOwnMeasure(ParsedEntry item) {
        return item.type() == EntryType.FINANCE && item.amount() != null
                || item.type() == EntryType.ROUTINE && item.quantity() != null;
    }

    private ParsedEntry parseSingle(String original) {
        return withSuggestedCategory(interpret(original, clean(original)), clean(original));
    }

    /**
     * Interpreta uma parte sem verbo com o verbo do item anterior: {@code 20 no uber} depois de um
     * gasto é outro gasto, e {@code 1 de suco} depois de {@code bebi 2 litros de água} é 1 litro.
     * O cumprido ou não vem junto com o verbo herdado.
     */
    private ParsedEntry interpretInherited(String original, ParsedEntry previous) {
        String normalized = clean(original);
        if (previous.type() == EntryType.FINANCE) {
            String verb = previous.direction() == Direction.INCOME ? "recebi " : "gastei ";
            return withSuggestedCategory(interpret(original, verb + normalized), normalized);
        }
        if (findFirst(normalized, MONEY_BEFORE, MONEY_AFTER) != null) {
            return parseSingle(original);
        }
        Matcher quantity = QUANTITY_WITH_UNIT.matcher(normalized);
        if (quantity.find()) {
            return routine(previous.outcome(), quantity.group(1), UNITS.get(quantity.group(2)), original, original,
                    CONFIDENCE_UNIT);
        }
        Matcher number = ANY_NUMBER.matcher(normalized);
        number.find();
        String unit = previous.unit();
        return routine(previous.outcome(), number.group(1), unit, original, original,
                unit != null ? CONFIDENCE_INHERITED_UNIT : CONFIDENCE_ROUTINE_COUNT);
    }

    private static ParsedEntry withSuggestedCategory(ParsedEntry interpreted, String normalized) {
        return interpreted.type() == EntryType.FINANCE
                ? interpreted.withCategory(suggestCategory(normalized))
                : interpreted;
    }

    /** Minúsculas, sem acentos, com os números compostos juntos e sem horários do relógio. */
    private static String clean(String original) {
        return CLOCK_TIME.matcher(joinCompoundNumbers(normalize(original))).replaceAll(" ").strip();
    }

    /** Troca cada número composto pela forma que as regras entendem: {@code 30 e 50} vira {@code 30,50}. */
    static String joinCompoundNumbers(String text) {
        StringBuilder joined = new StringBuilder();
        int last = 0;
        for (Compound compound : findCompounds(text)) {
            joined.append(text, last, compound.start()).append(compound.replacement());
            last = compound.end();
        }
        return joined.append(text, last, text.length()).toString();
    }

    /**
     * Números compostos com {@code e}, em ordem e sem sobreposição. Vale para o texto original e para o
     * normalizado: na divisão, o {@code e} dentro deles não separa itens.
     */
    private static List<Compound> findCompounds(String text) {
        List<Compound> found = new ArrayList<>();
        Matcher range = RANGE.matcher(text);
        while (range.find()) {
            found.add(new Compound(range.start(), range.end(), RANGE_REPLACEMENT));
        }
        Matcher interval = TIME_RANGE.matcher(text);
        while (interval.find()) {
            found.add(new Compound(interval.start(), interval.end(), duration(interval)));
        }
        Matcher compact = COMPACT_HOURS.matcher(text);
        while (compact.find()) {
            int minutes = Integer.parseInt(compact.group(2));
            if (minutes < 60) {
                found.add(new Compound(compact.start(), compact.end(),
                        format(hours(compact.group(1), compact.group(2))) + " h"));
            }
        }
        Matcher half = HALF_UNIT.matcher(text);
        while (half.find()) {
            found.add(new Compound(half.start(), half.end(),
                    format(toDecimal(half.group(1)).add(new BigDecimal("0.5"))) + " " + half.group(2)));
        }
        Matcher time = HOURS_AND_MINUTES.matcher(text);
        while (time.find()) {
            BigDecimal minutes = new BigDecimal(time.group(2)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            found.add(new Compound(time.start(), time.end(), format(toDecimal(time.group(1)).add(minutes)) + " h"));
        }
        Matcher money = REAIS_AND_CENTS.matcher(text);
        while (money.find()) {
            boolean centsWord = money.group(4) != null;
            boolean bareCents = money.group(2) == null && money.group(3).length() == 2
                    && !isFollowedByNoun(text.toLowerCase(Locale.ROOT), money.end());
            if (centsWord || bareCents) {
                BigDecimal cents = new BigDecimal(money.group(3)).movePointLeft(2);
                found.add(new Compound(money.start(), money.end(),
                        format(toDecimal(money.group(1)).add(cents)) + (centsWord ? " reais" : "")));
            }
        }
        found.sort(Comparator.comparingInt(Compound::start));
        List<Compound> disjoint = new ArrayList<>();
        for (Compound compound : found) {
            if (disjoint.isEmpty() || compound.start() >= disjoint.getLast().end()) {
                disjoint.add(compound);
            }
        }
        return disjoint;
    }

    /**
     * A duração de um intervalo de horário, passando da meia-noite quando o fim vem antes do começo
     * ({@code das 23h às 7h} são 8 h). Um intervalo inválido some do texto, como um horário.
     */
    private static String duration(Matcher interval) {
        String startMinutes = interval.group(2) == null ? "00" : interval.group(2);
        String endMinutes = interval.group(4) == null ? "00" : interval.group(4);
        if (Integer.parseInt(interval.group(1)) > 23 || Integer.parseInt(interval.group(3)) > 23
                || Integer.parseInt(startMinutes) > 59 || Integer.parseInt(endMinutes) > 59) {
            return " ";
        }
        BigDecimal start = hours(interval.group(1), startMinutes);
        BigDecimal end = hours(interval.group(3), endMinutes);
        BigDecimal length = end.subtract(start);
        if (length.signum() < 0) {
            length = length.add(BigDecimal.valueOf(24));
        }
        return length.signum() == 0 ? " " : format(length) + " h";
    }

    /** Horas e minutos em horas decimais, com duas casas: 7 e 30 viram 7,5. */
    private static BigDecimal hours(String hours, String minutes) {
        return new BigDecimal(hours).add(
                new BigDecimal(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
    }

    /** {@code 1.5} → {@code 1,5}; sem zeros à direita. */
    private static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** Posição de um trecho no texto original. */
    private record Span(int start, int end) {
    }

    /** Um número composto no texto e a forma que o substitui. */
    private record Compound(int start, int end, String replacement) {
    }

    /** Um item em formação: um trecho do texto original e, se herdou o verbo, o item de quem herdou. */
    private final class Group {

        private final String original;
        private final int start;
        private int end;
        private final ParsedEntry inheritedFrom;

        Group(String original, int start, int end, ParsedEntry inheritedFrom) {
            this.original = original;
            this.start = start;
            this.end = end;
            this.inheritedFrom = inheritedFrom;
        }

        void extendTo(int newEnd) {
            this.end = newEnd;
        }

        String text() {
            return original.substring(start, end);
        }

        boolean hasAnchor() {
            String normalized = clean(text());
            return inheritedFrom != null || hasVerb(normalized) || ANY_NUMBER.matcher(normalized).find();
        }

        ParsedEntry item() {
            return inheritedFrom == null ? parseSingle(text()) : interpretInherited(text(), inheritedFrom);
        }
    }

    /**
     * Categoria sugerida pelas palavras da frase, ou {@code null} quando nenhuma bate e o usuário
     * escolhe. A ordem importa: o lugar vem antes do produto, então {@code energético na farmácia}
     * fica em Farmácia e não em Alimentação.
     */
    static Category suggestCategory(String normalized) {
        for (Map.Entry<Category, Pattern> keywords : CATEGORY_KEYWORDS) {
            if (keywords.getValue().matcher(normalized).find()) {
                return keywords.getKey();
            }
        }
        return null;
    }

    private ParsedEntry interpret(String original, String normalized) {
        if (!LETTER.matcher(normalized).find() && !ANY_NUMBER.matcher(normalized).find()) {
            return ParsedEntry.unknown(null, CONFIDENCE_EMPTY);
        }

        if (NEGATED_FINANCE.matcher(normalized).find()) {
            return ParsedEntry.unknown(original, CONFIDENCE_NEGATED_FINANCE);
        }

        Direction direction = direction(normalized);
        RoutineOutcome outcome = routineOutcome(original);

        Matcher money = findFirst(normalized, MONEY_BEFORE, MONEY_AFTER);
        if (money != null) {
            return finance(direction, money.group(1), describe(original, normalized, money), original,
                    CONFIDENCE_MONEY_MARKER);
        }

        Matcher quantity = QUANTITY_WITH_UNIT.matcher(normalized);
        if (quantity.find()) {
            return routine(outcome, quantity.group(1), UNITS.get(quantity.group(2)),
                    describe(original, normalized, quantity), original, CONFIDENCE_UNIT);
        }

        Matcher alone = ONLY_NUMBER.matcher(normalized);
        if (alone.matches()) {
            return finance(direction, alone.group(1), null, original, CONFIDENCE_NUMBER_ALONE);
        }

        Matcher number = ANY_NUMBER.matcher(normalized);
        if (number.find()) {
            boolean followedByNoun = isFollowedByNoun(normalized, number.end());
            if (hasFinanceSignal(normalized) || INCOME_SIGNAL_WITH_NUMBER.matcher(normalized).find()) {
                return followedByNoun
                        ? ParsedEntry.finance(direction, null, original, CONFIDENCE_FINANCE_WITHOUT_AMOUNT)
                        : finance(direction, number.group(1), original, original, CONFIDENCE_FINANCE_VERB_WITH_AMOUNT);
            }
            return followedByNoun
                    ? routine(outcome, number.group(1), null, original, original, CONFIDENCE_ROUTINE_COUNT)
                    : ParsedEntry.unknown(original, CONFIDENCE_NUMBER_WITHOUT_CONTEXT);
        }

        if (hasFinanceSignal(normalized) || MONEY_WITHOUT_NUMBER.matcher(normalized).find()) {
            return ParsedEntry.finance(direction, null, original, CONFIDENCE_FINANCE_WITHOUT_AMOUNT);
        }
        return ParsedEntry.routine(outcome, null, null, original, CONFIDENCE_ROUTINE_WITHOUT_NUMBER);
    }

    /**
     * Finança com o valor lido, ou sem valor quando ele não pode ser gravado (zero, mais de duas casas
     * ou acima do máximo): aí o app pergunta, com o texto inteiro como descrição.
     */
    private static ParsedEntry finance(Direction direction, String number, String description, String original,
                                       double confidence) {
        BigDecimal amount = ParsedEntry.validAmount(toDecimal(number));
        return amount != null
                ? ParsedEntry.finance(direction, amount, description, confidence)
                : ParsedEntry.finance(direction, null, original, CONFIDENCE_FINANCE_WITHOUT_AMOUNT);
    }

    /** Rotina com a quantidade lida, ou sem quantidade (e sem unidade) quando ela não pode ser gravada. */
    private static ParsedEntry routine(RoutineOutcome outcome, String number, String unit, String description,
                                       String original, double confidence) {
        BigDecimal quantity = ParsedEntry.validQuantity(toDecimal(number));
        return quantity != null
                ? ParsedEntry.routine(outcome, quantity, unit, description, confidence)
                : ParsedEntry.routine(outcome, null, null, original, CONFIDENCE_ROUTINE_WITHOUT_NUMBER);
    }

    /** Minúsculas e sem acentos, para as regras não dependerem de como o usuário digitou. */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Cumprido ou não, pela primeira oração: negação ou verbo de falta tornam o hábito não cumprido,
     * e os dois juntos se anulam. Nas orações seguintes a negação fala de outra coisa, como em
     * {@code bebi água, não refrigerante}. A oração principal é a primeira com alguma letra, e as orações
     * se separam pelos mesmos separadores dos itens, mais {@code mas} e {@code porém}.
     *
     * @param original o texto com acentos, para {@code é} não ser lido como o separador {@code e}
     */
    static RoutineOutcome routineOutcome(String original) {
        String mainClause = "";
        for (String clause : CLAUSE_BREAK.split(original)) {
            if (LETTER.matcher(clause).find()) {
                mainClause = normalize(clause);
                break;
            }
        }
        boolean negated = NEGATION.matcher(mainClause).find();
        boolean missVerb = MISS_SIGNAL.matcher(mainClause).find();
        return negated != missVerb ? RoutineOutcome.MISSED : RoutineOutcome.DONE;
    }

    private static boolean hasFinanceSignal(String text) {
        return EXPENSE_SIGNAL.matcher(text).find() || INCOME_SIGNAL.matcher(text).find();
    }

    /**
     * Entrada ou saída pelo primeiro sinal de finança da frase, que é o da oração principal: em
     * {@code paguei o salário da diarista} e {@code gastei 30 no presente que ganhei} o verbo de gasto
     * vem antes, e é gasto.
     */
    private static Direction direction(String text) {
        int expense = firstPosition(text, EXPENSE_SIGNAL);
        int income = Math.min(firstPosition(text, INCOME_SIGNAL), firstPosition(text, INCOME_SIGNAL_WITH_NUMBER));
        return income < expense ? Direction.INCOME : Direction.EXPENSE;
    }

    private static int firstPosition(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.start() : Integer.MAX_VALUE;
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
     * padrão brasileiro; com vírgula de milhar e ponto decimal ({@code 1,000.50}) é o padrão americano.
     */
    static BigDecimal toDecimal(String number) {
        if (number.matches("\\d{1,3}(?:,\\d{3})+\\.\\d+")) {
            return new BigDecimal(number.replace(",", ""));
        }
        if (number.contains(",")) {
            return new BigDecimal(number.replace(".", "").replace(',', '.'));
        }
        if (number.matches("\\d{1,3}(?:\\.\\d{3})+")) {
            return new BigDecimal(number.replace(".", ""));
        }
        return new BigDecimal(number);
    }
}
