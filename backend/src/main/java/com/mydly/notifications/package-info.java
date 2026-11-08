/**
 * Notificações push (ADR 0016): alerta de orçamento, lembrete do dia e resumo da semana, pelo Firebase
 * Cloud Messaging.
 *
 * <p>Depende das APIs públicas de {@code finance} (orçamentos), {@code preferences} (semana e fuso),
 * {@code entries} (se o usuário lançou algo no dia), {@code insights} (a mensagem da semana) e
 * {@code accounts}. Nenhum módulo depende dele.
 */
package com.mydly.notifications;
