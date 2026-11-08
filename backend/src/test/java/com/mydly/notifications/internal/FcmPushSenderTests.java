package com.mydly.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mydly.MutableClock;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * O envio pelo Firebase contra servidores simulados (ADR 0016): o JWT da conta de serviço, a troca
 * pelo token de acesso e o que cada resposta do FCM significa para o aparelho.
 */
class FcmPushSenderTests {

    private static final String TOKEN_URI = "https://oauth2.googleapis.test/token";
    private static final PushSender.Message MESSAGE = new PushSender.Message("Como foi o seu dia?", "Escreva.",
            Map.of("route", "/"));

    private MockRestServiceServer server;
    private FcmPushSender sender;
    private MutableClock clock;
    private RSAPublicKey publicKey;

    @BeforeEach
    void sender() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        publicKey = (RSAPublicKey) keys.getPublic();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock(Instant.parse("2026-11-08T12:00:00Z"));
        sender = new FcmPushSender(builder.build(), new FcmPushSender.ServiceAccount("mydly-teste",
                "push@mydly-teste.iam.gserviceaccount.com", pem, TOKEN_URI), clock);
    }

    private void expectToken(String accessToken) {
        server.expect(once(), requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess("{\"access_token\": \"" + accessToken + "\", \"expires_in\": 3600}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void oJwtDaContaDeServicoEAssinadoComOsCamposDoGoogle() throws Exception {
        SignedJWT jwt = SignedJWT.parse(sender.assertion(clock.instant()));

        assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("push@mydly-teste.iam.gserviceaccount.com");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(TOKEN_URI);
        assertThat(jwt.getJWTClaimsSet().getStringClaim("scope")).isEqualTo(FcmPushSender.SCOPE);
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant())
                .isEqualTo(clock.instant().plusSeconds(3600));
    }

    @Test
    void enviaPeloFcmEReaproveitaOTokenDeAcesso() {
        expectToken("acesso-1");
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo("https://fcm.googleapis.com/v1/projects/mydly-teste/messages:send"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer acesso-1"))
                    .andExpect(content().json("""
                            {"message": {"token": "aparelho-1",
                              "notification": {"title": "Como foi o seu dia?", "body": "Escreva."},
                              "data": {"route": "/"}}}"""))
                    .andRespond(withSuccess("{\"name\": \"projects/mydly-teste/messages/1\"}",
                            MediaType.APPLICATION_JSON));
        }

        assertThat(sender.send("aparelho-1", MESSAGE)).isEqualTo(PushSender.Result.SENT);
        assertThat(sender.send("aparelho-1", MESSAGE)).isEqualTo(PushSender.Result.SENT);
        server.verify();
    }

    @Test
    void tokenDeAcessoRenovadoDepoisDoPrazo() {
        expectToken("acesso-1");
        expectToken("acesso-2");

        assertThat(sender.accessToken()).isEqualTo("acesso-1");
        clock.set(clock.instant().plus(FcmPushSender.ACCESS_TOKEN_REUSE).plusSeconds(1));
        assertThat(sender.accessToken()).isEqualTo("acesso-2");
    }

    @Test
    void aparelhoDesregistradoOuDeOutroProjetoTemOTokenInvalido() {
        expectToken("acesso-1");
        server.expect(requestTo("https://fcm.googleapis.com/v1/projects/mydly-teste/messages:send"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://fcm.googleapis.com/v1/projects/mydly-teste/messages:send"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(sender.send("velho", MESSAGE)).isEqualTo(PushSender.Result.INVALID_TOKEN);
        assertThat(sender.send("outro-projeto", MESSAGE)).isEqualTo(PushSender.Result.INVALID_TOKEN);
    }

    @Test
    void outraFalhaNaoApagaOAparelho() {
        expectToken("acesso-1");
        server.expect(requestTo("https://fcm.googleapis.com/v1/projects/mydly-teste/messages:send"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://fcm.googleapis.com/v1/projects/mydly-teste/messages:send"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(sender.send("aparelho-1", MESSAGE)).isEqualTo(PushSender.Result.FAILED);
        assertThat(sender.send("aparelho-1", MESSAGE)).isEqualTo(PushSender.Result.FAILED);
    }

    @Test
    void googleRecusandoACredencialEFalhaSemExcecao() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(sender.send("aparelho-1", MESSAGE)).isEqualTo(PushSender.Result.FAILED);
    }
}
