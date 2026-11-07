-- Cada dado passa a ter dono (ADR 0012). O que existia antes do login é do usuário demo.
--
-- Não há chave estrangeira para app_user: cada módulo guarda o id do usuário como guarda o id do
-- lançamento em finance_transaction, sem amarrar tabelas de módulos diferentes (ADR 0002).

-- entries
ALTER TABLE entry ADD COLUMN user_id UUID;
UPDATE entry SET user_id = 'd3d3d3d3-0000-4000-8000-000000000001';
ALTER TABLE entry ALTER COLUMN user_id SET NOT NULL;
DROP INDEX idx_entry_occurred_at;
DROP INDEX idx_entry_created_at;
CREATE INDEX idx_entry_user_occurred_at ON entry (user_id, occurred_at);

-- finance
ALTER TABLE finance_transaction ADD COLUMN user_id UUID;
UPDATE finance_transaction SET user_id = 'd3d3d3d3-0000-4000-8000-000000000001';
ALTER TABLE finance_transaction ALTER COLUMN user_id SET NOT NULL;
DROP INDEX idx_finance_transaction_occurred_at;
CREATE INDEX idx_finance_transaction_user_occurred_at ON finance_transaction (user_id, occurred_at) WHERE active;

ALTER TABLE budget ADD COLUMN user_id UUID;
UPDATE budget SET user_id = 'd3d3d3d3-0000-4000-8000-000000000001';
ALTER TABLE budget ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE budget DROP CONSTRAINT pk_budget;
ALTER TABLE budget ADD CONSTRAINT pk_budget PRIMARY KEY (user_id, category, valid_from);

-- routines
ALTER TABLE habit_log ADD COLUMN user_id UUID;
UPDATE habit_log SET user_id = 'd3d3d3d3-0000-4000-8000-000000000001';
ALTER TABLE habit_log ALTER COLUMN user_id SET NOT NULL;
DROP INDEX idx_habit_log_occurred_at;
CREATE INDEX idx_habit_log_user_occurred_at ON habit_log (user_id, occurred_at) WHERE active;

ALTER TABLE habit_goal ADD COLUMN user_id UUID;
UPDATE habit_goal SET user_id = 'd3d3d3d3-0000-4000-8000-000000000001';
ALTER TABLE habit_goal ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE habit_goal DROP CONSTRAINT pk_habit_goal;
ALTER TABLE habit_goal ADD CONSTRAINT pk_habit_goal PRIMARY KEY (user_id, habit_code, valid_from);

-- preferences: deixa de ter uma linha só (id = 1) e passa a ter uma por usuário. Usuário sem linha
-- usa o padrão (semana começando na segunda).
ALTER TABLE preferences ADD COLUMN user_id UUID;
UPDATE preferences SET user_id = 'd3d3d3d3-0000-4000-8000-000000000001';
ALTER TABLE preferences DROP CONSTRAINT chk_preferences_single_row;
ALTER TABLE preferences DROP CONSTRAINT preferences_pkey;
ALTER TABLE preferences DROP COLUMN id;
ALTER TABLE preferences ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE preferences ADD CONSTRAINT pk_preferences PRIMARY KEY (user_id);

-- Eventos entre módulos ainda não concluídos (ADR 0008) ganham o usuário, que os eventos passam a
-- exigir. Os concluídos já foram apagados (completion-mode: delete).
UPDATE event_publication
SET serialized_event = jsonb_set(serialized_event::jsonb, '{userId}',
                                 '"d3d3d3d3-0000-4000-8000-000000000001"'::jsonb)::text
WHERE event_type IN ('com.mydly.entries.EntryItemAccepted', 'com.mydly.entries.EntryItemRetracted')
  AND NOT (serialized_event::jsonb ? 'userId');
