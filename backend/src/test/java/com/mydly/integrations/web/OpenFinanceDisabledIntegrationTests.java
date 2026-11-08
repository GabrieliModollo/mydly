package com.mydly.integrations.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mydly.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Sem as chaves do Pluggy, o Open Finance fica desligado e o resto do app funciona igual (ADR 0015). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenFinanceDisabledIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void semChavesOOpenFinanceFicaDesligado() throws Exception {
        mvc.perform(get("/api/v1/open-finance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.connections.length()").value(0));
        mvc.perform(post("/api/v1/open-finance/connect-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("O Open Finance não está configurado neste servidor."));
        mvc.perform(post("/api/v1/open-finance/connections").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\": \"x\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
