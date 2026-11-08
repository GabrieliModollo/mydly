package com.mydly.entries.internal;

import java.util.UUID;

/**
 * A cota diária de chamadas ao LLM (ADR 0017): cada lançamento que precisaria do LLM pede uma vaga
 * antes da chamada. Sem vaga, o lançamento fica com o que as regras entenderam e o app pergunta ao
 * usuário, como acontece com o LLM desligado.
 */
interface LlmQuota {

    /** Sem limite, para os testes do parser. */
    LlmQuota UNLIMITED = userId -> true;

    /**
     * Reserva uma chamada para o usuário hoje.
     *
     * @return {@code false} quando a cota dele ou a de todos acabou
     */
    boolean tryAcquire(UUID userId);
}
