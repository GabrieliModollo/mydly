/**
 * Lançamentos em texto livre.
 *
 * <p>Recebe o texto bruto, guarda sempre o original e coordena a interpretação
 * (regras locais e LLM como fallback, ver ADR 0003). Publica eventos para
 * {@code finance} e {@code routines} quando um lançamento é confirmado.
 */
package com.mydly.entries;
