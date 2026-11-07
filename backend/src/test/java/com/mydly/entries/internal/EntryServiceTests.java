package com.mydly.entries.internal;

import static com.mydly.TestUsers.DEMO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import com.mydly.entries.RoutineOutcome;
import com.mydly.entries.EntryParser;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.internal.EntryService.Outcome;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Casos do serviço que o teste de integração não alcança de forma determinística.
 */
class EntryServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00.123456789Z");
    private static final Instant NOW_IN_MICROS = Instant.parse("2026-11-05T12:00:00.123456Z");

    private final EntryRepository repository = mock(EntryRepository.class);
    private final EntryParser parser = mock(EntryParser.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final EntryService service = new EntryService(repository, parser, Clock.fixed(NOW, ZoneOffset.UTC),
            events, mock(PlatformTransactionManager.class));

    private final UUID id = UUID.randomUUID();
    private final Entry saved = Entry.interpreted(DEMO, id, "Gastei 50 reais", NOW_IN_MICROS, NOW_IN_MICROS,
            List.of(ParsedEntry.finance(Direction.EXPENSE, BigDecimal.valueOf(50), "Gastei 50 reais", 0.95)));

    @Test
    void envioSimultaneoDoMesmoLancamentoDevolveOQueGravouPrimeiro() {
        simulateConcurrentInsert();

        EntryService.CreateResult result = service.create(DEMO, id, "Gastei 50 reais", null);

        assertThat(result.outcome()).isEqualTo(Outcome.ALREADY_EXISTS);
        assertThat(result.entry()).isSameAs(saved);
    }

    @Test
    void envioSimultaneoComOutroTextoEConflito() {
        simulateConcurrentInsert();

        assertThat(service.create(DEMO, id, "Gastei 60 reais", null).outcome()).isEqualTo(Outcome.CONFLICT);
    }

    @Test
    void outraViolacaoDeIntegridadeEPropagada() {
        when(parser.parse(any())).thenReturn(saved.getItems());
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("outra restrição"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> service.create(DEMO, id, "Gastei 50 reais", null));
    }

    @Test
    void semDataUsaORelogioDoServidorNaPrecisaoDoBanco() {
        when(parser.parse(any())).thenReturn(saved.getItems());
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EntryService.CreateResult result = service.create(DEMO, id, "Gastei 50 reais", null);

        assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(result.entry().getOccurredAt()).isEqualTo(NOW_IN_MICROS);
        assertThat(result.entry().getCreatedAt()).isEqualTo(NOW_IN_MICROS);
    }

    @Test
    void dataComNanossegundosNoReenvioNaoViraConflito() {
        when(repository.findById(id)).thenReturn(Optional.of(saved));

        assertThat(service.create(DEMO, id, "Gastei 50 reais", NOW).outcome()).isEqualTo(Outcome.ALREADY_EXISTS);
    }

    @Test
    void hojeEODiaDeSaoPauloMesmoQuandoEmUtcJaEAmanha() {
        // 02:00Z do dia 6 ainda são 23:00 do dia 5 em São Paulo.
        Clock lateNight = Clock.fixed(Instant.parse("2026-11-06T02:00:00Z"), ZoneOffset.UTC);
        EntryService lateService = new EntryService(repository, parser, lateNight, events,
                mock(PlatformTransactionManager.class));

        lateService.listByDay(DEMO, null);

        verify(repository).findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(DEMO,
                Instant.parse("2026-11-05T03:00:00Z"), Instant.parse("2026-11-06T03:00:00Z"));
    }

    private static final EntryService.ResolveCommand DISMISS =
            new EntryService.ResolveCommand(ResolutionAction.DISMISS, null, null, null, null, null, null);

    private Entry pendingEntry() {
        return Entry.interpreted(DEMO, id, "Comprei  pão ", NOW_IN_MICROS, NOW_IN_MICROS,
                List.of(ParsedEntry.finance(Direction.EXPENSE, null, "Comprei pão", 0.4)));
    }

    @Test
    void resolverLancamentoInexistenteOuDeOutraVersaoFalha() {
        when(repository.findByIdAndUserId(id, DEMO)).thenReturn(Optional.empty());
        assertThat(resolveReason(0, DISMISS)).isEqualTo(EntryResolutionException.Reason.ENTRY_NOT_FOUND);

        when(repository.findByIdAndUserId(id, DEMO)).thenReturn(Optional.of(pendingEntry()));
        assertThat(resolveReason(7, DISMISS)).isEqualTo(EntryResolutionException.Reason.VERSION_MISMATCH);
    }

    @Test
    void alteracaoConcorrenteNoMomentoDeGravarViraVersaoDesatualizada() {
        when(repository.findByIdAndUserId(id, DEMO)).thenReturn(Optional.of(pendingEntry()));
        when(repository.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(Entry.class, id));

        assertThat(resolveReason(0, DISMISS)).isEqualTo(EntryResolutionException.Reason.VERSION_MISMATCH);
    }

    @Test
    void gastoConfirmadoESaidaPorPadraoComODescricaoDoTexto() {
        when(repository.findByIdAndUserId(id, DEMO)).thenReturn(Optional.of(pendingEntry()));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Entry entry = service.resolve(DEMO, id, 0, 0, new EntryService.ResolveCommand(ResolutionAction.CONFIRM_FINANCE,
                new BigDecimal("8.50"), null, Category.FOOD, null, null, null));

        ParsedEntry item = entry.effectiveItems().getFirst().item();
        assertThat(item.direction()).isEqualTo(Direction.EXPENSE);
        assertThat(item.category()).isEqualTo(Category.FOOD);
        assertThat(item.description()).isEqualTo("Comprei pão");
        assertThat(item.confidence()).isEqualTo(ParsedEntry.CONFIRMED_BY_USER);
    }

    @Test
    void rotinaConfirmadaECumpridaPorPadrao() {
        when(repository.findByIdAndUserId(id, DEMO)).thenReturn(Optional.of(pendingEntry()));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Entry entry = service.resolve(DEMO, id, 0, 0,
                new EntryService.ResolveCommand(ResolutionAction.CONFIRM_ROUTINE, null, null, null, null, null, null));

        assertThat(entry.effectiveItems().getFirst().item().outcome()).isEqualTo(RoutineOutcome.DONE);
    }

    @Test
    void rotinaPodeSerConfirmadaComoNaoCumprida() {
        when(repository.findByIdAndUserId(id, DEMO)).thenReturn(Optional.of(pendingEntry()));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Entry entry = service.resolve(DEMO, id, 0, 0, new EntryService.ResolveCommand(ResolutionAction.CONFIRM_ROUTINE,
                null, null, null, null, null, RoutineOutcome.MISSED));

        assertThat(entry.effectiveItems().getFirst().item().outcome()).isEqualTo(RoutineOutcome.MISSED);
    }

    private EntryResolutionException.Reason resolveReason(long version, EntryService.ResolveCommand command) {
        try {
            service.resolve(DEMO, id, 0, version, command);
        } catch (EntryResolutionException e) {
            return e.reason();
        }
        throw new AssertionError("esperava EntryResolutionException");
    }

    private void simulateConcurrentInsert() {
        when(parser.parse(any())).thenReturn(saved.getItems());
        when(repository.findById(id)).thenReturn(Optional.empty(), Optional.of(saved));
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("pk duplicada"));
    }
}
