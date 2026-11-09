package com.mydly.entries;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * Um lançamento interpretado a partir de texto livre.
 *
 * <p>Este record também é o formato gravado no {@code parsed_json}. Campos novos precisam
 * aceitar ausência (dados antigos): o {@code outcome} vale {@link RoutineOutcome#DONE} quando
 * falta numa rotina, e a {@code category} pode faltar.
 *
 * @param type        finança, rotina ou desconhecido
 * @param direction   saída ou entrada de dinheiro; presente só em {@link EntryType#FINANCE}
 * @param outcome     se o hábito foi cumprido; presente só em {@link EntryType#ROUTINE}
 * @param category    categoria sugerida ou confirmada; só em {@link EntryType#FINANCE}, e pode faltar
 * @param amount      valor monetário em reais; só em {@link EntryType#FINANCE}, e pode faltar
 *                    quando a frase indica uma finança sem dizer quanto
 * @param quantity    quantidade medida ou contada (2 em {@code 2 litros}, 30 em {@code 30 flexões});
 *                    só em {@link EntryType#ROUTINE}
 * @param unit        unidade normalizada da quantidade (uma de {@link #UNITS}), ou {@code null}
 *                    quando a quantidade é uma contagem
 * @param description texto do lançamento sem espaços extras, ou {@code null} quando a frase
 *                    é só o valor (ex.: {@code 50 reais}) e a categoria precisa ser perguntada depois
 * @param confidence  confiança da interpretação, de 0 a 1; abaixo de {@link #ASK_THRESHOLD}
 *                    o lançamento vai para o fallback (LLM ou pergunta ao usuário), conforme o ADR 0003
 */
public record ParsedEntry(
        EntryType type,
        Direction direction,
        RoutineOutcome outcome,
        Category category,
        BigDecimal amount,
        BigDecimal quantity,
        String unit,
        String description,
        double confidence) {

    /** Abaixo desta confiança o app pergunta ao usuário em vez de aceitar a interpretação. */
    public static final double ASK_THRESHOLD = 0.50;

    /** Confiança de um item confirmado pelo próprio usuário. */
    public static final double CONFIRMED_BY_USER = 1.0;

    /** Unidades normalizadas aceitas em {@code unit}. */
    public static final Set<String> UNITS = Set.of("L", "mL", "h", "min", "km", "kg", "copo");

    /** O maior valor que cabe em {@code finance_transaction.amount}: 12 dígitos com 2 casas decimais. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    /** A maior quantidade que cabe em {@code habit_log.quantity}: 12 dígitos com 3 casas decimais. */
    public static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999.999");

    /**
     * O valor sem zeros à direita quando ele pode ser gravado: maior que zero, até {@link #MAX_AMOUNT}
     * e com no máximo duas casas decimais. Fora disso, {@code null}: o valor é tratado como não dito, e
     * o app pergunta. Um valor que não cabe na coluna faria o evento falhar para sempre.
     */
    public static BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            return null;
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() > 2 ? null : stripped.setScale(Math.max(stripped.scale(), 0));
    }

    /** A quantidade sem zeros à direita quando ela pode ser gravada: maior que zero, até {@link #MAX_QUANTITY} e com até três casas. */
    public static BigDecimal validQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0) {
            return null;
        }
        BigDecimal stripped = quantity.stripTrailingZeros();
        return stripped.scale() > 3 ? null : stripped.setScale(Math.max(stripped.scale(), 0));
    }

    public ParsedEntry {
        Objects.requireNonNull(type, "type");
        if (type == EntryType.ROUTINE && outcome == null) {
            // Itens gravados antes de o campo existir eram todos hábitos cumpridos.
            outcome = RoutineOutcome.DONE;
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence deve estar entre 0 e 1: " + confidence);
        }
        if ((type == EntryType.FINANCE) != (direction != null)) {
            throw new IllegalArgumentException("direction é obrigatória em FINANCE e proibida nos outros tipos");
        }
        if (type != EntryType.FINANCE && category != null) {
            throw new IllegalArgumentException("category só existe em FINANCE");
        }
        if (type != EntryType.ROUTINE && outcome != null) {
            throw new IllegalArgumentException("outcome só existe em ROUTINE");
        }
        if (unit != null && quantity == null) {
            throw new IllegalArgumentException("unit exige quantity");
        }
        if (unit != null && !UNITS.contains(unit)) {
            throw new IllegalArgumentException("unidade desconhecida: " + unit);
        }
    }

    public static ParsedEntry finance(Direction direction, BigDecimal amount, String description, double confidence) {
        return finance(direction, amount, null, description, confidence);
    }

    public static ParsedEntry finance(Direction direction, BigDecimal amount, Category category, String description,
                                      double confidence) {
        return new ParsedEntry(EntryType.FINANCE, direction, null, category, amount, null, null, description,
                confidence);
    }

    /** Rotina cumprida. */
    public static ParsedEntry routine(BigDecimal quantity, String unit, String description, double confidence) {
        return routine(RoutineOutcome.DONE, quantity, unit, description, confidence);
    }

    public static ParsedEntry routine(RoutineOutcome outcome, BigDecimal quantity, String unit, String description,
                                      double confidence) {
        return new ParsedEntry(EntryType.ROUTINE, null, Objects.requireNonNull(outcome, "outcome"), null, null,
                quantity, unit, description, confidence);
    }

    public static ParsedEntry unknown(String description, double confidence) {
        return new ParsedEntry(EntryType.UNKNOWN, null, null, null, null, null, null, description, confidence);
    }

    /** O mesmo item com outra categoria. Só faz sentido em finanças. */
    public ParsedEntry withCategory(Category newCategory) {
        return new ParsedEntry(type, direction, outcome, newCategory, amount, quantity, unit, description, confidence);
    }

    /** {@code true} quando o app deve confirmar com o usuário antes de registrar. */
    public boolean needsConfirmation() {
        return confidence < ASK_THRESHOLD;
    }
}
