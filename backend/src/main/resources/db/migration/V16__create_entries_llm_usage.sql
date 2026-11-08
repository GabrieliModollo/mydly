-- Cota diária de chamadas ao LLM de fallback do parser (ADR 0017). Uma linha por usuário e dia, com
-- quantas chamadas ele fez; a soma do dia é a cota de todos. Guarda só a contagem: nem o texto nem o
-- que o LLM respondeu.
CREATE TABLE llm_usage (
    user_id UUID     NOT NULL,
    day     DATE     NOT NULL,
    calls   INTEGER  NOT NULL,
    CONSTRAINT pk_llm_usage PRIMARY KEY (user_id, day),
    CONSTRAINT chk_llm_usage_calls CHECK (calls > 0)
);

CREATE INDEX idx_llm_usage_day ON llm_usage (day);
