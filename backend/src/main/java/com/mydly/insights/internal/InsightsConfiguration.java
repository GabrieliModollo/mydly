package com.mydly.insights.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do módulo insights.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WeekDefinition.class)
class InsightsConfiguration {
}
