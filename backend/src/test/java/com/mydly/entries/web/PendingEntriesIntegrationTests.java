package com.mydly.entries.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.TestUsers;
import com.mydly.TestcontainersConfiguration;
import com.mydly.accounts.internal.AccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Os lançamentos de outros dias que ainda pedem confirmação ({@code GET /api/v1/entries/pending}): sem
 * eles, um "Quanto foi?" de ontem ficaria pendente para sempre. Cada teste usa um usuário novo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PendingEntriesIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountRepository accounts;

    private RequestPostProcessor user;

    @BeforeEach
    void newUser() {
        user = TestUsers.as(accounts.create("pendentes-" + UUID.randomUUID().toString().substring(0, 8), "x").id());
    }

    @Test
    void listaSoOsPendentesDeDiasAnteriores() throws Exception {
        Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2));
        UUID yesterdayBread = create("Comprei pão", Instant.now().minus(Duration.ofDays(1)), user);
        UUID olderBread = create("Comprei leite", twoDaysAgo, user);
        create("Gastei 30 no açaí", twoDaysAgo, user);
        create("Comprei pão", Instant.now(), user);

        mvc.perform(get("/api/v1/entries/pending").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(yesterdayBread.toString()))
                .andExpect(jsonPath("$[1].id").value(olderBread.toString()))
                .andExpect(jsonPath("$[0].items[0].needsConfirmation").value(true));
    }

    @Test
    void depoisDeConfirmadoSaiDaLista() throws Exception {
        UUID id = create("Comprei pão", Instant.now().minus(Duration.ofDays(1)), user);

        mvc.perform(post("/api/v1/entries/{id}/items/0/resolution", id).with(user).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\": \"CONFIRM_FINANCE\", \"amount\": 8.50}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/entries/pending").with(user)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void naoMostraOsPendentesDeOutroUsuario() throws Exception {
        RequestPostProcessor other =
                TestUsers.as(accounts.create("outro-" + UUID.randomUUID().toString().substring(0, 8), "x").id());
        create("Comprei pão", Instant.now().minus(Duration.ofDays(1)), other);

        mvc.perform(get("/api/v1/entries/pending").with(user)).andExpect(jsonPath("$.length()").value(0));
    }

    private UUID create(String text, Instant occurredAt, RequestPostProcessor owner) throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/entries").with(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"%s\", \"text\": \"%s\", \"occurredAt\": \"%s\"}".formatted(id, text, occurredAt)))
                .andExpect(status().isCreated());
        return id;
    }
}
