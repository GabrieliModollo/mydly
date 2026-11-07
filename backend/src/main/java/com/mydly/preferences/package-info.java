/**
 * Preferências do usuário: o dia em que a semana começa e o calendário do usuário (fuso e hoje).
 *
 * <p>Depende só de {@code accounts}, no controlador, para saber quem está logado. Os módulos
 * {@code finance}, {@code routines} e {@code insights} usam a API pública dele para saber qual é a
 * semana de cada usuário (ADR 0010 e ADR 0012).
 */
package com.mydly.preferences;
