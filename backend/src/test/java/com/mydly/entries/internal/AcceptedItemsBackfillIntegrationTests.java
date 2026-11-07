package com.mydly.entries.internal;

import static com.mydly.TestUsers.DEMO;
import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.TestcontainersConfiguration;
import com.mydly.finance.FinanceTotals;
import com.mydly.routines.HabitOutcomes;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Backfill da etapa 5: os lançamentos gravados antes dos módulos finance e routines entram nos
 * totais, e publicar de novo não duplica nada (ADR 0008).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AcceptedItemsBackfillIntegrationTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DAY = LocalDate.of(2034, 5, 10);

    @Autowired
    private AcceptedItemsBackfill backfill;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private FinanceTotals finance;

    @Autowired
    private HabitOutcomes habits;

    @Test
    void publicaOsItensAceitosDosLancamentosAntigosSemDuplicar() {
        // Como um lançamento antigo, gravado direto no banco: um gasto aceito, uma rotina aceita
        // e um gasto descartado pelo usuário, que não pode entrar.
        jdbc.update("""
                INSERT INTO entry (user_id, id, raw_text, occurred_at, parsed_json, resolution, item_state, confidence, status)
                VALUES ('d3d3d3d3-0000-4000-8000-000000000001', ?, 'Gastei 30 no açaí e fui à academia e comprei pão', ?::timestamptz, ?::jsonb, ?::jsonb,
                        ?::jsonb, 0.40, 'CONFIRMED')""",
                UUID.randomUUID(), DAY + "T15:00:00Z", """
                [{"type": "FINANCE", "direction": "EXPENSE", "amount": 30, "confidence": 0.7, "description": "Gastei 30 no açaí"},
                 {"type": "ROUTINE", "outcome": "DONE", "confidence": 0.6, "description": "fui à academia"},
                 {"type": "FINANCE", "direction": "EXPENSE", "amount": null, "confidence": 0.4, "description": "comprei pão"}]""",
                "[null, null, {\"action\": \"DISMISS\", \"item\": null}]", """
                [{"state": "ACCEPTED_AUTO", "revision": 1, "parserRejected": false},
                 {"state": "ACCEPTED_AUTO", "revision": 1, "parserRejected": false},
                 {"state": "DISMISSED", "revision": 2, "parserRejected": false}]""");

        transactions.executeWithoutResult(status -> backfill.publishAll());
        transactions.executeWithoutResult(status -> backfill.publishAll());

        assertThat(finance.on(DEMO, DAY, ZONE).spent()).isEqualByComparingTo("30");
        assertThat(habits.on(DEMO, DAY, ZONE).achieved()).isEqualTo(1);
    }

    @Test
    void aTarefaFicaMarcadaComoFeitaAoSubir() {
        Long done = jdbc.queryForObject("SELECT count(*) FROM entry_one_time_task WHERE name = ?", Long.class,
                AcceptedItemsBackfill.TASK);

        assertThat(done).isEqualTo(1);
    }
}
