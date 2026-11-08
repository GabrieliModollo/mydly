package com.mydly.integrations.internal.openfinance;

import com.mydly.entries.Direction;
import com.mydly.finance.TransactionOrigin;
import com.mydly.integrations.ExternalTransaction;
import com.mydly.integrations.TransactionSource;
import com.mydly.integrations.internal.openfinance.PluggyModels.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador da porta {@link TransactionSource} para o Open Finance pelo Pluggy (ADR 0005 e ADR 0015).
 * As transações já foram buscadas na API; aqui elas viram o formato comum, e a importação e a
 * reconciliação são as mesmas do extrato (ADR 0014).
 */
final class OpenFinanceSource implements TransactionSource {

    private final List<ExternalTransaction> transactions;

    OpenFinanceSource(List<Transaction> fromPluggy, ZoneId zone) {
        this.transactions = fromPluggy.stream()
                .map(transaction -> toExternal(transaction, zone))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public TransactionOrigin origin() {
        return TransactionOrigin.OPEN_FINANCE;
    }

    @Override
    public List<ExternalTransaction> fetch() {
        return transactions;
    }

    /**
     * Uma transação do Pluggy no formato comum, ou vazio quando ela fica de fora:
     * <ul>
     *   <li>pendente ({@code PENDING}): o id pode mudar quando ela for lançada, e ela voltaria duplicada;</li>
     *   <li>em outra moeda sem o valor na moeda da conta;</li>
     *   <li>valor zero, data ou tipo inválidos.</li>
     * </ul>
     * A direção vem do {@code type}: {@code DEBIT} é saída e {@code CREDIT} é entrada. Uma data à
     * meia-noite UTC é só um dia e vale como está; com horário, vale o dia no fuso do usuário.
     */
    static Optional<ExternalTransaction> toExternal(Transaction transaction, ZoneId zone) {
        if (transaction.id() == null || "PENDING".equals(transaction.status())) {
            return Optional.empty();
        }
        Direction direction = switch (String.valueOf(transaction.type())) {
            case "DEBIT" -> Direction.EXPENSE;
            case "CREDIT" -> Direction.INCOME;
            default -> null;
        };
        BigDecimal amount = transaction.amountInAccountCurrency() != null
                ? transaction.amountInAccountCurrency()
                : "BRL".equals(transaction.currencyCode()) || transaction.currencyCode() == null ? transaction.amount() : null;
        Optional<LocalDate> day = day(transaction.date(), zone);
        if (direction == null || amount == null || amount.signum() == 0 || day.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal value = amount.abs();
        if (value.scale() > 2) {
            value = value.setScale(2, java.math.RoundingMode.HALF_EVEN);
        }
        if (value.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(new ExternalTransaction("pluggy:" + transaction.id(), day.get(), value, direction,
                transaction.description()));
    }

    private static Optional<LocalDate> day(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            if (raw.length() == 10) {
                return Optional.of(LocalDate.parse(raw));
            }
            OffsetDateTime moment = OffsetDateTime.parse(raw);
            boolean dateOnly = moment.getOffset().getTotalSeconds() == 0 && moment.toLocalTime().toSecondOfDay() == 0
                    && moment.getNano() == 0;
            return Optional.of(dateOnly ? moment.toLocalDate() : moment.atZoneSameInstant(zone).toLocalDate());
        } catch (DateTimeParseException invalid) {
            return Optional.empty();
        }
    }
}
