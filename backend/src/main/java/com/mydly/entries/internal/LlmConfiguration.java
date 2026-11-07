package com.mydly.entries.internal;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga o LLM de fallback do parser só quando há chave (ADR 0013). Sem ela, o app funciona só com as
 * regras locais, como pede o ADR 0003.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties.class)
class LlmConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmConfiguration.class);

    @Bean
    LlmInterpreter llmInterpreter(LlmProperties properties) {
        if (!properties.enabled()) {
            log.info("Parser sem LLM: defina LLM_API_KEY para interpretar as frases que as regras não entendem.");
            return LlmInterpreter.DISABLED;
        }
        log.info("Parser com LLM de fallback: {}", properties.model());
        return new ClaudeInterpreter(AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                // Uma nova tentativa só: o usuário está esperando o lançamento aparecer.
                .maxRetries(1)
                .build(), properties.model());
    }
}
