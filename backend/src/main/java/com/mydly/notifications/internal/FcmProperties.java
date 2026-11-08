package com.mydly.notifications.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Firebase Cloud Messaging (ADR 0016).
 *
 * @param serviceAccountFile caminho do JSON da conta de serviço do projeto Firebase; vazio desliga o envio
 * @param timeout            quanto cada chamada ao Firebase espera
 */
@ConfigurationProperties("mydly.notifications.fcm")
record FcmProperties(String serviceAccountFile, @DefaultValue("PT10S") Duration timeout) {

    boolean enabled() {
        return serviceAccountFile != null && !serviceAccountFile.isBlank();
    }
}
