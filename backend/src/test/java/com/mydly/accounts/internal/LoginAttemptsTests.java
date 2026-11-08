package com.mydly.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mydly.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** O limite de tentativas de login (ADR 0017): 3 por usuário e 5 por endereço em 10 minutos, neste teste. */
class LoginAttemptsTests {

    private static final Instant START = Instant.parse("2040-01-10T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final LoginAttempts attempts = new LoginAttempts(new LoginLimitProperties(3, 5, Duration.ofMinutes(10)),
            new DemoProperties(false, "demo"), clock);

    @Test
    void depoisDoLimiteDeSenhasErradasOUsuarioFicaBloqueadoAteAJanelaAcabar() {
        attempts.failed("ana", "10.0.0.1");
        attempts.failed("ana", "10.0.0.1");
        assertThat(attempts.blockedFor("ana", "10.0.0.1")).isEmpty();

        clock.set(START.plusSeconds(60));
        attempts.failed("ana", "10.0.0.1");

        assertThat(attempts.blockedFor("ana", "10.0.0.2")).contains(Duration.ofMinutes(9));
        clock.set(START.plus(Duration.ofMinutes(10)));
        assertThat(attempts.blockedFor("ana", "10.0.0.1")).isEmpty();
    }

    @Test
    void ninguemTrancaADemonstracaoErrandoASenhaDela() {
        for (int i = 0; i < 4; i++) {
            attempts.failed("Demo", "10.0.0." + i);
        }

        assertThat(attempts.blockedFor("demo", "10.0.0.9")).isEmpty();
        // O limite por endereço continua valendo para quem erra a senha da demonstração.
        for (int i = 0; i < 5; i++) {
            attempts.failed("demo", "10.0.0.5");
        }
        assertThat(attempts.blockedFor("demo", "10.0.0.5")).isPresent();
    }

    @Test
    void oUsuarioEContadoSemDiferenciarMaiusculasNemEspacos() {
        attempts.failed("Ana", null);
        attempts.failed(" ana ", null);
        attempts.failed("ANA", null);

        assertThat(attempts.blockedFor("ana", null)).isPresent();
    }

    @Test
    void oBloqueioDeUmUsuarioNaoAtingeOutro() {
        for (int i = 0; i < 3; i++) {
            attempts.failed("ana", "10.0.0.1");
        }

        assertThat(attempts.blockedFor("bia", "10.0.0.9")).isEmpty();
    }

    @Test
    void oLoginCertoEsqueceAsFalhasDoUsuario() {
        attempts.failed("ana", "10.0.0.1");
        attempts.failed("ana", "10.0.0.1");
        attempts.succeeded("ana");
        attempts.failed("ana", "10.0.0.1");
        attempts.failed("ana", "10.0.0.1");

        assertThat(attempts.blockedFor("ana", "10.0.0.1")).isEmpty();
    }

    @Test
    void muitasContasTentadasDoMesmoEnderecoBloqueiamOEndereco() {
        for (String user : new String[] {"a", "b", "c", "d", "e"}) {
            attempts.failed(user, "10.0.0.7");
        }

        assertThat(attempts.blockedFor("f", "10.0.0.7")).contains(Duration.ofMinutes(10));
        assertThat(attempts.blockedFor("f", "10.0.0.8")).isEmpty();
    }

    @Test
    void umaFalhaDepoisDaJanelaComecaUmaContagemNova() {
        attempts.failed("ana", null);
        attempts.failed("ana", null);
        clock.set(START.plus(Duration.ofMinutes(11)));
        attempts.failed("ana", null);
        attempts.failed("ana", null);

        assertThat(attempts.blockedFor("ana", null)).isEmpty();
    }

    @Test
    void oRetryAfterArredondaParaCimaENuncaEZero() {
        assertThat(new LoginBlockedException(Duration.ofMillis(1500)).retryAfterSeconds()).isEqualTo(2);
        assertThat(new LoginBlockedException(Duration.ZERO).retryAfterSeconds()).isEqualTo(1);
        assertThat(new LoginBlockedException(Duration.ofMinutes(15)).retryAfterSeconds()).isEqualTo(900);
    }
}
