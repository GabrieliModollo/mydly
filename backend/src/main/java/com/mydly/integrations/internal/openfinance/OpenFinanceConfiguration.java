package com.mydly.integrations.internal.openfinance;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** O cliente do Pluggy (ADR 0015). Sem as chaves, ele existe mas nunca é chamado. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenFinanceProperties.class)
class OpenFinanceConfiguration {

    @Bean
    PluggyClient pluggyClient(RestClient.Builder builder, OpenFinanceProperties properties, Clock clock) {
        return new PluggyClient(PluggyClient.http(builder, properties), properties, clock);
    }
}
