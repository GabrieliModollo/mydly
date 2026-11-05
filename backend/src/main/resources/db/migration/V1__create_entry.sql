-- Lançamento em texto livre. O texto original nunca é descartado (ADR 0003).
CREATE TABLE entry (
    id            UUID          PRIMARY KEY,
    raw_text      TEXT          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    detected_type VARCHAR(20),
    parsed_json   JSONB,
    confidence    NUMERIC(3, 2),
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    CONSTRAINT chk_entry_type   CHECK (detected_type IN ('FINANCE', 'ROUTINE')),
    CONSTRAINT chk_entry_status CHECK (status IN ('PENDING', 'CONFIRMED', 'NEEDS_INPUT'))
);

CREATE INDEX idx_entry_created_at ON entry (created_at);
