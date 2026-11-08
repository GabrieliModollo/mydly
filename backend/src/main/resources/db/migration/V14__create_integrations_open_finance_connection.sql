-- Conexões de Open Finance pelo agregador (ADR 0005 e ADR 0015), só no sandbox, com dados fictícios.
-- Cada conexão é um "item" do Pluggy: o banco que o usuário conectou pelo widget. As transações
-- dela entram por external_transaction com source OPEN_FINANCE (ADR 0014).
--
-- status é o do item no Pluggy na última leitura (UPDATED, UPDATING, LOGIN_ERROR, OUTDATED...).
CREATE TABLE open_finance_connection (
    id             UUID          PRIMARY KEY,
    user_id        UUID          NOT NULL,
    item_id        VARCHAR(64)   NOT NULL,
    institution    VARCHAR(120),
    status         VARCHAR(30)   NOT NULL,
    last_synced_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_open_finance_connection_item UNIQUE (item_id)
);

CREATE INDEX idx_open_finance_connection_user ON open_finance_connection (user_id);
