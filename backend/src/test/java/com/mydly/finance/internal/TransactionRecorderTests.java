package com.mydly.finance.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.TestcontainersConfiguration;
import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import com.mydly.entries.EntryItemAccepted;
import com.mydly.entries.EntryItemRetracted;
import com.mydly.entries.ParsedEntry;
import com.mydly.finance.FinanceTotals;
import com.mydly.finance.PeriodTotals;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

/**
 * Consumidor de eventos do módulo finance (ADR 0008): idempotente pela revisão e com lápides.
 * Os eventos podem chegar repetidos ou fora de ordem quando o registro de publicação reenvia.
 */
// O módulo usa a API pública de preferences (dia de hoje e semana), que entra junto (ADR 0010).
@ApplicationModuleTest(extraIncludes = "preferences")
@Import(TestcontainersConfiguration.class)
class TransactionRecorderTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    /** Cada teste usa um dia só seu, para os totais não se misturarem. */
    private static final AtomicInteger DAYS = new AtomicInteger();

    @Autowired
    private TransactionRecorder recorder;

    @Autowired
    private FinanceTotals totals;

    private LocalDate day;
    private Instant noon;
    private UUID entryId;

    /** Depois de 2019 o Brasil não tem horário de verão, então São Paulo é sempre UTC-3. */
    @BeforeEach
    void freshDayAndEntry() {
        day = LocalDate.of(2031, 1, 1).plusDays(DAYS.incrementAndGet());
        noon = day.atTime(12, 0).atZone(ZONE).toInstant();
        entryId = UUID.randomUUID();
    }

    @Test
    void gastoAceitoApareceNosTotaisDoDiaPorEvento(Scenario scenario) {
        scenario.publish(accepted(1, "50", Category.PHARMACY))
                .andWaitForStateChange(() -> spent(), total -> total.signum() > 0)
                .andVerify(total -> assertThat(total).isEqualByComparingTo("50"));

        PeriodTotals day = totals.on(this.day, ZONE);
        assertThat(day.spentByCategory()).containsExactly(
                new PeriodTotals.CategoryTotal(Category.PHARMACY, new BigDecimal("50.00")));
    }

    @Test
    void eventoRepetidoNaoDuplica() {
        recorder.on(accepted(1, "50", null));
        recorder.on(accepted(1, "50", null));

        assertThat(spent()).isEqualByComparingTo("50");
    }

    @Test
    void retiradaQueChegaAntesDaAceitacaoDeixaOItemDeFora() {
        recorder.on(new EntryItemRetracted(entryId, 0, 2));
        recorder.on(accepted(1, "50", null));

        assertThat(spent()).isZero();
    }

    @Test
    void aceitacaoAntigaAtrasadaNaoRessuscitaOItemRetirado() {
        recorder.on(accepted(1, "50", null));
        recorder.on(new EntryItemRetracted(entryId, 0, 2));
        recorder.on(accepted(1, "50", null));

        assertThat(spent()).isZero();
    }

    @Test
    void itemReaceitoVoltaAContarComOValorNovo() {
        recorder.on(accepted(1, "50", null));
        recorder.on(new EntryItemRetracted(entryId, 0, 2));
        recorder.on(accepted(3, "35", null));

        assertThat(spent()).isEqualByComparingTo("35");
    }

    @Test
    void entradaContaSeparadaDoGasto() {
        recorder.on(new EntryItemAccepted(entryId, 0, 1, noon,
                ParsedEntry.finance(Direction.INCOME, new BigDecimal("100"), "Recebi 100 reais", 0.95)));

        PeriodTotals day = totals.on(this.day, ZONE);
        assertThat(day.received()).isEqualByComparingTo("100");
        assertThat(day.spent()).isZero();
    }

    @Test
    void itemQueDeixouDeSerFinancaSaiDosTotais() {
        recorder.on(accepted(1, "50", null));
        recorder.on(new EntryItemAccepted(entryId, 0, 3, noon,
                ParsedEntry.routine(null, null, "Fui à academia", 1.0)));

        assertThat(spent()).isZero();
    }

    @Test
    void diaSegueOFusoDeSaoPaulo() {
        // 02:30Z do dia seguinte ainda são 23:30 deste dia em São Paulo.
        Instant lateNight = day.plusDays(1).atStartOfDay(ZoneId.of("UTC")).plusMinutes(150).toInstant();
        recorder.on(new EntryItemAccepted(entryId, 0, 1, lateNight,
                ParsedEntry.finance(Direction.EXPENSE, BigDecimal.TEN, "x", 0.95)));

        assertThat(spent()).isEqualByComparingTo("10");
        assertThat(totals.on(day.plusDays(1), ZONE).spent()).isZero();
    }

    private EntryItemAccepted accepted(long revision, String amount, Category category) {
        return new EntryItemAccepted(entryId, 0, revision, noon,
                ParsedEntry.finance(Direction.EXPENSE, new BigDecimal(amount), category, "Gastei " + amount, 0.95));
    }

    private BigDecimal spent() {
        return totals.on(day, ZONE).spent();
    }
}
