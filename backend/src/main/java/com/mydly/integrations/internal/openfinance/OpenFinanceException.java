package com.mydly.integrations.internal.openfinance;

/** Uma operação de Open Finance que não pôde ser feita. A mensagem é para o usuário e não traz dados dele. */
public class OpenFinanceException extends RuntimeException {

    public enum Kind {
        /** Sem as chaves do Pluggy no servidor. */
        DISABLED,
        /** A conexão não existe, ou é de outro usuário. */
        NOT_FOUND,
        /** O item não pode ser ligado: não é de sandbox ou foi criado para outro usuário. */
        REJECTED,
        /** O item já está ligado a outra conta do MyDly. */
        CONFLICT,
        /** O Pluggy não respondeu como esperado. */
        UNAVAILABLE
    }

    private final Kind kind;

    public OpenFinanceException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /** Falha do lado do Pluggy. */
    public OpenFinanceException(String message) {
        this(Kind.UNAVAILABLE, message);
    }

    public Kind kind() {
        return kind;
    }
}
