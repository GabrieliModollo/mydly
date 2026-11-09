package com.mydly.entries.internal;

import com.mydly.entries.Category;
import com.mydly.entries.Direction;
import com.mydly.entries.EntryType;
import com.mydly.entries.ParsedEntry;
import com.mydly.entries.RoutineOutcome;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Como o LLM leu um trecho de um lançamento (ADR 0013). É o formato da saída estruturada pedida ao
 * modelo; nada aqui é confiável antes de {@link #toParsedEntry(String)}, que valida campo a campo.
 *
 * @param index    o trecho a que a leitura se refere, na ordem enviada
 * @param type     {@code FINANCE}, {@code ROUTINE} ou {@code UNKNOWN}
 * @param direction {@code EXPENSE} ou {@code INCOME}, só em finança
 * @param amount   valor em reais, ou nulo quando a frase não diz
 * @param category categoria da finança, ou nula
 * @param outcome  {@code DONE} ou {@code MISSED}, só em rotina
 * @param quantity quantidade medida ou contada, só em rotina
 * @param unit     unidade normalizada da quantidade, ou nula numa contagem
 * @param clear    o modelo achou a frase clara; falso faz o app perguntar ao usuário mesmo assim
 */
record LlmReading(int index, String type, String direction, BigDecimal amount, String category, String outcome,
                  BigDecimal quantity, String unit, boolean clear) {

    /** Confiança de uma leitura clara e completa do LLM: aceita sem perguntar, abaixo das regras fortes. */
    static final double CONFIDENCE_CLEAR = 0.75;

    /** Leitura incerta ou incompleta: o app continua perguntando, já com a sugestão do LLM no painel. */
    static final double CONFIDENCE_UNSURE = 0.45;

    /**
     * O item que esta leitura vira, com o trecho original como descrição: o texto do LLM nunca é
     * guardado. Vazio quando o LLM não soube ({@code UNKNOWN}) ou devolveu algo fora do formato; aí
     * vale o que as regras entenderam.
     */
    Optional<ParsedEntry> toParsedEntry(String description) {
        try {
            return switch (EntryType.valueOf(type)) {
                case FINANCE -> Optional.of(finance(description));
                case ROUTINE -> Optional.of(routine(description));
                case UNKNOWN -> Optional.empty();
            };
        } catch (IllegalArgumentException | NullPointerException outOfFormat) {
            return Optional.empty();
        }
    }

    private ParsedEntry finance(String description) {
        Direction parsedDirection = direction == null ? Direction.EXPENSE : Direction.valueOf(direction);
        BigDecimal validAmount = validAmount();
        Category parsedCategory = category == null ? null : Category.valueOf(category);
        double confidence = clear && validAmount != null ? CONFIDENCE_CLEAR : CONFIDENCE_UNSURE;
        return ParsedEntry.finance(parsedDirection, validAmount, parsedCategory, description, confidence);
    }

    private ParsedEntry routine(String description) {
        RoutineOutcome parsedOutcome = outcome == null ? RoutineOutcome.DONE : RoutineOutcome.valueOf(outcome);
        BigDecimal validQuantity = ParsedEntry.validQuantity(quantity);
        String validUnit = validQuantity != null && unit != null && ParsedEntry.UNITS.contains(unit) ? unit : null;
        return ParsedEntry.routine(parsedOutcome, validQuantity, validUnit, description,
                clear ? CONFIDENCE_CLEAR : CONFIDENCE_UNSURE);
    }

    /** Valor positivo com até duas casas e que cabe na coluna; outro valor é tratado como não dito. */
    private BigDecimal validAmount() {
        return ParsedEntry.validAmount(amount);
    }
}
