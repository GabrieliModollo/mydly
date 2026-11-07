/**
 * Integrações: fontes externas de transação (extrato OFX ou CSV e, depois, Open Finance via
 * sandbox) atrás da porta {@code TransactionSource}, e reconciliação com os lançamentos manuais
 * (ADR 0005 e ADR 0014).
 *
 * <p>Depende de {@code finance} (para os números e os lançamentos manuais), de {@code entries} (os
 * eventos dos lançamentos), de {@code preferences} (o fuso) e de {@code accounts}. Nenhum módulo
 * depende dele.
 */
package com.mydly.integrations;
