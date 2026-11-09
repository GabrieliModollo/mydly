package com.mydly.entries.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EntryRepository extends JpaRepository<Entry, UUID> {

    /** O lançamento, só se for do usuário: o de outro usuário é tratado como inexistente. */
    Optional<Entry> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Lançamentos do usuário com {@code occurredAt} a partir de start (inclusive) e antes de end, do mais
     * recente ao mais antigo.
     */
    List<Entry> findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(UUID userId,
            Instant start, Instant end);

    /** Os lançamentos do usuário ainda com algum item para confirmar, escritos antes de {@code before}. */
    List<Entry> findTop50ByUserIdAndStatusAndOccurredAtLessThanOrderByOccurredAtDesc(UUID userId, EntryStatus status,
            Instant before);
}
