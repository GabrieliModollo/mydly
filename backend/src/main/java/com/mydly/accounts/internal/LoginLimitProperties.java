package com.mydly.accounts.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * O limite de tentativas de login (ADR 0017).
 *
 * @param maxFailuresPerUser    quantas senhas erradas um mesmo usuário aceita dentro da janela
 * @param maxFailuresPerAddress quantas senhas erradas um mesmo endereço de origem aceita dentro da janela,
 *                              somando todos os usuários que ele tentar
 * @param window                a janela: ela começa na primeira falha, e o bloqueio dura até ela acabar
 */
@ConfigurationProperties("mydly.auth.login-limit")
record LoginLimitProperties(@DefaultValue("5") int maxFailuresPerUser, @DefaultValue("20") int maxFailuresPerAddress,
                            @DefaultValue("PT15M") Duration window) {
}
