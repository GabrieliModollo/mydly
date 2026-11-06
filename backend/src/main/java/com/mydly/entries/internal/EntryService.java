package com.mydly.entries.internal;

import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import com.mydly.entries.EntryParser;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.RoutineOutcome;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recebe lançamentos, interpreta com o {@link EntryParser} e guarda o resultado.
 *
 * <p>A criação é idempotente pelo id (ADR 0006): o app offline pode reenviar o mesmo
 * lançamento depois de uma falha de rede sem duplicá-lo. Por privacidade (LGPD), nada
 * aqui registra em log o texto nem os valores.
 */
@Service
public class EntryService {

    /** Fuso usado para definir "o dia" de um lançamento até existir fuso por usuário. */
    public static final ZoneId USER_ZONE = ZoneId.of("America/Sao_Paulo");

    private final EntryRepository repository;
    private final EntryParser parser;
    private final Clock clock;

    EntryService(EntryRepository repository, EntryParser parser, Clock clock) {
        this.repository = repository;
        this.parser = parser;
        this.clock = clock;
    }

    /** O que aconteceu com um pedido de criação. */
    public enum Outcome {
        /** Lançamento novo, gravado agora. */
        CREATED,
        /** Reenvio do mesmo lançamento: o registro existente é devolvido sem reprocessar. */
        ALREADY_EXISTS,
        /** O id já existe com outro conteúdo; nada foi alterado. */
        CONFLICT
    }

    /** Resultado da criação: o desfecho e o lançamento gravado (o novo ou o que já existia). */
    public record CreateResult(Outcome outcome, Entry entry) {
    }

