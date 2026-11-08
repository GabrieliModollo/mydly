package com.mydly.notifications.internal;

import java.util.Map;
import java.util.Objects;

/**
 * Porta de envio de push (ADR 0016). O adaptador de verdade é o Firebase Cloud Messaging; sem a
 * conta de serviço configurada, o envio fica desligado e nenhuma notificação é marcada como enviada.
 */
interface PushSender {

    /** Sem Firebase configurado: nada é enviado. */
    PushSender DISABLED = new PushSender() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public Result send(String token, Message message) {
            return Result.FAILED;
        }
    };

    boolean enabled();

    /** Envia para um aparelho. Nunca lança exceção: a falha vem no resultado. */
    Result send(String token, Message message);

    enum Result {
        /** Entregue ao Firebase. */
        SENT,
        /** O token não vale mais (app desinstalado, token trocado): o aparelho sai da lista. */
        INVALID_TOKEN,
        /** Outra falha; a próxima rodada tenta de novo quando fizer sentido. */
        FAILED
    }

    /**
     * Uma notificação.
     *
     * @param data dados para o app abrir a tela certa ao tocar, como {@code route}
     */
    record Message(String title, String body, Map<String, String> data) {

        public Message {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
            data = Map.copyOf(data);
        }
    }
}
