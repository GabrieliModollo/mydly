-- Transações vindas de fora do app (ADR 0005 e ADR 0014): por enquanto, extratos OFX e CSV.
--
-- Cada linha do extrato vira uma transação externa, única por usuário, origem e id externo (o FITID
-- do OFX, ou um hash da linha no CSV): importar o mesmo arquivo de novo não duplica nada.
--
-- status:
--   IMPORTED: entrou nos números do usuário, como uma linha de finance_transaction com o id desta;
--   MATCHED:  é o mesmo gasto de um lançamento manual (matched_entry_id e matched_item_index) e não
--             conta de novo;
--   IGNORED:  o usuário pediu para não contar (uma transferência entre contas dele, por exemplo).
-- revision cresce a cada mudança de status e vai para finance_transaction, que só aplica revisões
-- maiores (ADR 0008).
CREATE TABLE external_transaction (
    id                 UUID           PRIMARY KEY,
    user_id            UUID           NOT NULL,
    source             VARCHAR(20)    NOT NULL,
    external_id        TEXT           NOT NULL,
    occurred_on        DATE           NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    direction          VARCHAR(10)    NOT NULL,
    description        VARCHAR(200),
    status             VARCHAR(10)    NOT NULL,
    matched_entry_id   UUID,
    matched_item_index INTEGER,
    revision           BIGINT         NOT NULL,
    imported_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_external_transaction UNIQUE (user_id, source, external_id),
    CONSTRAINT chk_external_transaction_source CHECK (source IN ('STATEMENT_IMPORT', 'OPEN_FINANCE')),
    CONSTRAINT chk_external_transaction_amount CHECK (amount > 0),
    CONSTRAINT chk_external_transaction_direction CHECK (direction IN ('EXPENSE', 'INCOME')),
    CONSTRAINT chk_external_transaction_status CHECK (status IN ('IMPORTED', 'MATCHED', 'IGNORED')),
    CONSTRAINT chk_external_transaction_match CHECK (
        (status = 'MATCHED') = (matched_entry_id IS NOT NULL AND matched_item_index IS NOT NULL))
);

CREATE INDEX idx_external_transaction_user_day ON external_transaction (user_id, occurred_on);

-- Um item de lançamento se liga a uma transação externa só.
CREATE UNIQUE INDEX uq_external_transaction_match
    ON external_transaction (matched_entry_id, matched_item_index) WHERE status = 'MATCHED';

-- finance_transaction passa a guardar também as transações externas que não casaram com um lançamento:
-- nelas, entry_id é o id da transação externa e item_index é 0.
ALTER TABLE finance_transaction DROP CONSTRAINT chk_finance_transaction_source;
ALTER TABLE finance_transaction ADD CONSTRAINT chk_finance_transaction_source
    CHECK (source IN ('MANUAL', 'STATEMENT_IMPORT', 'OPEN_FINANCE'));
