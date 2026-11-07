package com.mydly.integrations.internal.statement;

import com.mydly.entries.Direction;
import com.mydly.integrations.ExternalTransaction;
import com.mydly.integrations.InvalidSourceException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lê extratos CSV com cabeçalho. As colunas são achadas pelo nome, sem acento nem diferença de
 * maiúsculas: data ({@code data}, {@code date}), valor ({@code valor}, {@code amount}), descrição
 * ({@code descricao}, {@code historico}, {@code title}...) e, se houver, um id ({@code identificador}).
 * O separador é vírgula, ponto e vírgula ou tabulação, achado no cabeçalho.
 *
 * <p>Sinal do valor: negativo é saída e positivo é entrada, como no extrato de conta. A exceção é a
 * fatura de cartão no formato {@code date title amount}, em que a compra vem positiva e o pagamento
 * ou estorno vem negativo.
 */
final class CsvParser {

    /** O maior número de linhas lidas de um arquivo. */
    static final int MAX_ROWS = 5000;

    private static final Set<String> DATE_COLUMNS = Set.of("data", "date", "data lancamento", "data de lancamento",
            "data da transacao", "data movimento", "dt");
    private static final Set<String> AMOUNT_COLUMNS = Set.of("valor", "amount", "valor (r$)", "valor r$", "quantia");
    private static final Set<String> DESCRIPTION_COLUMNS = Set.of("descricao", "description", "historico", "title",
            "titulo", "lancamento", "estabelecimento", "detalhes");
    private static final Set<String> ID_COLUMNS = Set.of("identificador", "id", "id transacao");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT));

    private CsvParser() {
    }

    static List<ExternalTransaction> parse(String text) {
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new InvalidSourceException("O arquivo está vazio.");
        }
        char separator = separator(lines.getFirst());
        List<String> header = split(lines.getFirst(), separator).stream().map(CsvParser::normalize).toList();
        int date = column(header, DATE_COLUMNS);
        int amount = column(header, AMOUNT_COLUMNS);
        if (date < 0 || amount < 0) {
            throw new InvalidSourceException("O CSV precisa de uma coluna de data e uma de valor, com cabeçalho.");
        }
        int description = column(header, DESCRIPTION_COLUMNS);
        int id = column(header, ID_COLUMNS);
        boolean cardBill = header.equals(List.of("date", "title", "amount"));
        if (lines.size() - 1 > MAX_ROWS) {
            throw new InvalidSourceException("O arquivo tem mais de " + MAX_ROWS + " linhas. Exporte um período menor.");
        }

        List<ExternalTransaction> transactions = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            int lineNumber = i + 1;
            List<String> fields = split(lines.get(i), separator);
            LocalDate day = date(field(fields, date)).orElseThrow(() ->
                    new InvalidSourceException("Linha " + lineNumber + ": data inválida."));
            BigDecimal signed = StatementText.amount(field(fields, amount)).orElseThrow(() ->
                    new InvalidSourceException("Linha " + lineNumber + ": valor inválido."));
            if (signed.signum() == 0) {
                continue;
            }
            boolean expense = cardBill ? signed.signum() > 0 : signed.signum() < 0;
            String bankText = description < 0 ? null : field(fields, description);
            String bankId = id < 0 ? "" : field(fields, id);
            String key = bankId.isBlank()
                    ? "linha:" + StatementText.hash(day.toString(), signed.toPlainString(), String.valueOf(bankText))
                    : "id:" + bankId;
            int occurrence = seen.merge(key, 1, Integer::sum);
            String externalId = "csv:" + key + (occurrence > 1 ? "#" + occurrence : "");
            transactions.add(new ExternalTransaction(externalId, day, signed.abs(),
                    expense ? Direction.EXPENSE : Direction.INCOME, bankText));
        }
        return transactions;
    }

    private static char separator(String header) {
        long semicolons = header.chars().filter(c -> c == ';').count();
        long commas = header.chars().filter(c -> c == ',').count();
        long tabs = header.chars().filter(c -> c == '\t').count();
        if (tabs > semicolons && tabs > commas) {
            return '\t';
        }
        return semicolons > commas ? ';' : ',';
    }

    /** Divide uma linha respeitando aspas: {@code "Padaria, centro"} é um campo só. */
    static List<String> split(String line, char separator) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == separator && !quoted) {
                fields.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().strip());
        return fields;
    }

    private static int column(List<String> header, Set<String> names) {
        for (int i = 0; i < header.size(); i++) {
            if (names.contains(header.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String field(List<String> fields, int index) {
        return index < fields.size() ? fields.get(index) : "";
    }

    private static Optional<LocalDate> date(String raw) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(raw.strip(), format));
            } catch (DateTimeParseException tryNext) {
                // Tenta o próximo formato.
            }
        }
        return Optional.empty();
    }

    private static String normalize(String name) {
        String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
