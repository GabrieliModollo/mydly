package com.mydly.entries.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * A cota diária de chamadas ao LLM (ADR 0017). O dia é o de São Paulo, como o dos lançamentos.
 *
 * @param perUser quantas chamadas cada usuário faz por dia
 * @param total   quantas chamadas todos os usuários fazem por dia, somados: o teto do custo
 */
@ConfigurationProperties("mydly.llm.daily-limit")
record LlmQuotaProperties(@DefaultValue("30") int perUser, @DefaultValue("300") int total) {
}
