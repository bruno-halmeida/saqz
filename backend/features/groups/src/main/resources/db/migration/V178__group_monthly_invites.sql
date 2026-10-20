-- DEB-106 (CONV-010 a 012): convite de mensalistas pelo WhatsApp.
-- Link de mensalistas: um por grupo, separado do convite público. Vale 7 dias a partir do último envio; vencido, o
-- próximo envio troca o código. Invalidar anula o código na hora e a linha fica para a retenção de 30 dias.
CREATE TABLE group_monthly_invite_links (
    group_id uuid PRIMARY KEY REFERENCES access_groups (id) ON DELETE CASCADE,
    short_code varchar(8),
    -- Mesma chave de busca do convite público (sha256 do texto), para prévia e resgate usarem um digest só.
    short_code_digest bytea GENERATED ALWAYS AS (sha256(short_code::text::bytea)) STORED,
    expires_at timestamptz NOT NULL,
    invalidated_at timestamptz,
    created_by_user_id uuid NOT NULL REFERENCES access_users (id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_group_monthly_invite_links_short_code CHECK (short_code ~ '^[2-9A-HJKMNP-Z]{8}$'),
    CONSTRAINT ck_group_monthly_invite_links_alive CHECK ((short_code IS NULL) = (invalidated_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_group_monthly_invite_links_short_code ON group_monthly_invite_links (short_code);
CREATE UNIQUE INDEX uq_group_monthly_invite_links_short_code_digest ON group_monthly_invite_links (short_code_digest);

-- Quem foi cadastrado e ainda não entrou. Não é membro: não ocupa vaga. Telefone normalizado (+55DDD9XXXXXXXX).
CREATE TABLE group_monthly_invitees (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL REFERENCES access_groups (id) ON DELETE CASCADE,
    name varchar(80) NOT NULL,
    phone varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    sent_at timestamptz,
    next_attempt_at timestamptz,
    created_by_user_id uuid REFERENCES access_users (id) ON DELETE SET NULL,
    request_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_group_monthly_invitees_phone UNIQUE (group_id, phone),
    CONSTRAINT ck_group_monthly_invitees_status
        CHECK (status IN ('QUEUED', 'WAITING', 'FAILED', 'NO_WHATSAPP', 'PAUSED')),
    CONSTRAINT ck_group_monthly_invitees_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_group_monthly_invitees_request ON group_monthly_invitees (group_id, request_id);
CREATE INDEX ix_group_monthly_invitees_status ON group_monthly_invitees (status, created_at);
