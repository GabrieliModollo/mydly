package com.mydly.entries.internal;

import java.util.List;
import java.util.Optional;

/**
 * O LLM de fallback do parser (ADR 0003 e ADR 0013), atrás de uma interface para o parser não
 * depender de um fornecedor e para os testes rodarem sem rede.
 */
interface LlmInterpreter {

    /** Sem chave de LLM configurada: o parser fica só com as regras locais. */
    LlmInterpreter DISABLED = new LlmInterpreter() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public Optional<List<LlmReading>> read(String text, List<String> segments) {
            return Optional.empty();
        }
    };

    boolean enabled();

    /**
     * Lê os trechos de um lançamento que as regras não entenderam.
     *
     * @param text     o lançamento inteiro, só como contexto; é o único dado do usuário enviado
     * @param segments os trechos a interpretar, na ordem
     * @return as leituras, que podem vir incompletas ou fora de ordem; vazio quando a chamada falhou,
     *         demorou demais ou o modelo recusou, e aí valem as regras
     */
    Optional<List<LlmReading>> read(String text, List<String> segments);
}
