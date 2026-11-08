package com.mydly.accounts.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Conta as senhas erradas por usuário e por endereço de origem e bloqueia o login até a janela acabar
 * (ADR 0017). As contagens ficam em memória: a aplicação roda numa instância só, e reiniciar zera
 * tudo, o que só favorece quem errou a senha.
 *
 * <p>O bloqueio por usuário segura quem tenta adivinhar a senha de uma conta; o por endereço segura
 * quem tenta muitas contas de um mesmo lugar. O usuário de demonstração não tem bloqueio por usuário:
 * a senha dele é pública, e o bloqueio só serviria para alguém trancar a demonstração para todo mundo.
 * Por privacidade, nada aqui vai para o log.
 */
@Component
@EnableConfigurationProperties({LoginLimitProperties.class, DemoProperties.class})
class LoginAttempts {

    /** Acima disso, as janelas vencidas são apagadas antes de contar mais uma, e a memória não cresce sem fim. */
    static final int PRUNE_ABOVE = 10_000;

    private final LoginLimitProperties properties;
    private final String demoUsername;
    private final Clock clock;
    private final Map<String, Failures> byUser = new ConcurrentHashMap<>();
    private final Map<String, Failures> byAddress = new ConcurrentHashMap<>();

    LoginAttempts(LoginLimitProperties properties, DemoProperties demo, Clock clock) {
        this.properties = properties;
        this.demoUsername = key(demo.username());
        this.clock = clock;
    }

    /** As falhas de uma janela: quando ela começou e quantas foram. */
    private record Failures(Instant since, int count) {
    }

    /** Quanto falta para poder tentar de novo, ou vazio quando o login está liberado. */
    Optional<Duration> blockedFor(String username, String address) {
        Instant now = clock.instant();
        Optional<Duration> user = remaining(byUser.get(key(username)), properties.maxFailuresPerUser(), now);
        Optional<Duration> origin = address == null ? Optional.empty()
                : remaining(byAddress.get(address), properties.maxFailuresPerAddress(), now);
        if (user.isPresent() && origin.isPresent()) {
            return Optional.of(user.get().compareTo(origin.get()) >= 0 ? user.get() : origin.get());
        }
        return user.isPresent() ? user : origin;
    }

    /** Uma senha errada (ou um usuário que não existe, que conta igual). */
    void failed(String username, String address) {
        Instant now = clock.instant();
        if (!key(username).equals(demoUsername)) {
            add(byUser, key(username), now);
        }
        if (address != null) {
            add(byAddress, address, now);
        }
    }

    /** O login deu certo: as falhas do usuário são esquecidas. As do endereço ficam até a janela acabar. */
    void succeeded(String username) {
        byUser.remove(key(username));
    }

    private void add(Map<String, Failures> map, String key, Instant now) {
        if (map.size() > PRUNE_ABOVE) {
            map.values().removeIf(failures -> expired(failures, now));
        }
        map.merge(key, new Failures(now, 1),
                (current, first) -> expired(current, now) ? first : new Failures(current.since(), current.count() + 1));
    }

    private Optional<Duration> remaining(Failures failures, int max, Instant now) {
        if (failures == null || failures.count() < max || expired(failures, now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, failures.since().plus(properties.window())));
    }

    private boolean expired(Failures failures, Instant now) {
        return !now.isBefore(failures.since().plus(properties.window()));
    }

    private static String key(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
