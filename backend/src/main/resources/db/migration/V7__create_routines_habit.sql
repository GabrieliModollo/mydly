-- Catálogo fixo de hábitos do módulo routines (ADR 0008), com polaridade: GOOD é cumprir,
-- BAD é evitar. As palavras-chave são comparadas ao texto do item em minúsculas e sem acentos;
-- vale a que aparece primeiro. Hábitos personalizados entram junto com as metas.
CREATE TABLE habit (
    code     VARCHAR(30)  PRIMARY KEY,
    name     VARCHAR(60)  NOT NULL,
    polarity VARCHAR(4)   NOT NULL,
    keywords TEXT[]       NOT NULL,
    CONSTRAINT chk_habit_polarity CHECK (polarity IN ('GOOD', 'BAD'))
);

INSERT INTO habit (code, name, polarity, keywords) VALUES
    ('HYDRATION',   'Hidratação',   'GOOD', ARRAY['agua', 'hidratei', 'hidratacao']),
    ('GYM',         'Academia',     'GOOD', ARRAY['academia', 'treino', 'treinei', 'malhei', 'musculacao',
                                                  'flexoes', 'abdominais', 'corri', 'corrida', 'pedalei',
                                                  'nadei', 'exercicio', 'exercicios']),
    ('STUDY',       'Estudo',       'GOOD', ARRAY['estudei', 'estudo', 'estudar', 'aula', 'curso']),
    ('SLEEP',       'Sono',         'GOOD', ARRAY['dormi', 'sono', 'cochilei']),
    ('DIET',        'Dieta',        'GOOD', ARRAY['dieta', 'salada', 'fruta', 'frutas', 'verdura', 'verduras',
                                                  'legumes']),
    ('READING',     'Leitura',      'GOOD', ARRAY['li', 'leitura', 'livro', 'livros', 'ler']),
    ('DELIVERY',    'Delivery',     'BAD',  ARRAY['delivery', 'ifood']),
    ('ENERGY_DRINK','Energético',   'BAD',  ARRAY['energetico', 'energeticos']),
    ('JUNK_FOOD',   'Besteira',     'BAD',  ARRAY['besteira', 'besteiras', 'fritura', 'salgadinho', 'doce',
                                                  'doces']),
    ('SMOKING',     'Cigarro',      'BAD',  ARRAY['fumei', 'fumar', 'fumo', 'cigarro', 'cigarros']),
    ('SODA',        'Refrigerante', 'BAD',  ARRAY['refrigerante', 'refrigerantes', 'refri']);

-- Registro de cada item de rotina aceito em entries, com a mesma regra de revisão e lápide de
-- finance_transaction. habit_code nulo = "outros": fica registrado, mas fora dos indicadores.
CREATE TABLE habit_log (
    entry_id    UUID           NOT NULL,
    item_index  INTEGER        NOT NULL,
    revision    BIGINT         NOT NULL,
    active      BOOLEAN        NOT NULL,
    habit_code  VARCHAR(30)    REFERENCES habit (code),
    outcome     VARCHAR(10),
    quantity    NUMERIC(12, 3),
    unit        VARCHAR(10),
    occurred_at TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (entry_id, item_index),
    CONSTRAINT chk_habit_log_outcome CHECK (outcome IN ('DONE', 'MISSED')),
    CONSTRAINT chk_habit_log_active CHECK (NOT active OR (outcome IS NOT NULL AND occurred_at IS NOT NULL))
);

CREATE INDEX idx_habit_log_occurred_at ON habit_log (occurred_at) WHERE active;
