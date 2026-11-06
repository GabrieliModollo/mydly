package com.mydly.entries;

/**
 * Categoria de uma finança. Lista fixa por enquanto; categorias personalizadas entram com o
 * módulo finance (etapa 5 do backlog). O app mostra os nomes em português.
 */
public enum Category {

    /** Alimentação: padaria, lanche, restaurante, delivery. */
    FOOD,

    /** Mercado: supermercado, feira, hortifrúti. */
    GROCERIES,

    /** Farmácia: remédios e drogaria. */
    PHARMACY,

    /** Transporte: aplicativo, ônibus, combustível, estacionamento. */
    TRANSPORT,

    /** Moradia: aluguel, condomínio, contas da casa. */
    HOUSING,

    /** Saúde: consultas, exames, academia. */
    HEALTH,

    /** Lazer: cinema, bar, viagem, assinaturas. */
    LEISURE,

    /** Trabalho: salário, freelas, cursos. */
    WORK,

    /** Outros. */
    OTHER
}
