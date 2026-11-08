package com.mydly.notifications.internal;

import com.mydly.notifications.internal.NotificationRepository.Kind;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Manda uma notificação para os aparelhos de um usuário, uma vez só por tipo e assunto (ADR 0016).
 *
 * <p>A marca de enviada é gravada antes do envio, para duas rodadas ou duas instâncias não mandarem
 * em dobro. Se nenhum aparelho recebeu por falha do Firebase, a marca é desfeita e a próxima rodada
 * tenta de novo. Um token que o Firebase recusa sai da lista.
 */
@Component
class Notifier {

    private final NotificationRepository repository;
    private final PushSender sender;

    Notifier(NotificationRepository repository, PushSender sender) {
        this.repository = repository;
        this.sender = sender;
    }

    boolean enabled() {
        return sender.enabled();
    }

    /** {@code true} quando ao menos um aparelho recebeu. */
    boolean notify(UUID userId, Kind kind, String key, PushSender.Message message) {
        if (!sender.enabled()) {
            return false;
        }
        List<String> tokens = repository.tokens(userId);
        if (tokens.isEmpty() || !repository.markSent(userId, kind, key)) {
            return false;
        }
        boolean delivered = false;
        boolean failed = false;
        for (String token : tokens) {
            switch (sender.send(token, message)) {
                case SENT -> delivered = true;
                case INVALID_TOKEN -> repository.removeToken(token);
                case FAILED -> failed = true;
            }
        }
        if (!delivered && failed) {
            repository.unmarkSent(userId, kind, key);
        }
        return delivered;
    }
}
