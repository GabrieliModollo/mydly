package com.mydly.entries.internal;

import static com.mydly.TestUsers.DEMO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mydly.entries.Direction;
import com.mydly.entries.EntryItemAccepted;
import com.mydly.entries.EntryItemRetracted;
import com.mydly.entries.ParsedEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Regras de consolidação dos itens em um lançamento. Frases compostas geram vários itens,
 * e o lançamento resume todos eles.
 */
class EntryTests {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    private static final ParsedEntry FINANCE = ParsedEntry.finance(Direction.EXPENSE, BigDecimal.TEN, "x", 0.95);
    private static final ParsedEntry ROUTINE = ParsedEntry.routine(null, null, "y", 0.60);
    private static final ParsedEntry LOW_CONFIDENCE = ParsedEntry.finance(Direction.EXPENSE, null, "z", 0.40);

    @Test
    void itensDeTiposDiferentesDeixamOTipoDoLancamentoVazio() {
        Entry entry = entry(FINANCE, ROUTINE);

        assertThat(entry.getDetectedType()).isNull();
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.CONFIRMED);
    }

    @Test
    void confiancaDoLancamentoEAMenorEntreOsItens() {
        assertThat(entry(FINANCE, ROUTINE).getConfidence()).isEqualByComparingTo("0.60");
    }

    @Test
    void umItemDeBaixaConfiancaPedeConfirmacaoDoLancamentoInteiro() {
        assertThat(entry(FINANCE, LOW_CONFIDENCE).getStatus()).isEqualTo(EntryStatus.NEEDS_INPUT);
    }

    @Test
    void lancamentoNovoSoDeixaDeSerNovoDepoisDeGravado() {
        Entry entry = entry(FINANCE);
        assertThat(entry.isNew()).isTrue();

        entry.markNotNew();

        assertThat(entry.isNew()).isFalse();
    }

    @Test
    void semResolucaoOItemEfetivoEODoParser() {
        Entry entry = entry(LOW_CONFIDENCE);

        assertThat(entry.effectiveItems()).containsExactly(new EffectiveItem(LOW_CONFIDENCE, ItemStatus.initial(ItemState.PENDING)));
    }

    @Test
    void resolucaoTrocaOItemEfetivoSemMudarODoParser() {
        Entry entry = entry(LOW_CONFIDENCE);
        ParsedEntry confirmed = ParsedEntry.finance(Direction.EXPENSE, new BigDecimal("8.50"), "z", 1.0);

        entry.resolve(0, ItemResolution.confirmed(confirmed));

        assertThat(entry.effectiveItems()).containsExactly(new EffectiveItem(confirmed, new ItemStatus(ItemState.ACCEPTED_BY_USER, 2, false)));
        assertThat(entry.getItems()).containsExactly(LOW_CONFIDENCE);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.CONFIRMED);
    }

    @Test
    void descarteMantemOItemDoParserMarcadoComoDescartado() {
        Entry entry = entry(LOW_CONFIDENCE);

        entry.resolve(0, ItemResolution.dismissed());

        assertThat(entry.effectiveItems()).containsExactly(new EffectiveItem(LOW_CONFIDENCE, new ItemStatus(ItemState.DISMISSED, 2, false)));
    }

    @Test
    void naoResolveItemInexistenteNemItemQueNaoPrecisaDeConfirmacao() {
        Entry entry = entry(FINANCE, LOW_CONFIDENCE);

        assertThat(reasonOf(() -> entry.resolve(-1, ItemResolution.dismissed())))
                .isEqualTo(EntryResolutionException.Reason.ITEM_NOT_FOUND);
        assertThat(reasonOf(() -> entry.resolve(2, ItemResolution.dismissed())))
                .isEqualTo(EntryResolutionException.Reason.ITEM_NOT_FOUND);
        assertThat(reasonOf(() -> entry.resolve(0, ItemResolution.dismissed())))
                .isEqualTo(EntryResolutionException.Reason.NOT_PENDING);
    }

    @Test
    void resolverDeNovoOMesmoItemNaoEPermitido() {
        Entry entry = entry(LOW_CONFIDENCE);
        entry.resolve(0, ItemResolution.dismissed());

        assertThat(reasonOf(() -> entry.resolve(0, ItemResolution.dismissed())))
                .isEqualTo(EntryResolutionException.Reason.NOT_PENDING);
    }

    @Test
    void naoSeConfirmaUmItemComoDesconhecidoNemDescarteComItem() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ItemResolution.confirmed(ParsedEntry.unknown("x", 1.0)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ItemResolution(ResolutionAction.DISMISS, FINANCE));
    }

    private static EntryResolutionException.Reason reasonOf(Runnable action) {
        try {
            action.run();
        } catch (EntryResolutionException e) {
            return e.reason();
        }
        throw new AssertionError("esperava EntryResolutionException");
    }

    @Test
    void lancamentoSemResolucaoPodeSerReinterpretado() {
        Entry entry = entry(LOW_CONFIDENCE);

        boolean changed = entry.reinterpret(List.of(ROUTINE, FINANCE));

        assertThat(changed).isTrue();
        assertThat(entry.getItems()).containsExactly(ROUTINE, FINANCE);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.CONFIRMED);
        assertThat(entry.getConfidence()).isEqualByComparingTo("0.60");
    }

    /**
     * A resolução é alinhada aos itens pela posição (ADR 0007). Uma interpretação nova pode ter
     * outro número de itens, e a confirmação do usuário passaria a valer para outro item.
     */
    @Test
    void lancamentoComResolucaoNaoEReprocessado() {
        Entry entry = entry(LOW_CONFIDENCE);
        entry.resolve(0, ItemResolution.dismissed());

        boolean changed = entry.reinterpret(List.of(ROUTINE, FINANCE));

        assertThat(changed).isFalse();
        assertThat(entry.getItems()).containsExactly(LOW_CONFIDENCE);
        assertThat(entry.effectiveItems()).containsExactly(new EffectiveItem(LOW_CONFIDENCE, new ItemStatus(ItemState.DISMISSED, 2, false)));
    }

    @Test
    void itemDeConfiancaSuficienteJaValeEGeraEvento() {
        Entry entry = entry(FINANCE, LOW_CONFIDENCE);

        assertThat(entry.effectiveItems()).extracting(item -> item.status().state())
                .containsExactly(ItemState.ACCEPTED_AUTO, ItemState.PENDING);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.NEEDS_INPUT);
        assertThat(entry.pullEvents()).containsExactly(new EntryItemAccepted(DEMO, entry.getId(), 0, 1, NOW, FINANCE));
        assertThat(entry.pullEvents()).as("os eventos saem uma vez só").isEmpty();
    }

    @Test
    void confirmarUmItemGeraEventoComARevisaoNova() {
        Entry entry = entry(LOW_CONFIDENCE);
        ParsedEntry confirmed = ParsedEntry.finance(Direction.EXPENSE, new BigDecimal("8.50"), "z", 1.0);
        entry.pullEvents();

        entry.resolve(0, ItemResolution.confirmed(confirmed));

        assertThat(entry.pullEvents()).containsExactly(new EntryItemAccepted(DEMO, entry.getId(), 0, 2, NOW, confirmed));
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.CONFIRMED);
    }

    @Test
    void descartarNaoGeraEvento() {
        Entry entry = entry(LOW_CONFIDENCE);

        entry.resolve(0, ItemResolution.dismissed());

        assertThat(entry.pullEvents()).isEmpty();
    }

    @Test
    void naoFoiIssoRetiraOItemAceitoEVoltaAPedirConfirmacao() {
        Entry entry = entry(FINANCE);
        entry.pullEvents();

        entry.reopen(0);

        assertThat(entry.pullEvents()).containsExactly(new EntryItemRetracted(DEMO, entry.getId(), 0, 2));
        assertThat(entry.effectiveItems()).containsExactly(
                new EffectiveItem(FINANCE, new ItemStatus(ItemState.PENDING, 2, true)));
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.NEEDS_INPUT);
    }

    @Test
    void itemReabertoPodeSerConfirmadoComOutroValor() {
        Entry entry = entry(FINANCE);
        entry.reopen(0);
        entry.pullEvents();
        ParsedEntry corrected = ParsedEntry.finance(Direction.EXPENSE, new BigDecimal("35"), "x", 1.0);

        entry.resolve(0, ItemResolution.confirmed(corrected));

        assertThat(entry.pullEvents()).containsExactly(new EntryItemAccepted(DEMO, entry.getId(), 0, 3, NOW, corrected));
        assertThat(entry.effectiveItems().getFirst().item()).isEqualTo(corrected);
        assertThat(entry.effectiveItems().getFirst().status().parserRejected()).isTrue();
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.CONFIRMED);
    }

    @Test
    void desfazerUmDescarteNaoGeraEvento() {
        Entry entry = entry(LOW_CONFIDENCE);
        entry.resolve(0, ItemResolution.dismissed());

        entry.reopen(0);

        assertThat(entry.pullEvents()).isEmpty();
        assertThat(entry.effectiveItems().getFirst().status())
                .isEqualTo(new ItemStatus(ItemState.PENDING, 3, false));
    }

    @Test
    void naoReabreItemQueJaEstaPendente() {
        Entry entry = entry(LOW_CONFIDENCE);

        assertThat(reasonOf(() -> entry.reopen(0))).isEqualTo(EntryResolutionException.Reason.ALREADY_PENDING);
        assertThat(reasonOf(() -> entry.reopen(1))).isEqualTo(EntryResolutionException.Reason.ITEM_NOT_FOUND);
    }

    @Test
    void reinterpretarRetiraOsItensAntigosEAceitaOsNovosComRevisaoMaior() {
        Entry entry = entry(FINANCE);
        entry.pullEvents();

        entry.reinterpret(List.of(ROUTINE, FINANCE));

        assertThat(entry.pullEvents()).containsExactly(
                new EntryItemRetracted(DEMO, entry.getId(), 0, 2),
                new EntryItemAccepted(DEMO, entry.getId(), 0, 3, NOW, ROUTINE),
                new EntryItemAccepted(DEMO, entry.getId(), 1, 1, NOW, FINANCE));
    }

    @Test
    void lancamentoReabertoPeloUsuarioNaoEReprocessado() {
        Entry entry = entry(FINANCE);
        entry.reopen(0);

        assertThat(entry.reinterpret(List.of(ROUTINE))).isFalse();
    }

    @Test
    void itensAceitosViramEventosParaOBackfill() {
        Entry entry = entry(FINANCE, LOW_CONFIDENCE, ROUTINE);

        assertThat(entry.acceptedItems()).containsExactly(
                new EntryItemAccepted(DEMO, entry.getId(), 0, 1, NOW, FINANCE),
                new EntryItemAccepted(DEMO, entry.getId(), 2, 1, NOW, ROUTINE));
    }

    @Test
    void exigeAoMenosUmItem() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Entry.interpreted(DEMO, UUID.randomUUID(), "x", NOW, NOW, List.of()));
    }

    private static Entry entry(ParsedEntry... items) {
        return Entry.interpreted(DEMO, UUID.randomUUID(), "texto", NOW, NOW, List.of(items));
    }
}
