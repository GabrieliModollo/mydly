package com.mydly;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Todo pedido do MockMvc sai autenticado como o usuário demo, para os testes de cada módulo não
 * repetirem o login. Um teste pede outro usuário com {@code .with(TestUsers.as(id))} ou nenhum com
 * {@code .with(anonymous())}: o processador do próprio pedido roda depois deste e vale.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

    @Bean
    MockMvcBuilderCustomizer demoUserByDefault() {
        return builder -> builder.defaultRequest(get("/").with(TestUsers.as(TestUsers.DEMO)));
    }
}
