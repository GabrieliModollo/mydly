package com.mydly.notifications.internal;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Liga o envio de push só com a conta de serviço do Firebase (ADR 0016). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FcmProperties.class)
class NotificationsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NotificationsConfiguration.class);

    @Bean
    PushSender pushSender(FcmProperties properties, RestClient.Builder builder, Clock clock) {
        if (!properties.enabled()) {
            log.info("Push desligado: defina FCM_SERVICE_ACCOUNT_FILE para enviar notificações pelo Firebase.");
            return PushSender.DISABLED;
        }
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requests.setReadTimeout(properties.timeout());
        FcmPushSender.ServiceAccount account = FcmPushSender.read(Path.of(properties.serviceAccountFile()));
        log.info("Push pelo Firebase ligado: {}", account);
        return new FcmPushSender(builder.requestFactory(requests).build(), account, clock);
    }
}
