package com.mydly.entries.internal;

import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.internal.EntryResolutionException.Reason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Lançamento em texto livre com a sua interpretação. O texto original nunca é descartado,
 * para poder ser reprocessado quando o parser melhorar (ADR 0003).
 *
 * <p>O que o parser entendeu ({@code parsed_json}) e o que o usuário confirmou ({@code resolution})
 * ficam separados (ADR 0007): o primeiro nunca é sobrescrito, e o valor efetivo de cada item é a
 * resolução, quando existe.
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

    /** O que o parser entendeu; frases compostas vão gerar mais de um item (ADR 0003, item 4). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_json")
    private List<ParsedEntry> items;

    /** Respostas do usuário, alinhadas com {@link #items}; posição {@code null} = não resolvido. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolution")
    private List<ItemResolution> resolutions;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EntryStatus status;

    /** Controle de concorrência otimista; vira o ETag da API. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    /** Cada item como vale agora: a resolução do usuário, se houver, senão o do parser. */
    public List<EffectiveItem> effectiveItems() {
        return IntStream.range(0, items.size()).mapToObj(this::effectiveItem).toList();
    }

    private EffectiveItem effectiveItem(int index) {
        ParsedEntry parsed = items.get(index);
        ItemResolution resolution = resolutionAt(index);
        if (resolution == null) {
            return new EffectiveItem(parsed, false, false, parsed.needsConfirmation());
        }
        if (resolution.action() == ResolutionAction.DISMISS) {
            return new EffectiveItem(parsed, true, true, false);
        }
        return new EffectiveItem(resolution.item(), true, false, false);
    }

    private ItemResolution resolutionAt(int index) {
        return resolutions != null && index < resolutions.size() ? resolutions.get(index) : null;
    }

    /**
     * Aplica a resposta do usuário a um item que precisava de confirmação. Quando não sobra
     * nenhum item pendente, o lançamento sai de {@code NEEDS_INPUT} e volta a {@code PENDING}.
     */
    void resolve(int index, ItemResolution resolution) {
        if (index < 0 || index >= items.size()) {
            throw new EntryResolutionException(Reason.ITEM_NOT_FOUND, "O lançamento não tem o item " + index + ".");
        }
        if (!effectiveItem(index).needsConfirmation()) {
            throw new EntryResolutionException(Reason.NOT_PENDING,
                    "Este item já foi resolvido ou não precisa de confirmação.");
        }
        List<ItemResolution> updated = new ArrayList<>(Collections.nCopies(items.size(), (ItemResolution) null));
        for (int i = 0; i < items.size(); i++) {
            updated.set(i, resolutionAt(i));
        }
        updated.set(index, resolution);
        // Uma lista nova faz o Hibernate perceber a mudança na coluna JSON.
        this.resolutions = Collections.unmodifiableList(updated);
        if (status == EntryStatus.NEEDS_INPUT && effectiveItems().stream().noneMatch(EffectiveItem::needsConfirmation)) {
            status = EntryStatus.PENDING;
        }
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

    /** O que o parser entendeu, sem as correções do usuário. Use {@link #effectiveItems()} para o valor atual. */
    public List<ParsedEntry> getItems() {
        return items;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public EntryStatus getStatus() {
        return status;
    }

    /** Versão atual; {@code 0} logo após a criação. */
    public long getVersion() {
        return version == null ? 0 : version;
    }
}
