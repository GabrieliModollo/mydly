package com.mydly.integrations;

/** A fonte não pôde ser lida. A mensagem é para o usuário e nunca traz valores nem descrições. */
public class InvalidSourceException extends RuntimeException {

    public InvalidSourceException(String message) {
        super(message);
    }
}
