package com.mydly.integrations.internal.openfinance;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.Direction;
import com.mydly.finance.TransactionOrigin;
import com.mydly.integrations.ExternalTransaction;
import com.mydly.integrations.internal.openfinance.PluggyModels.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Transações do Pluggy no formato comum da importação (ADR 0015). Cada linha dos casos inválidos é uma
 * transação que fica de fora.
 */
class OpenFinanceSourceTests {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void debitoESaidaECreditoEEntrada() {
        ExternalTransaction expense = map(transaction("t1", "2026-11-05T00:00:00.000Z", "DEBIT", "-50.00")).orElseThrow();
        ExternalTransaction income = map(transaction("t2", "2026-11-05T00:00:00.000Z", "CREDIT", "3000")).orElseThrow();

        assertThat(expense).isEqualTo(new ExternalTransaction("pluggy:t1", LocalDate.of(2026, 11, 5),
                new BigDecimal("50.00"), Direction.EXPENSE, "DROGASIL"));
        assertThat(income.direction()).isEqualTo(Direction.INCOME);
        assertThat(income.amount()).isEqualByComparingTo("3000");
    }

    @Test
    void cartaoComCompraPositivaSegueOTipo() {
        assertThat(map(transaction("t1", "2026-11-05T00:00:00.000Z", "DEBIT", "42.90")).orElseThrow().direction())
                .isEqualTo(Direction.EXPENSE);
    }

    @ParameterizedTest(name = "{0} vira {1}")
    @CsvSource({
            "2026-11-05T00:00:00.000Z, 2026-11-05",
            "2026-11-05, 2026-11-05",
            "2026-11-06T01:30:00.000Z, 2026-11-05",
            "2026-11-05T15:00:00-03:00, 2026-11-05"})
    void diaDaTransacao(String date, LocalDate expected) {
        assertThat(map(transaction("t", date, "DEBIT", "-1")).orElseThrow().date()).isEqualTo(expected);
    }

    @Test
    void outraMoedaUsaOValorNaMoedaDaConta() {
        Transaction dollars = new Transaction("t", "a", "2026-11-05T00:00:00.000Z", "Loja", "DEBIT",
                new BigDecimal("-10"), new BigDecimal("-55.123"), "USD", "POSTED");

        assertThat(map(dollars).orElseThrow().amount()).isEqualByComparingTo("55.12");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(nullValues = "NADA", value = {
            "pendente, 2026-11-05T00:00:00.000Z, DEBIT, -10, BRL, PENDING",
            "valor zero, 2026-11-05T00:00:00.000Z, DEBIT, 0, BRL, POSTED",
            "tipo desconhecido, 2026-11-05T00:00:00.000Z, OTHER, -10, BRL, POSTED",
            "outra moeda sem conversão, 2026-11-05T00:00:00.000Z, DEBIT, -10, USD, POSTED",
            "data inválida, ontem, DEBIT, -10, BRL, POSTED",
            "sem data, NADA, DEBIT, -10, BRL, POSTED"})
    void ficaDeFora(String caso, String date, String type, BigDecimal amount, String currency, String status) {
        assertThat(map(new Transaction("t", "a", date, "x", type, amount, null, currency, status))).isEmpty();
    }

    @Test
    void aFonteEOpenFinance() {
        OpenFinanceSource source = new OpenFinanceSource(List.of(
                transaction("t1", "2026-11-05T00:00:00.000Z", "DEBIT", "-50"),
                new Transaction("t2", "a", "2026-11-05T00:00:00.000Z", "x", "DEBIT", BigDecimal.TEN, null, "BRL",
                        "PENDING")), SAO_PAULO);

        assertThat(source.origin()).isEqualTo(TransactionOrigin.OPEN_FINANCE);
        assertThat(source.fetch()).extracting(ExternalTransaction::externalId).containsExactly("pluggy:t1");
    }

    private static Optional<ExternalTransaction> map(Transaction transaction) {
        return OpenFinanceSource.toExternal(transaction, SAO_PAULO);
    }

    private static Transaction transaction(String id, String date, String type, String amount) {
        return new Transaction(id, "conta", date, "DROGASIL", type, new BigDecimal(amount), null, "BRL", "POSTED");
    }
}
