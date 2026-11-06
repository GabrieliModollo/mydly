package com.mydly.entries.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EntryRepository extends JpaRepository<Entry, UUID> {

    /** Lançamentos com {@code occurredAt} a partir de start (inclusive) e antes de end, do mais recente ao mais antigo. */
    List<Entry> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(Instant start, Instant end);
}
