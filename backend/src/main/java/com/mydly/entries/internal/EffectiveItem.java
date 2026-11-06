package com.mydly.entries.internal;

import com.mydly.entries.ParsedEntry;

/**
 * Um item como ele vale agora: a resolução do usuário, se houver, senão o que o parser entendeu.
 *
 * @param item              o item efetivo; num descarte, continua sendo o do parser
 * @param resolvedByUser    o usuário confirmou ou descartou este item
 * @param dismissed         o usuário disse que não era nada a registrar
 * @param needsConfirmation ainda falta o usuário confirmar
 */
public record EffectiveItem(ParsedEntry item, boolean resolvedByUser, boolean dismissed, boolean needsConfirmation) {
}
