package com.mydly.entries.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** O LLM só liga com chave; sem ela o app funciona só com as regras (ADR 0003, item 6). */
class LlmConfigurationTests {

    private final LlmConfiguration configuration = new LlmConfiguration();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void semChaveOParserFicaSoComAsRegras(String key) {
        LlmInterpreter interpreter = configuration.llmInterpreter(
                new LlmProperties(key, "claude-opus-5-5", Duration.ofSeconds(15)));

        assertThat(interpreter).isSameAs(LlmInterpreter.DISABLED);
        assertThat(interpreter.enabled()).isFalse();
    }

    @Test
    void comChaveUsaOClaude() {
        LlmInterpreter interpreter = configuration.llmInterpreter(
                new LlmProperties("sk-ant-teste", "claude-opus-5-5", Duration.ofSeconds(15)));

        assertThat(interpreter).isInstanceOf(ClaudeInterpreter.class);
        assertThat(interpreter.enabled()).isTrue();
    }

    @Test
    void aChaveNaoApareceEmLog() {
        assertThat(new LlmProperties("sk-ant-segredo", "claude-opus-5-5", Duration.ofSeconds(15)).toString())
                .doesNotContain("sk-ant-segredo");
    }
}
