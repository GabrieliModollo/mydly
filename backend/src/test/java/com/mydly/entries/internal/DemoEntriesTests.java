package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.RoutineOutcome;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * A semana de exemplo da demonstração (ADR 0017) só tem frases que as regras locais entendem, sem
 * LLM, e mostra os padrões que o Resumo procura.
 */
class DemoEntriesTests {

    private final RuleBasedEntryParser rules = new RuleBasedEntryParser();
    private final List<DemoEntries.Line> lines = DemoEntries.week(DemoEntries.DAYS);

    @Test
    void asRegrasEntendemTodasAsFrasesMenosOPaoQueFicaParaConfirmar() {
        for (DemoEntries.Line line : lines) {
            List<ParsedEntry> items = rules.parse(line.text());
            assertThat(items).as(line.text()).hasSize(1);
            boolean pending = line.text().equals("Comprei pão");
            assertThat(items.getFirst().needsConfirmation()).as(line.text()).isEqualTo(pending);
        }
    }

    @Test
    void cadaDiaTemUmaNoiteDeSonoEHojeFicaVazio() {
        Map<Integer, Long> sleepPerDay = lines.stream().filter(line -> line.text().startsWith("Dormi"))
                .collect(Collectors.groupingBy(DemoEntries.Line::daysAgo, Collectors.counting()));
        assertThat(sleepPerDay).hasSize(DemoEntries.DAYS).allSatisfy((day, count) -> assertThat(count).isOne());
        assertThat(lines.getFirst().daysAgo()).isEqualTo(DemoEntries.DAYS);
        assertThat(lines.getLast().daysAgo()).isOne();
    }

    @Test
    void emQuaisquerSeteDiasSeguidosOGastoNasNoitesRuinsEPeloMenos20PorCentoMaior() {
        for (int newest = 1; newest + 6 <= DemoEntries.DAYS; newest++) {
            int from = newest;
            List<Integer> badDays = days(line -> line.text().equals("Dormi mal"), from);
            List<Integer> goodDays = days(line -> line.text().equals("Dormi bem"), from);
            assertThat(badDays).hasSize(3);
            assertThat(goodDays).hasSize(4);
            BigDecimal bad = average(badDays);
            BigDecimal good = average(goodDays);
            assertThat(bad).as("janela a partir de %d dias atrás", from)
                    .isGreaterThanOrEqualTo(good.multiply(new BigDecimal("1.2")));
        }
    }

    @Test
    void temHabitosFeitosEPerdidosParaAsMetasAparecerem() {
        List<RoutineOutcome> outcomes = lines.stream().map(line -> rules.parse(line.text()).getFirst())
                .filter(item -> item.type() == EntryType.ROUTINE).map(ParsedEntry::outcome).distinct().toList();
        assertThat(outcomes).contains(RoutineOutcome.DONE, RoutineOutcome.MISSED);
    }

    private List<Integer> days(java.util.function.Predicate<DemoEntries.Line> sleep, int newest) {
        return lines.stream().filter(sleep).map(DemoEntries.Line::daysAgo)
                .filter(day -> day >= newest && day <= newest + 6).toList();
    }

    private BigDecimal average(List<Integer> days) {
        BigDecimal total = lines.stream().filter(line -> days.contains(line.daysAgo()))
                .map(line -> rules.parse(line.text()).getFirst())
                .filter(item -> item.type() == EntryType.FINANCE && item.direction() == com.mydly.entries.Direction.EXPENSE)
                .map(ParsedEntry::amount).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(days.size()), 2, java.math.RoundingMode.HALF_UP);
    }
}
