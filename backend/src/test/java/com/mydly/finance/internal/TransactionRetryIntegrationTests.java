package com.mydly.finance.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.IncompleteEventsRetry;
import com.mydly.TestcontainersConfiguration;
import com.mydly.entries.EntryItemAccepted;
import com.mydly.finance.FinanceTotals;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Consumidor que falha na primeira entrega (ADR 0008): o lançamento fica gravado, o total fica sem
 * ele, a saúde acusa o evento parado e a nova tentativa agendada acerta tudo sem duplicar.
 *
 * <p>Os prazos ficam em zero para o teste não esperar; o agendamento automático fica longe (1 hora)
 * e o teste chama a nova tentativa direto.
 */
@SpringBootTest(properties = {
        "mydly.events.retry-older-than=PT0S",
        "mydly.events.stale-after=PT0S",
        "mydly.events.retry-interval=PT1H"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionRetryIntegrationTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DAY = LocalDate.of(2035, 3, 10);

    @MockitoSpyBean
    private TransactionRecorder recorder;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FinanceTotals totals;

    @Autowired
    private IncompleteEventsRetry retry;

    @Autowired
    @Qualifier("incompleteEvents")
    private HealthIndicator health;

    @Test
    void consumidorQueFalhaUmaVezEntraNosTotaisNaNovaTentativa() throws Exception {
        // O espião fica atrás do proxy de transação e do registro de eventos; o stub vai nele direto.
        TransactionRecorder spy = AopTestUtils.getUltimateTargetObject(recorder);
        doThrow(new IllegalStateException("falha simulada do banco")).doCallRealMethod()
                .when(spy).on(any(EntryItemAccepted.class));

        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id": "%s", "text": "Gastei 50 reais na farmácia", "occurredAt": "%sT15:00:00Z"}"""
                        .formatted(UUID.randomUUID(), DAY)))
                .andExpect(status().isCreated());

        assertThat(totals.on(DAY, ZONE).spent()).as("o consumidor falhou").isZero();
        assertThat(health.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);

        retry.retry();

        assertThat(totals.on(DAY, ZONE).spent()).isEqualByComparingTo("50");
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);

        retry.retry();

        assertThat(totals.on(DAY, ZONE).spent()).as("repetir não duplica").isEqualByComparingTo("50");
    }
}
