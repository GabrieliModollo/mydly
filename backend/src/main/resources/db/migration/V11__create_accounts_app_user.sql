-- Usuários do módulo accounts (ADR 0012). O nome de login é guardado em minúsculas, e a senha só
-- como hash BCrypt.
CREATE TABLE app_user (
    id            UUID         PRIMARY KEY,
    username      VARCHAR(40)  NOT NULL,
    password_hash TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT chk_app_user_username CHECK (username = lower(username) AND length(username) > 0)
);

-- Usuário da demonstração, com a senha pública "demo". Os dados que já existiam passam a ser dele (V12).
INSERT INTO app_user (id, username, password_hash) VALUES
    ('d3d3d3d3-0000-4000-8000-000000000001', 'demo', '$2a$10$F4Jqb0iWeLMLZDA3bng1.OSH0jhKibirbTjaBEcSW4TB5QbUY6Khy');
