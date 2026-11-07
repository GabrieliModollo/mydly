-- Orçamentos do módulo finance (ADR 0010): limite de gasto por categoria, por semana (que segue o
-- dia de início da semana) ou por mês civil. Versionados como as metas de hábito: o limite vigente
-- num dia é o de maior valid_from até esse dia, e remover grava uma versão sem limite (period e
-- limit_amount nulos). Uma segunda gravação no mesmo dia substitui a versão daquele dia.
CREATE TABLE budget (
    category     VARCHAR(20)    NOT NULL,
    valid_from   DATE           NOT NULL,
    period       VARCHAR(10),
    limit_amount NUMERIC(12, 2),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_budget PRIMARY KEY (category, valid_from),
    CONSTRAINT chk_budget_category CHECK (category IN
        ('FOOD', 'GROCERIES', 'PHARMACY', 'TRANSPORT', 'HOUSING', 'HEALTH', 'LEISURE', 'WORK', 'OTHER')),
    CONSTRAINT chk_budget_period CHECK (period IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT chk_budget_limit_amount CHECK (limit_amount > 0),
    -- Versão de remoção: sem período e sem limite. Com limite, o período é obrigatório.
    CONSTRAINT chk_budget_removal CHECK ((period IS NULL) = (limit_amount IS NULL))
);
