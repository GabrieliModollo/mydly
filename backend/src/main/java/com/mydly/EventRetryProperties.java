package com.mydly;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Nova tentativa dos eventos entre módulos que ficaram sem concluir (ADR 0008).
 *
 * @param retryInterval de quanto em quanto tempo a nova tentativa roda
 * @param retryOlderThan só reenvia eventos publicados há mais que isso, para não disputar com a
 *                       entrega normal que ainda está em andamento
 * @param staleAfter a partir de quanto tempo um evento incompleto deixa a saúde da aplicação
 *                   {@code OUT_OF_SERVICE}
 */
@ConfigurationProperties(prefix = "mydly.events")
record EventRetryProperties(
        @DefaultValue("PT1M") Duration retryInterval,
        @DefaultValue("PT1M") Duration retryOlderThan,
        @DefaultValue("PT10M") Duration staleAfter) {
}
