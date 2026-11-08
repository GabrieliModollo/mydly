-- Notificações push do módulo notifications (ADR 0016).

-- Aparelhos que recebem push: o token do Firebase Cloud Messaging é a chave. Um aparelho que passa
-- para outro usuário (outra pessoa entrou no app) troca de dono, e quem saiu deixa de receber.
CREATE TABLE notification_device (
    token        VARCHAR(512)  PRIMARY KEY,
    user_id      UUID          NOT NULL,
    platform     VARCHAR(10)   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_device_platform CHECK (platform IN ('ANDROID', 'IOS'))
);

CREATE INDEX idx_notification_device_user ON notification_device (user_id);

-- O que cada usuário quer receber. Sem linha, valem os padrões (tudo ligado, tom direto). O tom vem do
-- app, que é onde a pessoa o escolhe.
CREATE TABLE notification_settings (
    user_id        UUID         PRIMARY KEY,
    budget_alerts  BOOLEAN      NOT NULL DEFAULT TRUE,
    daily_reminder BOOLEAN      NOT NULL DEFAULT TRUE,
    weekly_summary BOOLEAN      NOT NULL DEFAULT TRUE,
    tone           VARCHAR(10)  NOT NULL DEFAULT 'direct',
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_settings_tone CHECK (tone IN ('gentle', 'direct', 'scold'))
);

-- Uma notificação de cada tipo e assunto vai uma vez só: o alerta de uma categoria por período e
-- nível, o lembrete por dia, o resumo por semana. A linha é gravada antes do envio.
CREATE TABLE notification_sent (
    user_id   UUID          NOT NULL,
    kind      VARCHAR(20)   NOT NULL,
    dedup_key VARCHAR(120)  NOT NULL,
    sent_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification_sent PRIMARY KEY (user_id, kind, dedup_key),
    CONSTRAINT chk_notification_sent_kind CHECK (kind IN ('BUDGET_ALERT', 'DAILY_REMINDER', 'WEEKLY_SUMMARY'))
);
