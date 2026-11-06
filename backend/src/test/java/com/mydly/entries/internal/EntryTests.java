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
    void exigeAoMenosUmItem() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Entry.interpreted(UUID.randomUUID(), "x", NOW, NOW, List.of()));
    }

    private static Entry entry(ParsedEntry... items) {
        return Entry.interpreted(UUID.randomUUID(), "texto", NOW, NOW, List.of(items));
    }
}
