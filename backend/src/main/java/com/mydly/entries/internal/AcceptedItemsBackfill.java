package com.mydly.entries.internal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica uma vez só os itens que já valiam antes de existirem os módulos {@code finance} e
 * {@code routines}, para eles nascerem com o histórico (ADR 0008).
 *
 * <p>A tarefa fica marcada em {@code entry_one_time_task} na mesma transação dos eventos. Se ela
 * rodar de novo (duas instâncias subindo juntas, por exemplo), os consumidores ignoram as revisões
 * que já aplicaram, então nada é duplicado.
 */
@Component
class AcceptedItemsBackfill implements ApplicationRunner {

    static final String TASK = "publish-accepted-items-v1";

    private final EntryRepository repository;
    private final ApplicationEventPublisher events;
    private final JdbcClient jdbc;

    AcceptedItemsBackfill(EntryRepository repository, ApplicationEventPublisher events, JdbcClient jdbc) {
        this.repository = repository;
        this.events = events;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean done = jdbc.sql("SELECT count(*) FROM entry_one_time_task WHERE name = ?")
                .param(TASK).query(Long.class).single() > 0;
        if (!done) {
            publishAll();
            jdbc.sql("INSERT INTO entry_one_time_task (name) VALUES (?) ON CONFLICT DO NOTHING").param(TASK).update();
        }
    }

    /** Publica de novo os itens aceitos de todos os lançamentos. Repetir é seguro. */
    void publishAll() {
        repository.findAll().forEach(entry -> entry.acceptedItems().forEach(events::publishEvent));
    }
}
