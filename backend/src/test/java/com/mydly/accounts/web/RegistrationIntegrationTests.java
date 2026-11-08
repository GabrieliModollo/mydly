package com.mydly.accounts.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydly.GoalsTestData;
import com.mydly.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Cadastro de conta (ADR 0018), com o filtro de segurança de verdade: cada conta nova entra com o token
 * que o cadastro devolve, e duas contas não veem os dados uma da outra.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegistrationIntegrationTests {

    /** Um dia só destes testes, longe dos dias que os outros testes usam. */
    private static final LocalDate DAY = LocalDate.of(2042, 7, 9);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcClient jdbc;

    private MockMvc mvc;

    @BeforeEach
    void withoutDefaultUser() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        GoalsTestData.reset(jdbc);
    }

    @AfterEach
    void reset() {
        GoalsTestData.reset(jdbc);
    }

    @Test
    void oCadastroDevolveUmTokenQueJaAbreAApi() throws Exception {
        String username = uniqueName("ana");
        JsonNode body = read(register(username, "uma-senha-boa")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value(username)));

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(body.at("/user/id").asText()))
                .andExpect(jsonPath("$.username").value(username));
        mvc.perform(get("/api/v1/entries").header(HttpHeaders.AUTHORIZATION, bearer(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void depoisDoCadastroOLoginFuncionaComAMesmaSenha() throws Exception {
        String username = uniqueName("bia");
        register(username, "uma-senha-boa").andExpect(status().isCreated());

        login(username.toUpperCase(), "uma-senha-boa").andExpect(status().isOk());
        login(username, "outra-senha").andExpect(status().isUnauthorized());
    }

    @Test
    void oNomeEGuardadoEmMinusculasESemEspacosESoOHashDaSenhaVaiAoBanco() throws Exception {
        String username = uniqueName("caio");
        register("  " + username.toUpperCase() + " ", "uma-senha-boa")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value(username));

        String hash = jdbc.sql("SELECT password_hash FROM app_user WHERE username = ?").param(username)
                .query(String.class).single();
        assertThat(hash).startsWith("$2").doesNotContain("uma-senha-boa");
    }

    @Test
    void usuarioQueJaExisteE409MesmoComOutrasMaiusculas() throws Exception {
        String username = uniqueName("davi");
        register(username, "uma-senha-boa").andExpect(status().isCreated());

        refused(register(username.toUpperCase(), "outra-senha-boa"), 409, "USERNAME_TAKEN");
        refused(register("demo", "outra-senha-boa"), 409, "USERNAME_TAKEN")
                .andExpect(jsonPath("$.detail").value("Esse usuário já existe. Escolha outro."))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "  ", "ab", "com espaço", "joão", "-comeca-com-hifen", "a@b",
            "um-nome-de-usuario-com-mais-de-40-letras-x"})
    void usuarioInvalidoE400(String username) throws Exception {
        refused(register(username, "uma-senha-boa"), 400, "INVALID_USERNAME");
    }

    @Test
    void usuarioOuSenhaAusenteE400() throws Exception {
        refused(send(Map.of("password", "uma-senha-boa")), 400, "INVALID_USERNAME");
        refused(send(Map.of("username", uniqueName("eva"))), 400, "PASSWORD_TOO_SHORT");
    }

    @Test
    void senhaCurtaE400() throws Exception {
        refused(register(uniqueName("fabi"), "1234567"), 400, "PASSWORD_TOO_SHORT")
                .andExpect(jsonPath("$.detail").value("A senha precisa de pelo menos 8 caracteres."));
    }

    @Test
    void senhaAlemDoQueOBcryptUsaE400() throws Exception {
        // 37 letras com acento são 74 bytes: o BCrypt cortaria a senha sem avisar.
        refused(register(uniqueName("gil"), "ç".repeat(37)), 400, "PASSWORD_TOO_LONG");
        register(uniqueName("gil"), "ç".repeat(36)).andExpect(status().isCreated());
    }

    @Test
    void duasContasCadastradasNaoVeemOsDadosUmaDaOutra() throws Exception {
        JsonNode ana = read(register(uniqueName("ana"), "senha-da-ana").andExpect(status().isCreated()));
        JsonNode bia = read(register(uniqueName("bia"), "senha-da-bia").andExpect(status().isCreated()));
        UUID expense = UUID.randomUUID();
        UUID water = UUID.randomUUID();

        // Ana lança um gasto e um hábito e define meta e limite.
        createEntry(ana, expense, "Gastei 50 reais na farmácia").andExpect(status().isCreated());
        createEntry(ana, water, "Bebi 2 litros de água").andExpect(status().isCreated());
        mvc.perform(as(ana, put("/api/v1/goals/habits/{habit}", "HYDRATION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"DAILY_QUANTITY\", \"targetQuantity\": 2, \"unit\": \"L\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(ana, put("/api/v1/budgets/{category}", "HEALTH"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"period\": \"WEEKLY\", \"limit\": 300}"))
                .andExpect(status().isOk());

        // Lançamentos: Bia não lista nem abre os da Ana.
        mvc.perform(as(ana, get("/api/v1/entries").param("date", DAY.toString())))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(as(bia, get("/api/v1/entries").param("date", DAY.toString())))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(as(bia, get("/api/v1/entries/{id}", expense))).andExpect(status().isNotFound());

        // Transações e hábitos: os números do dia da Bia ficam zerados.
        mvc.perform(as(ana, get("/api/v1/days/{date}/summary", DAY)))
                .andExpect(jsonPath("$.spent").value(50))
                .andExpect(jsonPath("$.habitsAchieved").value(1));
        mvc.perform(as(bia, get("/api/v1/days/{date}/summary", DAY)))
                .andExpect(jsonPath("$.spent").value(0))
                .andExpect(jsonPath("$.habitsAchieved").value(0));
        assertThat(rows("finance_transaction", ana)).isEqualTo(1);
        assertThat(rows("habit_log", ana)).isEqualTo(1);
        assertThat(rows("finance_transaction", bia)).isZero();
        assertThat(rows("habit_log", bia)).isZero();

        // Metas e limites: os da Ana não aparecem para a Bia.
        mvc.perform(as(ana, get("/api/v1/goals/habits/{habit}", "HYDRATION")))
                .andExpect(jsonPath("$.goal.targetQuantity").value(2));
        mvc.perform(as(bia, get("/api/v1/goals/habits/{habit}", "HYDRATION")))
                .andExpect(jsonPath("$.goal").doesNotExist());
        mvc.perform(as(ana, get("/api/v1/budgets/{category}", "HEALTH")))
                .andExpect(jsonPath("$.limit").value(300));
        mvc.perform(as(bia, get("/api/v1/budgets/{category}", "HEALTH")))
                .andExpect(jsonPath("$.limit").doesNotExist());

        // O mesmo id de lançamento vindo da Bia é conflito, sem devolver nada da Ana.
        createEntry(bia, expense, "Gastei 50 reais na farmácia")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.text").doesNotExist());
    }

    private ResultActions register(String username, String password) throws Exception {
        return send(Map.of("username", username, "password", password));
    }

    private ResultActions send(Map<String, String> body) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new HashMap<>(body))));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private static ResultActions refused(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private ResultActions createEntry(JsonNode user, UUID id, String text) throws Exception {
        return mvc.perform(as(user, post("/api/v1/entries")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": \"" + id + "\", \"text\": \"" + text + "\", \"occurredAt\": \"" + DAY
                        + "T15:00:00Z\"}"));
    }

    private int rows(String table, JsonNode user) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = ? AND active")
                .param(UUID.fromString(user.at("/user/id").asText()))
                .query(Integer.class).single();
    }

    private static MockHttpServletRequestBuilder as(JsonNode user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    private static String bearer(JsonNode registration) {
        return "Bearer " + registration.get("accessToken").asText();
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Um nome que nenhum outro teste usou, para os testes não dependerem da ordem. */
    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
