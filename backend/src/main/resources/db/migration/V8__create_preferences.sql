-- Preferências do módulo preferences (ADR 0010). Globais até existir usuário: a tabela tem uma linha
-- só (id = 1). Na etapa de usuário ela ganha user_id e a chave passa a ser o usuário.
-- O dia de início da semana substitui a configuração mydly.week.first-day-of-week (ADR 0009).
CREATE TABLE preferences (
    id                SMALLINT     PRIMARY KEY,
    first_day_of_week VARCHAR(9)   NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_preferences_single_row CHECK (id = 1),
    CONSTRAINT chk_preferences_first_day_of_week CHECK (first_day_of_week IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'))
);

INSERT INTO preferences (id, first_day_of_week) VALUES (1, 'MONDAY');
