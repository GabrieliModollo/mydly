/**
 * Contas: usuários, login com senha e o token de acesso (JWT) que a API exige (ADR 0012).
 *
 * <p>Módulo folha: não depende de nenhum outro. Os controladores dos outros módulos usam
 * {@link com.mydly.accounts.CurrentUser} para saber de quem é o pedido e passam o id adiante; os
 * serviços nunca leem o usuário do contexto de segurança, o que mantém os eventos e as tarefas
 * agendadas funcionando sem pedido HTTP.
 */
package com.mydly.accounts;
