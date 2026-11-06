-- Transações do módulo finance (ADR 0008), alimentadas pelos eventos de entries.
-- Uma linha por item de lançamento. A revisão garante a ordem: um evento só vale se a revisão for
-- maior que a gravada. Item retirado vira lápide (active = false), nunca DELETE, para que um evento
-- antigo atrasado não ressuscite o dado.
CREATE TABLE finance_transaction (
    entry_id    UUID           NOT NULL,
    item_index  INTEGER        NOT NULL,
    revision    BIGINT         NOT NULL,
    active      BOOLEAN        NOT NULL,
    amount      NUMERIC(12, 2),
    direction   VARCHAR(10),
    category    VARCHAR(20),
    occurred_at TIMESTAMPTZ,
    source      VARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (entry_id, item_index),
    CONSTRAINT chk_finance_transaction_direction CHECK (direction IN ('EXPENSE', 'INCOME')),
    CONSTRAINT chk_finance_transaction_source CHECK (source IN ('MANUAL')),
    CONSTRAINT chk_finance_transaction_active CHECK (
        NOT active OR (amount > 0 AND direction IS NOT NULL AND occurred_at IS NOT NULL))
);

CREATE INDEX idx_finance_transaction_occurred_at ON finance_transaction (occurred_at) WHERE active;
