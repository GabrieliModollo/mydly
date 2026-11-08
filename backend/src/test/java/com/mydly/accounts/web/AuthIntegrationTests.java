package com.mydly.accounts.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydly.TestUsers;
import com.mydly.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Login e proteção da API (etapa 9, ADR 0012), com o filtro de segurança de verdade: aqui o MockMvc não
 * entra como o usuário demo, e o token precisa passar pela assinatura, pelo emissor e pela validade.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private Clock clock;

    @Autowired
    private ObjectMapper json;

    private MockMvc mvc;

    @BeforeEach
    void withoutDefaultUser() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void oLoginDoDemoDevolveUmTokenQueAbreAApi() throws Exception {
        String token = token("demo", "demo");

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TestUsers.DEMO.toString()))
                .andExpect(jsonPath("$.username").value("demo"));
        mvc.perform(get("/api/v1/entries").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void oNomeDeLoginNaoDiferenciaMaiusculas() throws Exception {
        login("  DEMO ", "demo").andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource({"demo, errada", "ninguem, demo", "demo, DEMO"})
    void usuarioInexistenteESenhaErradaTemAMesmaResposta(String username, String password) throws Exception {
        login(username, password)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuário ou senha incorretos."))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void senhasErradasDemaisDao429ComRetryAfter() throws Exception {
        // Um usuário e um endereço só deste teste, para o bloqueio não atingir os outros.
        String username = "tentativas-" + UUID.randomUUID().toString().substring(0, 8);
        // Pelo app nativo, que só lê o Retry-After se o CORS o expuser.
        RequestPostProcessor from = request -> {
            request.setRemoteAddr("192.0.2.14");
            request.addHeader(HttpHeaders.ORIGIN, "capacitor://localhost");
            return request;
        };
        for (int i = 0; i < 5; i++) {
            login(username, "errada", from).andExpect(status().isUnauthorized());
        }

        login(username, "errada", from)
                .andExpect(status().isTooManyRequests())
                // A janela é de 15 minutos e começou na primeira falha, há menos de um minuto.
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("8[4-9][0-9]|900")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")))
                .andExpect(jsonPath("$.detail").value("Muitas tentativas de login. Tente de novo em alguns minutos."));
        login("demo", "demo").andExpect(status().isOk());
    }

    @Test
    void usuarioOuSenhaEmBrancoE400() throws Exception {
        login("", "demo").andExpect(status().isBadRequest());
        login("demo", " ").andExpect(status().isBadRequest());
    }

    @Test
    void semTokenAApiResponde401() throws Exception {
        for (String path : new String[] {"/api/v1/entries", "/api/v1/days/2026-11-05/summary",
                "/api/v1/weeks/current/summary", "/api/v1/goals/habits", "/api/v1/budgets", "/api/v1/preferences",
                "/api/v1/auth/me"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/entries").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"50 reais\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAssinadoComOutroSegredoE401() throws Exception {
        String token = token("demo", "demo");
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1] + ".assinaturaInvalida";

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoE401() throws Exception {
        Instant now = clock.instant();
        String expired = encode(JwtClaimsSet.builder().issuer("mydly").subject(TestUsers.DEMO.toString())
                .issuedAt(now.minus(Duration.ofDays(8))).expiresAt(now.minus(Duration.ofDays(1))).build());

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, not(emptyOrNullString())));
    }

    @Test
    void tokenDeOutroEmissorE401() throws Exception {
        Instant now = clock.instant();
        String foreign = encode(JwtClaimsSet.builder().issuer("outro").subject(TestUsers.DEMO.toString())
                .issuedAt(now).expiresAt(now.plus(Duration.ofHours(1))).build());

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oTokenValeSeteDias() throws Exception {
        JsonNode body = json.readTree(login("demo", "demo").andReturn().getResponse().getContentAsString());

        Instant expiresAt = Instant.parse(body.get("expiresAt").asText());
        Instant inSevenDays = clock.instant().plus(Duration.ofDays(7));
        assertThat(expiresAt).isBetween(inSevenDays.minusSeconds(60), inSevenDays.plusSeconds(60));
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
    }

    @Test
    void saudeESwaggerNaoPedemToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }

    private ResultActions login(String username, String password) throws Exception {
        return login(username, password, request -> request);
    }

    private ResultActions login(String username, String password, RequestPostProcessor from) throws Exception {
        String body = json.writeValueAsString(Map.of("username", username, "password", password));
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body).with(from));
    }

    private String token(String username, String password) throws Exception {
        String response = login(username, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("demo"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
