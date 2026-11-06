package com.mydly.entries;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Um lançamento interpretado a partir de texto livre.
 *
 * @param type        finança, rotina ou desconhecido
 * @param direction   saída ou entrada de dinheiro; presente só em {@link EntryType#FINANCE}
 * @param amount      valor monetário em reais; só em {@link EntryType#FINANCE}, e pode faltar
 *                    quando a frase indica uma finança sem dizer quanto
 * @param quantity    quantidade medida ou contada (2 em {@code 2 litros}, 30 em {@code 30 flexões});
 *                    só em {@link EntryType#ROUTINE}
 * @param unit        unidade normalizada da quantidade ({@code L}, {@code mL}, {@code h}, {@code min},
 *                    {@code km}, {@code kg}, {@code copo}), ou {@code null} quando a quantidade é uma contagem
 * @param description texto do lançamento sem espaços extras, ou {@code null} quando a frase
 *                    é só o valor (ex.: {@code 50 reais}) e a categoria precisa ser perguntada depois
 * @param confidence  confiança da interpretação, de 0 a 1; abaixo de {@link #ASK_THRESHOLD}
 *                    o lançamento vai para o fallback (LLM ou pergunta ao usuário), conforme o ADR 0003
 */
public record ParsedEntry(
        EntryType type,
        Direction direction,
        BigDecimal amount,
        BigDecimal quantity,
        String unit,
        String description,
        double confidence) {

    /** Abaixo desta confiança o app pergunta ao usuário em vez de aceitar a interpretação. */
    public static final double ASK_THRESHOLD = 0.50;

    public ParsedEntry {
        Objects.requireNonNull(type, "type");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence deve estar entre 0 e 1: " + confidence);
        }
        if ((type == EntryType.FINANCE) != (direction != null)) {
            throw new IllegalArgumentException("direction é obrigatória em FINANCE e proibida nos outros tipos");
        }
        if (unit != null && quantity == null) {
            throw new IllegalArgumentException("unit exige quantity");
        }
    }

    public static ParsedEntry finance(Direction direction, BigDecimal amount, String description, double confidence) {
        return new ParsedEntry(EntryType.FINANCE, direction, amount, null, null, description, confidence);
    }

    public static ParsedEntry routine(BigDecimal quantity, String unit, String description, double confidence) {
        return new ParsedEntry(EntryType.ROUTINE, null, null, quantity, unit, description, confidence);
    }

    public static ParsedEntry unknown(String description, double confidence) {
        return new ParsedEntry(EntryType.UNKNOWN, null, null, null, null, description, confidence);
    }

    /** {@code true} quando o app deve confirmar com o usuário antes de registrar. */
    public boolean needsConfirmation() {
        return confidence < ASK_THRESHOLD;
    }
}