    /**
     * Cria o lançamento ou, se o id já existir, decide entre reenvio e conflito.
     *
     * <p>É reenvio quando o texto é igual (ignorando espaços extras) e, se o cliente informou
     * {@code occurredAt}, a data também é igual. Qualquer outra diferença é conflito: editar
     * um lançamento será uma operação própria.
     *
     * @param id         id gerado pelo cliente, ou {@code null} para o servidor gerar
     * @param text       texto do lançamento, guardado como veio
     * @param occurredAt quando o usuário escreveu, ou {@code null} para usar o momento atual
     */
    public CreateResult create(UUID id, String text, Instant occurredAt) {
        UUID entryId = id != null ? id : UUID.randomUUID();
        Instant requestedOccurredAt = toDatabasePrecision(occurredAt);
        Optional<Entry> existing = repository.findById(entryId);
        if (existing.isPresent()) {
            return compareWithExisting(existing.get(), text, requestedOccurredAt);
        }

        Instant now = toDatabasePrecision(clock.instant());
        Entry entry = Entry.interpreted(entryId, text, requestedOccurredAt != null ? requestedOccurredAt : now,
                now, parser.parse(text));
        try {
            return new CreateResult(Outcome.CREATED, repository.saveAndFlush(entry));
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Dois envios simultâneos com o mesmo id: o outro gravou primeiro, então comparamos com o dele.
            return repository.findById(entryId)
                    .map(saved -> compareWithExisting(saved, text, requestedOccurredAt))
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Entry> find(UUID id) {
        return repository.findById(id);
    }

    /**
     * Resposta do usuário para um item que precisava de confirmação.
     *
     * @param action    confirmar como finança, como rotina, ou descartar
     * @param amount    valor, obrigatório e positivo em {@link ResolutionAction#CONFIRM_FINANCE}
     * @param direction saída ou entrada; saída quando omitida
     * @param category  categoria escolhida; opcional
     * @param quantity  quantidade da rotina; opcional e positiva
     * @param unit      unidade da quantidade, uma de {@link ParsedEntry#UNITS}
     * @param outcome   se o hábito foi cumprido; cumprido quando omitido
     */
    public record ResolveCommand(ResolutionAction action, BigDecimal amount, Direction direction, Category category,
                                 BigDecimal quantity, String unit, RoutineOutcome outcome) {
    }

    /**
     * Aplica a resposta do usuário ao item {@code index} do lançamento, desde que ele ainda esteja
     * na versão que o cliente leu. O que o parser entendeu não é alterado (ADR 0007).
     *
     * @return o lançamento atualizado, já com a versão nova
     * @throws EntryResolutionException quando o lançamento ou o item não existem, a versão mudou,
     *                                  o item não está pendente ou os dados são inválidos
     */
    @Transactional
    public Entry resolve(UUID id, int index, long expectedVersion, ResolveCommand command) {
        Entry entry = repository.findById(id).orElseThrow(() -> new EntryResolutionException(
                EntryResolutionException.Reason.ENTRY_NOT_FOUND, "Lançamento não encontrado."));
        if (entry.getVersion() != expectedVersion) {
            throw versionMismatch();
        }
        entry.resolve(index, toResolution(command, entry.itemText(index)));
        try {
            return repository.saveAndFlush(entry);
        } catch (ObjectOptimisticLockingFailureException concurrentChange) {
            throw versionMismatch();
        }
    }

    private static EntryResolutionException versionMismatch() {
        return new EntryResolutionException(EntryResolutionException.Reason.VERSION_MISMATCH,
                "O lançamento mudou desde a última leitura. Carregue de novo e tente outra vez.");
    }

    private static ItemResolution toResolution(ResolveCommand command, String description) {
        try {
            return switch (command.action()) {
                case DISMISS -> ItemResolution.dismissed();
                case CONFIRM_FINANCE -> {
                    if (command.amount() == null || command.amount().signum() <= 0) {
                        throw invalid("Informe um valor maior que zero.");
                    }
                    if (command.amount().stripTrailingZeros().scale() > 2) {
                        throw invalid("O valor aceita no máximo duas casas decimais.");
                    }
                    Direction direction = command.direction() != null ? command.direction() : Direction.EXPENSE;
                    yield ItemResolution.confirmed(ParsedEntry.finance(direction, command.amount(), command.category(),
                            description, ParsedEntry.CONFIRMED_BY_USER));
                }
                case CONFIRM_ROUTINE -> {
                    if (command.quantity() != null && command.quantity().signum() <= 0) {
                        throw invalid("A quantidade precisa ser maior que zero.");
                    }
                    RoutineOutcome outcome = command.outcome() != null ? command.outcome() : RoutineOutcome.DONE;
                    yield ItemResolution.confirmed(ParsedEntry.routine(outcome, command.quantity(), command.unit(),
                            description, ParsedEntry.CONFIRMED_BY_USER));
                }
            };
        } catch (IllegalArgumentException invalidItem) {
            // Unidade sem quantidade, unidade desconhecida e afins, vindos das regras do ParsedEntry.
            throw invalid(invalidItem.getMessage());
        }
    }

    private static EntryResolutionException invalid(String message) {
        return new EntryResolutionException(EntryResolutionException.Reason.INVALID, message);
    }

    /**
     * Lançamentos de um dia no fuso do usuário, pela data em que foram escritos, do mais recente
     * para o mais antigo. Enquanto não há usuários (e fuso por usuário), vale {@link #USER_ZONE}.
     *
     * @param day o dia, ou {@code null} para hoje
     */
    @Transactional(readOnly = true)
    public List<Entry> listByDay(LocalDate day) {
        LocalDate date = day != null ? day : LocalDate.now(clock.withZone(USER_ZONE));
        Instant start = date.atStartOfDay(USER_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(USER_ZONE).toInstant();
        return repository.findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(start, end);
    }

    private static CreateResult compareWithExisting(Entry existing, String text, Instant occurredAt) {
        boolean sameText = normalizeSpaces(existing.getRawText()).equals(normalizeSpaces(text));
        boolean sameOccurredAt = occurredAt == null || Objects.equals(existing.getOccurredAt(), occurredAt);
        return new CreateResult(sameText && sameOccurredAt ? Outcome.ALREADY_EXISTS : Outcome.CONFLICT, existing);
    }

    private static String normalizeSpaces(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    /** O PostgreSQL guarda microssegundos; truncar evita que a mesma data pareça diferente depois de gravada. */
    private static Instant toDatabasePrecision(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }
}
