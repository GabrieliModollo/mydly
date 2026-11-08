package com.mydly.accounts.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A demonstração pública (ADR 0017).
 *
 * @param resetEnabled liga o reset diário e o reset ao subir. Desligado por padrão: o reset apaga
 *                     tudo do usuário de demonstração, inclusive o que alguém tenha lançado nele de verdade
 * @param username     o usuário de demonstração
 */
@ConfigurationProperties("mydly.demo")
record DemoProperties(@DefaultValue("false") boolean resetEnabled, @DefaultValue("demo") String username) {
}
