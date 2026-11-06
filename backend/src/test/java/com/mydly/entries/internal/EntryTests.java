package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mydly.entries.Direction;
import com.mydly.entries.ParsedEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Regras de consolidação dos itens em um lançamento. Hoje o parser só devolve um item, mas
 * frases compostas vão gerar vários, então os casos com mais de um item já ficam garantidos.
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
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.PENDING);
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

        assertThat(entry.effectiveItems()).containsExactly(new EffectiveItem(LOW_CONFIDENCE, false, false, true));
    }

    @Test
    void resolucaoTrocaOItemEfetivoSemMudarODoParser() {
        Entry entry = entry(LOW_CONFIDENCE);
        ParsedEntry confirmed = ParsedEntry.finance(Direction.EXPENSE, new BigDecimal("8.50"), "z", 1.0);

        entry.resolve(0, ItemResolution.confirmed(confirmed));

        assertThat(entry.effectiveItems()).containsExactly(new EffectiveItem(confirmed, true, false, false));
        assertThat(entry.getItems()).containsExactly(LOW_CONFIDENCE);
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.PENDING);
    }

    @Test
    void descarteMantemOItemDoParserMarcadoComoDescartado() {
        Entry entry = entry(LOW_CONFIDENCE);

        entry.resolve(0, ItemResolution.dismissed());

        assertThat(entry.effectiveItems()).containsExactly(new EffectiveItem(LOW_CONFIDENCE, true, true, false));
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
    void exigeAoMenosUmItem() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Entry.interpreted(UUID.randomUUID(), "x", NOW, NOW, List.of()));
    }

    private static Entry entry(ParsedEntry... items) {
        return Entry.interpreted(UUID.randomUUID(), "texto", NOW, NOW, List.of(items));
    }
}
