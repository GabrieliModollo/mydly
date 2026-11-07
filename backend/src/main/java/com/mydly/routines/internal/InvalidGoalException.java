package com.mydly.routines.internal;

/**
 * Meta recusada: tipo, unidade ou valor que não combinam com o hábito. A API responde 400 com a
 * mensagem, que é escrita para o usuário e nunca traz o valor enviado.
 */
public class InvalidGoalException extends RuntimeException {

    public InvalidGoalException(String message) {
        super(message);
    }
}
