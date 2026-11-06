package com.mydly.entries.internal;

import com.mydly.entries.EntryParser;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
