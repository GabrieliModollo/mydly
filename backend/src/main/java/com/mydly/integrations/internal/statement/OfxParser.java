package com.mydly.integrations.internal.statement;

import com.mydly.entries.Direction;
import com.mydly.integrations.ExternalTransaction;
import com.mydly.integrations.InvalidSourceException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê extratos OFX, nas versões 1.x (SGML, em que as tags de valor não fecham) e 2.x (XML). Só o que o
 * app usa: data, valor, id e descrição de cada {@code STMTTRN}, de conta corrente e de cartão.
 *
 * <p>O id externo junta a conta e o {@code FITID}, que o banco só garante único dentro da conta. Sem
 * {@code FITID}, o id é um hash da linha, como no CSV.
 */
final class OfxParser {

    private static final Pattern STATEMENT = Pattern.compile("<(CC)?STMTRS>(.*?)</(CC)?STMTRS>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TRANSACTION = Pattern.compile("<STMTTRN>(.*?)</STMTTRN>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DATE = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})");

    private OfxParser() {
    }

    /** {@code true} quando o texto tem cara de OFX, em qualquer versão. */
    static boolean looksLikeOfx(String text) {
        String start = text.substring(0, Math.min(text.length(), 2000)).toUpperCase();
        return start.contains("OFXHEADER") || start.contains("<OFX>") || start.contains("<?OFX");
    }

    static List<ExternalTransaction> parse(String text) {
        List<ExternalTransaction> transactions = new ArrayList<>();
        Matcher statements = STATEMENT.matcher(text);
        boolean anyStatement = false;
        while (statements.find()) {
            anyStatement = true;
            String body = statements.group(2);
            read(body, element(body, "ACCTID").orElse(""), transactions);
        }
        if (!anyStatement) {
            read(text, element(text, "ACCTID").orElse(""), transactions);
        }
        return transactions;
    }

    private static void read(String statement, String account, List<ExternalTransaction> into) {
        Matcher blocks = TRANSACTION.matcher(statement);
        Map<String, Integer> seen = new HashMap<>();
        int number = 0;
        while (blocks.find()) {
            int position = ++number;
            String block = blocks.group(1);
            LocalDate date = element(block, "DTPOSTED").flatMap(OfxParser::date).orElseThrow(() ->
                    new InvalidSourceException("Transação " + position + " do OFX sem data válida."));
            BigDecimal signed = element(block, "TRNAMT").flatMap(StatementText::amount).orElseThrow(() ->
                    new InvalidSourceException("Transação " + position + " do OFX sem valor válido."));
            if (signed.signum() == 0) {
                continue;
            }
            String description = element(block, "MEMO").or(() -> element(block, "NAME")).orElse(null);
            String fitId = element(block, "FITID").orElse("");
            String key = fitId.isBlank()
                    ? "linha:" + StatementText.hash(date.toString(), signed.toPlainString(), String.valueOf(description))
                    : "fitid:" + fitId;
            // Duas transações iguais no mesmo arquivo continuam sendo duas.
            int occurrence = seen.merge(key, 1, Integer::sum);
            String externalId = "ofx:" + account + ":" + key + (occurrence > 1 ? "#" + occurrence : "");
            into.add(new ExternalTransaction(externalId, date, signed.abs(),
                    signed.signum() < 0 ? Direction.EXPENSE : Direction.INCOME, description));
        }
    }

    /** O valor de uma tag: até a próxima tag ou o fim da linha, porque no SGML ela não fecha. */
    private static Optional<String> element(String text, String tag) {
        Matcher matcher = Pattern.compile("<" + tag + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String value = unescape(matcher.group(1).strip());
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<LocalDate> date(String raw) {
        Matcher matcher = DATE.matcher(raw);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))));
        } catch (DateTimeException invalid) {
            return Optional.empty();
        }
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
