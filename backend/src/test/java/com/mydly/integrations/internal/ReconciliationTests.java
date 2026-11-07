package com.mydly.integrations.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A janela da reconciliação (ADR 0014): o banco pode lançar de um dia antes até três dias depois do
 * que a pessoa escreveu.
 */
class ReconciliationTests {

    private static final LocalDate MANUAL = LocalDate.of(2026, 11, 5);

    @ParameterizedTest(name = "banco em {0}: {1}")
    @CsvSource({
            "2026-11-03, false",
            "2026-11-04, true",
            "2026-11-05, true",
            "2026-11-08, true",
            "2026-11-09, false"})
    void janela(LocalDate bankDay, boolean matches) {
        assertThat(Reconciliation.withinWindow(bankDay, MANUAL)).isEqualTo(matches);
    }

    @Test
    void osLimitesDosLancamentosParaUmDiaDoBanco() {
        LocalDate bank = LocalDate.of(2026, 11, 8);

        assertThat(Reconciliation.firstManualDay(bank)).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(Reconciliation.lastManualDay(bank)).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void venceODiaMaisProximoENoEmpateOPrimeiro() {
        List<LocalDate> manual = List.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 8));

        assertThat(Reconciliation.best(LocalDate.of(2026, 11, 5), true, manual, Function.identity()))
                .contains(LocalDate.of(2026, 11, 4));
    }

    @Test
    void doLadoDoLancamentoAJanelaEAEspelhada() {
        List<LocalDate> bank = List.of(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 7));

        assertThat(Reconciliation.best(MANUAL, false, bank, Function.identity())).contains(LocalDate.of(2026, 11, 7));
        assertThat(Reconciliation.best(MANUAL, false, List.of(LocalDate.of(2026, 11, 3)), Function.identity())).isEmpty();
    }
}
