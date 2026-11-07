package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.entries.Category;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * O parser híbrido (ADR 0003 e ADR 0013): regras primeiro, LLM só para os itens que elas não
 * entenderam, e as regras de volta sempre que o LLM falhar ou não souber.
 */
class HybridEntryParserTests {

    private final RuleBasedEntryParser rules = new RuleBasedEntryParser();

    /** LLM de mentira: guarda o que recebeu e responde o que o teste mandar. */
    private static final class FakeLlm implements LlmInterpreter {

        final List<String> texts = new ArrayList<>();
        final List<List<String>> segments = new ArrayList<>();
        Function<List<String>, Optional<List<LlmReading>>> answer = sent -> Optional.empty();

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Optional<List<LlmReading>> read(String text, List<String> sent) {
            texts.add(text);
            segments.add(sent);
            return answer.apply(sent);
        }
    }

    private final FakeLlm llm = new FakeLlm();
    private final HybridEntryParser parser = new HybridEntryParser(rules, llm);

    @Test
    void oQueAsRegrasEntendemNaoVaiParaOLlm() {
        List<ParsedEntry> items = parser.parse("Gastei 50 reais na farmácia");

        assertThat(llm.texts).isEmpty();
        assertThat(items).isEqualTo(rules.parse("Gastei 50 reais na farmácia"));
    }

    @Test
    void valorPorExtensoVemDoLlm() {
        llm.answer = sent -> Optional.of(List.of(finance(0, "50", "FOOD", true)));

        List<ParsedEntry> items = parser.parse("Gastei cinquenta reais na padaria");

        assertThat(llm.segments).containsExactly(List.of("Gastei cinquenta reais na padaria"));
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.amount()).isEqualByComparingTo("50");
            assertThat(item.category()).isEqualTo(Category.FOOD);
            assertThat(item.needsConfirmation()).isFalse();
            assertThat(item.description()).isEqualTo("Gastei cinquenta reais na padaria");
        });
    }

    @Test
    void numaFraseCompostaSoOTrechoDuvidosoVaiParaOLlm() {
        // As regras entendem a academia e não sabem o valor do pão.
        List<ParsedEntry> byRules = rules.parse("Fui à academia e comprei pão por cinco reais");
        assertThat(byRules).hasSize(2);
        assertThat(byRules.get(0).needsConfirmation()).isFalse();
        assertThat(byRules.get(1).needsConfirmation()).isTrue();
        llm.answer = sent -> Optional.of(List.of(finance(0, "5", "FOOD", true)));

        List<ParsedEntry> items = parser.parse("Fui à academia e comprei pão por cinco reais");

        assertThat(llm.texts).containsExactly("Fui à academia e comprei pão por cinco reais");
        assertThat(llm.segments).containsExactly(List.of(byRules.get(1).description()));
        assertThat(items.get(0)).isEqualTo(byRules.get(0));
        assertThat(items.get(1).amount()).isEqualByComparingTo("5");
        assertThat(items.get(1).description()).isEqualTo(byRules.get(1).description());
    }

    @Test
    void semRespostaDoLlmFicaComAsRegrasEOAppPergunta() {
        List<ParsedEntry> items = parser.parse("Comprei pão");

        assertThat(llm.texts).hasSize(1);
        assertThat(items).isEqualTo(rules.parse("Comprei pão"));
        assertThat(items.getFirst().needsConfirmation()).isTrue();
    }

    @Test
    void llmQueNaoSabeDeixaOItemDasRegras() {
        llm.answer = sent -> Optional.of(List.of(new LlmReading(0, "UNKNOWN", null, null, null, null, null, null, false)));

        assertThat(parser.parse("Não gastei nada hoje")).isEqualTo(rules.parse("Não gastei nada hoje"));
    }

    @Test
    void leituraComIndiceErradoOuRepetidoEIgnorada() {
        llm.answer = sent -> Optional.of(List.of(
                finance(7, "10", null, true),
                finance(-1, "10", null, true),
                finance(0, "8.50", "FOOD", true),
                finance(0, "999", "OTHER", true)));

        ParsedEntry item = parser.parse("Comprei pão").getFirst();

        assertThat(item.amount()).isEqualByComparingTo("8.50");
        assertThat(item.category()).isEqualTo(Category.FOOD);
    }

    @Test
    void textoVazioNaoVaiParaOLlm() {
        assertThat(parser.parse("   ").getFirst().type()).isEqualTo(EntryType.UNKNOWN);
        assertThat(llm.texts).isEmpty();
    }

    @Test
    void semChaveSoAsRegras() {
        HybridEntryParser withoutLlm = new HybridEntryParser(rules, LlmInterpreter.DISABLED);

        assertThat(withoutLlm.parse("Comprei pão")).isEqualTo(rules.parse("Comprei pão"));
    }

    private static LlmReading finance(int index, String amount, String category, boolean clear) {
        return new LlmReading(index, "FINANCE", "EXPENSE", new BigDecimal(amount), category, null, null, null, clear);
    }
}
