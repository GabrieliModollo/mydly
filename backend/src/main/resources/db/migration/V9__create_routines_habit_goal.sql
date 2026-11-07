-- Metas de hábito do módulo routines (ADR 0010).
--
-- Dimensão de medida de cada hábito do catálogo: uma meta diária de quantidade só vale para hábitos
-- com medida, e a unidade da meta precisa ser da mesma dimensão (L e mL para volume, h e min para
-- duração). Academia mede o tempo de treino ou de corrida.
ALTER TABLE habit ADD COLUMN measure VARCHAR(10) NOT NULL DEFAULT 'NONE';
ALTER TABLE habit ADD CONSTRAINT chk_habit_measure CHECK (measure IN ('VOLUME', 'DURATION', 'NONE'));

UPDATE habit SET measure = 'VOLUME' WHERE code = 'HYDRATION';
UPDATE habit SET measure = 'DURATION' WHERE code IN ('STUDY', 'SLEEP', 'READING', 'GYM');

-- Versões da meta de cada hábito. A meta vigente num dia é a de maior valid_from até esse dia.
-- Mudar a meta grava uma versão nova a partir de hoje, e remover grava uma versão NONE: o passado
-- continua avaliado pela meta que valia nele. Uma segunda gravação no mesmo dia substitui a versão
-- daquele dia (upsert pela chave).
--
-- DAILY_QUANTITY: quantidade por dia (target_quantity em unit).
-- WEEKLY_FREQUENCY: dias por semana; num hábito ruim é o limite (no máximo n dias), por isso aceita 0.
CREATE TABLE habit_goal (
    habit_code      VARCHAR(30)    NOT NULL REFERENCES habit (code),
    valid_from      DATE           NOT NULL,
    kind            VARCHAR(20)    NOT NULL,
    target_quantity NUMERIC(12, 3),
    unit            VARCHAR(10),
    days_per_week   SMALLINT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_habit_goal PRIMARY KEY (habit_code, valid_from),
    CONSTRAINT chk_habit_goal_kind CHECK (kind IN ('DAILY_QUANTITY', 'WEEKLY_FREQUENCY', 'NONE')),
    CONSTRAINT chk_habit_goal_daily CHECK (kind <> 'DAILY_QUANTITY' OR (
        target_quantity > 0 AND unit IN ('L', 'mL', 'h', 'min') AND days_per_week IS NULL)),
    CONSTRAINT chk_habit_goal_weekly CHECK (kind <> 'WEEKLY_FREQUENCY' OR (
        days_per_week BETWEEN 0 AND 7 AND target_quantity IS NULL AND unit IS NULL)),
    CONSTRAINT chk_habit_goal_none CHECK (kind <> 'NONE' OR (
        target_quantity IS NULL AND unit IS NULL AND days_per_week IS NULL))
);
