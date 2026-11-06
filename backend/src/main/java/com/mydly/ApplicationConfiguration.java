package com.mydly;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("MyDly API")
                .version("v1")
                .description("Controle de rotinas e finanças com lançamento em linguagem natural. "
                        + "Escreva como falaria (\"Gastei 50 reais na farmácia\", \"Bebi 2 litros de água\") "
                        + "e o MyDly decide se é finança ou rotina.")
                .license(new License().name("Projeto de portfólio")));
    }
}
