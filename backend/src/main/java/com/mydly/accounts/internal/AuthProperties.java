package com.mydly.accounts.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do login (ADR 0012).
 *
 * @param jwtSecret segredo de assinatura dos tokens (HS256) com pelo menos 32 bytes; vazio gera um
 *                  segredo aleatório a cada subida, bom para desenvolvimento, e os tokens antigos deixam
 *                  de valer quando a aplicação reinicia
 * @param tokenTtl  por quanto tempo um token vale
 */
@ConfigurationProperties("mydly.auth")
record AuthProperties(String jwtSecret, @DefaultValue("P7D") Duration tokenTtl) {

    /** Emissor gravado e conferido em todo token. */
    static final String ISSUER = "mydly";
}
