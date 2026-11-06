package com.mydly;

import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reenvia, de tempos em tempos, os eventos entre módulos que ficaram sem concluir (ADR 0008).
 *
 * <p>Os consumidores rodam logo depois do commit, para o app ver o número novo na hora. Se um deles
 * falhar (banco ocupado, um deadlock), o lançamento já está gravado, mas fica fora dos totais. Sem
 * esta tarefa, o evento só seria reenviado quando a aplicação reiniciasse. Reenviar é seguro: os
 * consumidores ignoram revisões que já aplicaram.
 */
@Component
public class IncompleteEventsRetry {

    private final IncompleteEventPublications incomplete;
    private final EventRetryProperties properties;

    IncompleteEventsRetry(IncompleteEventPublications incomplete, EventRetryProperties properties) {
        this.incomplete = incomplete;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${mydly.events.retry-interval:PT1M}",
            initialDelayString = "${mydly.events.retry-interval:PT1M}")
    public void retry() {
        incomplete.resubmitIncompletePublicationsOlderThan(properties.retryOlderThan());
    }
}
