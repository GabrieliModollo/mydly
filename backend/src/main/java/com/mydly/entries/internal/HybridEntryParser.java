package com.mydly.entries.internal;

import com.mydly.entries.EntryParser;
import com.mydly.entries.ParsedEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * O parser híbrido do ADR 0003: regras locais primeiro, LLM só para o que elas não entenderam.
 *
 * <p>As regras dividem a frase e interpretam cada parte. Só os itens que ficaram abaixo do limiar de
 * confirmação ({@link ParsedEntry#needsConfirmation()}) vão para o LLM, todos numa chamada, cada um
 * com o seu trecho. Os itens que as regras entenderam ficam como estão. Uma leitura do LLM troca o
 * item das regras quando é válida; se o LLM não souber, falhar ou estiver desligado, o item continua
 * como as regras deixaram e o app pergunta ao usuário (ADR 0013).
 */
@Primary
@Component
class HybridEntryParser implements EntryParser {

    private final RuleBasedEntryParser rules;
    private final LlmInterpreter llm;

    HybridEntryParser(RuleBasedEntryParser rules, LlmInterpreter llm) {
        this.rules = rules;
        this.llm = llm;
    }

    @Override
    public List<ParsedEntry> parse(String text) {
        List<ParsedEntry> items = rules.parse(text);
        if (!llm.enabled()) {
            return items;
        }
        List<Integer> doubtful = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ParsedEntry item = items.get(i);
            if (item.needsConfirmation() && item.description() != null && !item.description().isBlank()) {
                doubtful.add(i);
            }
        }
        if (doubtful.isEmpty()) {
            return items;
        }
        List<String> segments = doubtful.stream().map(i -> items.get(i).description()).toList();
        Optional<List<LlmReading>> readings = llm.read(text, segments);
        if (readings.isEmpty()) {
            return items;
        }
        List<ParsedEntry> result = new ArrayList<>(items);
        Set<Integer> answered = new HashSet<>();
        for (LlmReading reading : readings.get()) {
            int segment = reading.index();
            // Índice fora da lista ou repetido: a leitura é ignorada, e o item fica como as regras deixaram.
            if (segment < 0 || segment >= segments.size() || !answered.add(segment)) {
                continue;
            }
            int position = doubtful.get(segment);
            reading.toParsedEntry(items.get(position).description()).ifPresent(item -> result.set(position, item));
        }
        return List.copyOf(result);
    }
}
