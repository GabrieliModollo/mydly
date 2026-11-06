-- Data em que o usuário escreveu o lançamento, que pode ser anterior à sincronização (ADR 0006).
-- O default só preenche linhas já existentes; daqui em diante a aplicação sempre informa o valor.
ALTER TABLE entry ADD COLUMN occurred_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE entry ALTER COLUMN occurred_at DROP DEFAULT;

CREATE INDEX idx_entry_occurred_at ON entry (occurred_at);
