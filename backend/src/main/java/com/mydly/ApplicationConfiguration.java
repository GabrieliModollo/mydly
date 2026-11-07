package com.mydly;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Beans compartilhados entre os módulos. O agendamento existe para a nova tentativa dos eventos
 * incompletos ({@link IncompleteEventsRetry}).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(EventRetryProperties.class)
class ApplicationConfiguration {

    /** Relógio injetável, para os testes controlarem o tempo quando precisarem. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Documentação da API. Todo endpoint pede o token (ADR 0012), menos o login: no Swagger, entre por
     * {@code POST /api/v1/auth/login} e cole o {@code accessToken} no botão Authorize.
     */
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                .info(new Info()
                .title("MyDly API")
                .version("v1")
                .description("Controle de rotinas e finanças com lançamento em linguagem natural. "
                        + "Escreva como falaria (\"Gastei 50 reais na farmácia\", \"Bebi 2 litros de água\") "
                        + "e o MyDly decide se é finança ou rotina.")
                .license(new License().name("Projeto de portfólio")));
    }
}
