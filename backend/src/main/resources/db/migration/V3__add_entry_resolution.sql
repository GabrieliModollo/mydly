-- Correções do usuário, guardadas à parte do que o parser entendeu (ADR 0007).
-- Lista alinhada com os itens de parsed_json: posição vazia (null) = item ainda não resolvido.
ALTER TABLE entry ADD COLUMN resolution JSONB;

-- Versão para controle de concorrência otimista (ETag / If-Match na API).
ALTER TABLE entry ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
