package com.mydly.entries.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do LLM de fallback do parser (ADR 0013).
 *
 * @param apiKey  chave da API da Anthropic; vazia desliga o LLM e o parser fica só com as regras
 * @param model   o modelo chamado
 * @param timeout quanto o lançamento espera o LLM antes de seguir com as regras
 */
@ConfigurationProperties("mydly.llm")
record LlmProperties(String apiKey, @DefaultValue("claude-opus-5-5") String model,
                     @DefaultValue("PT15S") Duration timeout) {

    boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        // A chave não aparece em log nem em mensagem de erro.
        return "LlmProperties[model=" + model + ", timeout=" + timeout + ", enabled=" + enabled() + "]";
    }
}
