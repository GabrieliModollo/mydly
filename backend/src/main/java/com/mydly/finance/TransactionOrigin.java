package com.mydly.finance;

/** De onde veio uma transação ({@code finance_transaction.source}). */
public enum TransactionOrigin {

    /** Um lançamento escrito no app. */
    MANUAL,

    /** Uma linha de extrato OFX ou CSV importado (ADR 0014). */
    STATEMENT_IMPORT,

    /** O agregador de Open Finance, só no sandbox (ADR 0005). */
    OPEN_FINANCE
}
