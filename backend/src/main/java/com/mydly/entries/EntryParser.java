package com.mydly.entries;

import java.util.List;
import java.util.UUID;

/**
 * Interpreta um lançamento em texto livre (por exemplo {@code gastei 50 reais na farmácia}
 * ou {@code bebi 2 litros de água}) e decide se é finança ou rotina.
 *
 * <p>Devolve uma lista porque uma frase composta pode gerar vários lançamentos (ADR 0003, item 4).
 * A lista nunca é vazia, e implementações nunca lançam exceção por texto inválido: frases vazias
 * ou ambíguas voltam como um único {@link EntryType#UNKNOWN} com confiança baixa.
 */
public interface EntryParser {

    List<ParsedEntry> parse(String text);

    /**
     * Como {@link #parse(String)}, para o lançamento de um usuário: o que for ao LLM conta na cota
     * diária dele (ADR 0017). O {@code userId} só serve à cota e nunca vai para o LLM.
     */
    default List<ParsedEntry> parse(String text, UUID userId) {
        return parse(text);
    }
}
