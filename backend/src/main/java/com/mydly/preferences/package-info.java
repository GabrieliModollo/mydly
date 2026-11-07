/**
 * Preferências do usuário: o dia em que a semana começa e o calendário do usuário (fuso e hoje).
 *
 * <p>Módulo folha: não depende de nenhum outro. Os módulos {@code finance}, {@code routines} e
 * {@code insights} usam a API pública dele para saber qual é a semana. Até existir usuário (etapa 9),
 * as preferências são globais; depois passam para o perfil (ADR 0010).
 */
package com.mydly.preferences;
