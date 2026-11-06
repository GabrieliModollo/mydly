package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mydly.entries.Direction;
import com.mydly.entries.EntryParser;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.internal.EntryService.Outcome;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Casos do serviço que o teste de integração não alcança de forma determinística.
 */
class EntryServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00.123456789Z");
    private static final Instant NOW_IN_MICROS = Instant.parse("2026-11-05T12:00:00.123456Z");

    private final EntryRepository repository = mock(EntryRepository.class);
    private final EntryParser parser = mock(EntryParser.class);
    private final EntryService service = new EntryService(repository, parser, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID id = UUID.randomUUID();
    private final Entry saved = Entry.interpreted(id, "Gastei 50 reais", NOW_IN_MICROS, NOW_IN_MICROS,
            List.of(ParsedEntry.finance(Direction.EXPENSE, BigDecimal.valueOf(50), "Gastei 50 reais", 0.95)));

    @Test
    void envioSimultaneoDoMesmoLancamentoDevolveOQueGravouPrimeiro() {
        simulateConcurrentInsert();

        EntryService.CreateResult result = service.create(id, "Gastei 50 reais", null);

        assertThat(result.outcome()).isEqualTo(Outcome.ALREADY_EXISTS);
        assertThat(result.entry()).isSameAs(saved);
    }

    @Test
    void envioSimultaneoComOutroTextoEConflito() {
        simulateConcurrentInsert();

        assertThat(service.create(id, "Gastei 60 reais", null).outcome()).isEqualTo(Outcome.CONFLICT);
    }

    @Test
    void outraViolacaoDeIntegridadeEPropagada() {
        when(parser.parse(any())).thenReturn(saved.getItems());
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("outra restrição"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> service.create(id, "Gastei 50 reais", null));
    }

    @Test
    void semDataUsaORelogioDoServidorNaPrecisaoDoBanco() {
        when(parser.parse(any())).thenReturn(saved.getItems());
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EntryService.CreateResult result = service.create(id, "Gastei 50 reais", null);

        assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(result.entry().getOccurredAt()).isEqualTo(NOW_IN_MICROS);
        assertThat(result.entry().getCreatedAt()).isEqualTo(NOW_IN_MICROS);
    }

    @Test
    void dataComNanossegundosNoReenvioNaoViraConflito() {
        when(repository.findById(id)).thenReturn(Optional.of(saved));

        assertThat(service.create(id, "Gastei 50 reais", NOW).outcome()).isEqualTo(Outcome.ALREADY_EXISTS);
    }

    private void simulateConcurrentInsert() {
        when(parser.parse(any())).thenReturn(saved.getItems());
        when(repository.findById(id)).thenReturn(Optional.empty(), Optional.of(saved));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("pk duplicada"));
    }
}
