package com.mydly.entries;

import java.util.List;

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
}
