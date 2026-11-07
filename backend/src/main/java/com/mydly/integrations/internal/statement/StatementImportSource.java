package com.mydly.integrations.internal.statement;

import com.mydly.finance.TransactionOrigin;
import com.mydly.integrations.ExternalTransaction;
import com.mydly.integrations.InvalidSourceException;
import com.mydly.integrations.TransactionSource;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador da porta {@link TransactionSource} para um extrato enviado pelo usuário (ADR 0005 e
 * ADR 0014). O formato é decidido pelo conteúdo: OFX quando tem o cabeçalho do OFX, CSV nos outros
 * casos. Por privacidade, nada aqui registra em log o conteúdo do arquivo.
 */
public final class StatementImportSource implements TransactionSource {

    /** O maior número de transações importadas de uma vez. */
    public static final int MAX_TRANSACTIONS = CsvParser.MAX_ROWS;

    public enum Format { OFX, CSV }

    private final String text;
    private final Format format;

    private StatementImportSource(String text, Format format) {
        this.text = text;
        this.format = format;
    }

    /**
     * O extrato de um arquivo.
     *
     * @throws InvalidSourceException quando o arquivo está vazio ou não é OFX nem CSV
     */
    public static StatementImportSource of(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new InvalidSourceException("O arquivo está vazio.");
        }
        String text = StatementText.decode(content);
        if (OfxParser.looksLikeOfx(text)) {
            return new StatementImportSource(text, Format.OFX);
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".ofx") || name.endsWith(".qfx")) {
            throw new InvalidSourceException("O arquivo não parece um OFX válido.");
        }
        if (text.indexOf('\u0000') >= 0) {
            throw new InvalidSourceException("Formato não reconhecido. Envie o extrato em OFX ou CSV, exportado do banco.");
        }
        return new StatementImportSource(text, Format.CSV);
    }

    public Format format() {
        return format;
    }

    @Override
    public TransactionOrigin origin() {
        return TransactionOrigin.STATEMENT_IMPORT;
    }

    @Override
    public List<ExternalTransaction> fetch() {
        List<ExternalTransaction> transactions = format == Format.OFX ? OfxParser.parse(text) : CsvParser.parse(text);
        if (transactions.isEmpty()) {
            throw new InvalidSourceException("Nenhuma transação encontrada no arquivo.");
        }
        if (transactions.size() > MAX_TRANSACTIONS) {
            throw new InvalidSourceException("O arquivo tem mais de " + MAX_TRANSACTIONS
                    + " transações. Exporte um período menor.");
        }
        return transactions;
    }
}
