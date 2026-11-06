package com.mydly.entries.internal;

import com.mydly.entries.EntryItemAccepted;
import com.mydly.entries.EntryItemRetracted;
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
import java.util.Objects;
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
 * <p>Cada item tem um estado e uma revisão ({@code item_state}, ADR 0008). Quando um item passa a
 * valer ou deixa de valer, a entidade guarda um evento ({@link EntryItemAccepted} ou
 * {@link EntryItemRetracted}) que o serviço publica na mesma transação da gravação.
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

    /** O que o parser entendeu; uma frase composta gera mais de um item (ADR 0003, item 4). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_json")
    private List<ParsedEntry> items;

    /** Respostas do usuário, alinhadas com {@link #items}; posição {@code null} = sem resposta. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolution")
    private List<ItemResolution> resolutions;

    /** Estado e revisão de cada item, alinhados com {@link #items} (ADR 0008). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_state")
    private List<ItemStatus> statuses;

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

    /** Eventos ainda não publicados; o serviço os publica depois de gravar. */
    @Transient
    private final List<Object> events = new ArrayList<>();

    protected Entry() {
        // JPA
    }

    private Entry(UUID id, String rawText, Instant occurredAt, Instant createdAt, List<ParsedEntry> items) {
        this.id = id;
        this.rawText = rawText;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        applyInterpretation(items, IntStream.range(0, items.size()).mapToLong(i -> 1).toArray());
        this.isNew = true;
    }

    static Entry interpreted(UUID id, String rawText, Instant occurredAt, Instant createdAt,
                             List<ParsedEntry> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("um lançamento precisa de ao menos um item interpretado");
        }
        return new Entry(id, rawText, occurredAt, createdAt, items);
    }

    /**
     * Grava os itens do parser: os de confiança suficiente já valem ({@link ItemState#ACCEPTED_AUTO}) e
     * geram um evento; os outros esperam o usuário.
     */
    private void applyInterpretation(List<ParsedEntry> newItems, long[] revisions) {
        this.items = List.copyOf(newItems);
        this.detectedType = commonType(newItems);
        this.confidence = lowestConfidence(newItems);
        List<ItemStatus> initial = new ArrayList<>();
        for (int i = 0; i < newItems.size(); i++) {
            ItemState state = newItems.get(i).needsConfirmation() ? ItemState.PENDING : ItemState.ACCEPTED_AUTO;
            initial.add(new ItemStatus(state, revisions[i], false));
        }
        this.statuses = Collections.unmodifiableList(initial);
        for (int i = 0; i < newItems.size(); i++) {
            if (initial.get(i).state().isAccepted()) {
                events.add(new EntryItemAccepted(id, i, revisions[i], occurredAt, newItems.get(i)));
            }
        }
        updateStatus();
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
        ItemStatus itemStatus = statusAt(index);
        ItemResolution resolution = resolutionAt(index);
        boolean confirmed = itemStatus.state() == ItemState.ACCEPTED_BY_USER && resolution != null
                && resolution.item() != null;
        return new EffectiveItem(confirmed ? resolution.item() : items.get(index), itemStatus);
    }

    private ItemResolution resolutionAt(int index) {
        return resolutions != null && index < resolutions.size() ? resolutions.get(index) : null;
    }

    private ItemStatus statusAt(int index) {
        if (statuses != null && index < statuses.size() && statuses.get(index) != null) {
            return statuses.get(index);
        }
        // Sem estado gravado (não acontece depois da V4): deduz como a migração.
        ItemResolution resolution = resolutionAt(index);
        if (resolution != null) {
            return ItemStatus.initial(resolution.action() == ResolutionAction.DISMISS
                    ? ItemState.DISMISSED
                    : ItemState.ACCEPTED_BY_USER);
        }
        return ItemStatus.initial(items.get(index).needsConfirmation() ? ItemState.PENDING : ItemState.ACCEPTED_AUTO);
    }

    /**
     * Aplica a resposta do usuário a um item pendente: confirmar faz o item valer e gera
     * {@link EntryItemAccepted}; descartar não gera evento.
     */
    void resolve(int index, ItemResolution resolution) {
        requireItem(index);
        if (statusAt(index).state() != ItemState.PENDING) {
            throw new EntryResolutionException(Reason.NOT_PENDING,
                    "Este item já foi resolvido ou não precisa de confirmação.");
        }
        ItemState newState = resolution.action() == ResolutionAction.DISMISS
                ? ItemState.DISMISSED
                : ItemState.ACCEPTED_BY_USER;
        ItemStatus next = statusAt(index).next(newState, false);
        setResolution(index, resolution);
        setStatus(index, next);
        if (newState.isAccepted()) {
            events.add(new EntryItemAccepted(id, index, next.revision(), occurredAt, resolution.item()));
        }
        updateStatus();
    }

    /**
     * {@code Não foi isso} ou {@code Desfazer}: o item aceito ou descartado volta a esperar confirmação,
     * e a resposta anterior do usuário é apagada. Se o item valia, gera {@link EntryItemRetracted};
     * se o parser o tinha aceitado sozinho, ele fica marcado como rejeitado.
     */
    void reopen(int index) {
        requireItem(index);
        ItemStatus current = statusAt(index);
        if (current.state() == ItemState.PENDING) {
            throw new EntryResolutionException(Reason.ALREADY_PENDING, "Este item já está esperando confirmação.");
        }
        ItemStatus next = current.next(ItemState.PENDING, current.state() == ItemState.ACCEPTED_AUTO);
        setResolution(index, null);
        setStatus(index, next);
        if (current.state().isAccepted()) {
            events.add(new EntryItemRetracted(id, index, next.revision()));
        }
        updateStatus();
    }

    /**
     * Troca o que o parser entendeu por uma interpretação nova do mesmo texto, por exemplo depois de
     * o parser aprender frases compostas.
     *
     * <p>Lançamento em que o usuário já mexeu (confirmou, descartou ou reabriu algum item) não é
     * reprocessado (ADR 0007): as respostas dele são alinhadas aos itens pela posição, e uma
     * interpretação nova pode ter outro número de itens ou outra ordem. Os itens que já valiam são
     * retirados com {@link EntryItemRetracted}, e os novos aceitos geram {@link EntryItemAccepted}
     * com uma revisão maior, para os consumidores aplicarem na ordem certa.
     *
     * @return {@code true} se a interpretação foi trocada, {@code false} se o usuário já tinha mexido
     *         no lançamento e ele ficou como estava
     */
    boolean reinterpret(List<ParsedEntry> newItems) {
        if (newItems.isEmpty()) {
            throw new IllegalArgumentException("um lançamento precisa de ao menos um item interpretado");
        }
        if (touchedByUser()) {
            return false;
        }
        long[] revisions = new long[newItems.size()];
        for (int i = 0; i < items.size(); i++) {
            ItemStatus old = statusAt(i);
            if (old.state().isAccepted()) {
                events.add(new EntryItemRetracted(id, i, old.revision() + 1));
            }
            if (i < revisions.length) {
                revisions[i] = old.revision() + 2;
            }
        }
        for (int i = items.size(); i < revisions.length; i++) {
            revisions[i] = 1;
        }
        applyInterpretation(newItems, revisions);
        return true;
    }

    /** {@code true} quando o usuário já respondeu, descartou ou reabriu algum item. */
    boolean touchedByUser() {
        boolean answered = resolutions != null && resolutions.stream().anyMatch(Objects::nonNull);
        return answered || IntStream.range(0, items.size()).anyMatch(i -> statusAt(i).revision() > 1);
    }

    /**
     * Os itens que valem agora, como eventos: usado para publicar de novo o que já existia antes
     * dos módulos {@code finance} e {@code routines} (backfill). Repetir é seguro, porque os
     * consumidores ignoram revisões já aplicadas.
     */
    List<EntryItemAccepted> acceptedItems() {
        List<EntryItemAccepted> accepted = new ArrayList<>();
        List<EffectiveItem> effective = effectiveItems();
        for (int i = 0; i < effective.size(); i++) {
            ItemStatus itemStatus = effective.get(i).status();
            if (itemStatus.state().isAccepted()) {
                accepted.add(new EntryItemAccepted(id, i, itemStatus.revision(), occurredAt, effective.get(i).item()));
            }
        }
        return accepted;
    }

    /** Devolve e esquece os eventos guardados; quem chama publica. */
    List<Object> pullEvents() {
        List<Object> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    private void requireItem(int index) {
        if (index < 0 || index >= items.size()) {
            throw new EntryResolutionException(Reason.ITEM_NOT_FOUND, "O lançamento não tem o item " + index + ".");
        }
    }

    private void setResolution(int index, ItemResolution resolution) {
        List<ItemResolution> updated = new ArrayList<>(Collections.nCopies(items.size(), (ItemResolution) null));
        for (int i = 0; i < items.size(); i++) {
            updated.set(i, resolutionAt(i));
        }
        updated.set(index, resolution);
        // Uma lista nova faz o Hibernate perceber a mudança na coluna JSON.
        this.resolutions = Collections.unmodifiableList(updated);
    }

    private void setStatus(int index, ItemStatus itemStatus) {
        List<ItemStatus> updated = new ArrayList<>(IntStream.range(0, items.size()).mapToObj(this::statusAt).toList());
        updated.set(index, itemStatus);
        this.statuses = Collections.unmodifiableList(updated);
    }

    /** Espera o usuário enquanto algum item estiver pendente; senão, está confirmado. */
    private void updateStatus() {
        boolean pending = IntStream.range(0, items.size()).anyMatch(i -> statusAt(i).state() == ItemState.PENDING);
        this.status = pending ? EntryStatus.NEEDS_INPUT : EntryStatus.CONFIRMED;
    }

    /**
     * O trecho do texto que gerou o item ({@code comprei pão} em {@code fui à academia e comprei pão}),
     * ou o texto inteiro sem espaços extras quando o item não tem descrição ou não existe.
     */
    String itemText(int index) {
        String description = index >= 0 && index < items.size() ? items.get(index).description() : null;
        return description != null ? description : rawText.strip().replaceAll("\\s+", " ");
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
