package com.mydly.entries.internal;

import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Lançamento em texto livre com a sua interpretação. O texto original nunca é descartado,
 * para poder ser reprocessado quando o parser melhorar (ADR 0003).
 *
 * <p>O id pode vir do cliente (ADR 0006); por isso a entidade implementa {@link Persistable}
 * e diz ao Spring Data quando é nova, evitando um {@code SELECT} antes de cada inserção.
 */
@Entity
@Table(name = "entry")
public class Entry implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "detected_type", length = 20)
    private EntryType detectedType;

    /** Lista de itens interpretados; frases compostas vão gerar mais de um (ADR 0003, item 4). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_json")
    private List<ParsedEntry> items;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EntryStatus status;

    @Transient
    private boolean isNew;

    protected Entry() {
        // JPA
    }

    private Entry(UUID id, String rawText, Instant occurredAt, Instant createdAt, List<ParsedEntry> items) {
        this.id = id;
        this.rawText = rawText;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.items = List.copyOf(items);
        this.detectedType = commonType(items);
        this.confidence = lowestConfidence(items);
        this.status = items.stream().anyMatch(ParsedEntry::needsConfirmation)
                ? EntryStatus.NEEDS_INPUT
                : EntryStatus.PENDING;
        this.isNew = true;
    }

    static Entry interpreted(UUID id, String rawText, Instant occurredAt, Instant createdAt,
                             List<ParsedEntry> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("um lançamento precisa de ao menos um item interpretado");
        }
        return new Entry(id, rawText, occurredAt, createdAt, items);
    }

    /** O tipo comum a todos os itens, ou {@code null} se forem mistos ou desconhecidos. */
    private static EntryType commonType(List<ParsedEntry> items) {
        EntryType first = items.getFirst().type();
        boolean allSame = items.stream().allMatch(item -> item.type() == first);
        return allSame && first != EntryType.UNKNOWN ? first : null;
    }

    /** O lançamento vale o que vale o seu item menos confiável. */
    private static BigDecimal lowestConfidence(List<ParsedEntry> items) {
        double lowest = items.stream().map(ParsedEntry::confidence).min(Comparator.naturalOrder()).orElseThrow();
        return BigDecimal.valueOf(lowest).setScale(2, RoundingMode.HALF_UP);
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getRawText() {
        return rawText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public EntryType getDetectedType() {
        return detectedType;
    }

    public List<ParsedEntry> getItems() {
        return items;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public EntryStatus getStatus() {
        return status;
    }
}
