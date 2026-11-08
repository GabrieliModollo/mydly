package com.mydly.accounts.internal;

import com.mydly.accounts.DemoReset;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Volta a demonstração ao exemplo (ADR 0017): ao subir e todo dia às 4h em São Paulo, quando
 * {@code mydly.demo.reset-enabled} está ligado. Visitantes compartilham o usuário demo, e assim ninguém
 * herda por mais de um dia o que outra pessoa escreveu.
 */
@Component
public class DemoResetJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoResetJob.class);

    /**
     * O fuso da demonstração. É o mesmo de {@code UserCalendar}, repetido aqui porque {@code accounts}
     * não depende de outro módulo.
     */
    static final String ZONE_ID = "America/Sao_Paulo";

    private final DemoProperties properties;
    private final AccountRepository accounts;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    DemoResetJob(DemoProperties properties, AccountRepository accounts, ApplicationEventPublisher events,
                 PlatformTransactionManager transactionManager, Clock clock) {
        this.properties = properties;
        this.accounts = accounts;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.resetEnabled()) {
            reset();
        }
    }

    @Scheduled(cron = "${mydly.demo.reset-cron:0 0 4 * * *}", zone = ZONE_ID)
    public void daily() {
        if (properties.resetEnabled()) {
            reset();
        }
    }

    /**
     * Apaga os dados do usuário de demonstração e grava a semana de exemplo, tudo numa transação: se
     * um módulo falhar, a demonstração fica como estava.
     *
     * @return {@code false} quando o usuário de demonstração não existe
     */
    public boolean reset() {
        Optional<Account> demo = accounts.findByUsername(properties.username());
        if (demo.isEmpty()) {
            log.warn("Reset da demonstração ignorado: o usuário de demonstração não existe.");
            return false;
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(ZONE_ID)));
        transactions.executeWithoutResult(status -> events.publishEvent(new DemoReset(demo.get().id(), today)));
        log.info("Demonstração restaurada para o exemplo de {}.", today);
        return true;
    }
}
