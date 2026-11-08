package com.mydly.notifications.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Envio de push pelo Firebase Cloud Messaging, API HTTP v1 (ADR 0016), sem SDK do Firebase.
 *
 * <p>A autenticação segue o fluxo de conta de serviço do Google: um JWT RS256 assinado com a chave da
 * conta, com o escopo {@code firebase.messaging}, trocado em {@code oauth2.googleapis.com/token} por um
 * token de acesso de 1 hora, reaproveitado por 50 minutos. A assinatura usa o Nimbus, que já vem com o
 * Spring Security. Por privacidade, nada aqui registra em log o token do aparelho nem o texto enviado.
 */
class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    static final String FCM_URL = "https://fcm.googleapis.com/v1/projects/{project}/messages:send";
    static final Duration ACCESS_TOKEN_REUSE = Duration.ofMinutes(50);

    private final RestClient http;
    private final ServiceAccount account;
    private final PrivateKey key;
    private final Clock clock;

    private String accessToken;
    private Instant accessTokenUntil = Instant.MIN;

    /** O JSON da conta de serviço, só os campos usados. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ServiceAccount(@JsonProperty("project_id") String projectId,
                          @JsonProperty("client_email") String clientEmail,
                          @JsonProperty("private_key") String privateKey,
                          @JsonProperty("token_uri") String tokenUri) {

        @Override
        public String toString() {
            // A chave privada não aparece em log nem em mensagem de erro.
            return "ServiceAccount[projectId=" + projectId + "]";
        }
    }

    FcmPushSender(RestClient http, ServiceAccount account, Clock clock) {
        this.http = http;
        this.account = account;
        this.key = privateKey(account.privateKey());
        this.clock = clock;
    }

    /** Lê a conta de serviço do arquivo baixado no console do Firebase. */
    static ServiceAccount read(Path file) {
        try {
            ServiceAccount account = new ObjectMapper().readValue(Files.readAllBytes(file), ServiceAccount.class);
            if (account.projectId() == null || account.clientEmail() == null || account.privateKey() == null) {
                throw new IllegalStateException("O JSON da conta de serviço do Firebase está incompleto.");
            }
            return account;
        } catch (IOException unreadable) {
            throw new IllegalStateException("Não foi possível ler a conta de serviço do Firebase.", unreadable);
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Result send(String token, Message message) {
        Map<String, Object> body = Map.of("message", Map.of(
                "token", token,
                "notification", Map.of("title", message.title(), "body", message.body()),
                "data", message.data()));
        try {
            http.post().uri(FCM_URL, account.projectId())
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden gone) {
            // UNREGISTERED (404) ou SENDER_ID_MISMATCH (403): o token não serve mais para este projeto.
            return Result.INVALID_TOKEN;
        } catch (RestClientException | IllegalStateException failure) {
            log.warn("Falha ao enviar push pelo Firebase: {}", failure.getClass().getSimpleName());
            return Result.FAILED;
        }
    }

    /** O token de acesso do Google, renovado quando passa do prazo. */
    synchronized String accessToken() {
        Instant now = clock.instant();
        if (accessToken == null || !now.isBefore(accessTokenUntil)) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            form.add("assertion", assertion(now));
            TokenResponse response = http.post().uri(tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("O Google respondeu sem token de acesso.");
            }
            accessToken = response.accessToken();
            accessTokenUntil = now.plus(ACCESS_TOKEN_REUSE);
        }
        return accessToken;
    }

    /** O JWT da conta de serviço: válido por uma hora, o máximo que o Google aceita. */
    String assertion(Instant now) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(account.clientEmail())
                .audience(tokenUri())
                .claim("scope", SCOPE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(1))))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException signing) {
            throw new IllegalStateException("Não foi possível assinar o JWT da conta de serviço.", signing);
        }
        return jwt.serialize();
    }

    private String tokenUri() {
        return account.tokenUri() != null ? account.tokenUri() : "https://oauth2.googleapis.com/token";
    }

    private static PrivateKey privateKey(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw new IllegalStateException("A chave privada da conta de serviço do Firebase é inválida.", invalid);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }
}
