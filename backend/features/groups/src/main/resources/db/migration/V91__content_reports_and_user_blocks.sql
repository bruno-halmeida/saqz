-- Denúncias e bloqueios de conteúdo criado por usuários (Apple 1.2).
--
-- A denúncia não tem FK para o alvo: remover o conteúdo é justamente a resposta esperada, e a
-- denúncia precisa sobreviver a isso. `excerpt` guarda uma cópia do que foi denunciado no
-- momento da denúncia; `target_user_id` é quem responde pelo conteúdo (autor do aviso, pessoa
-- denunciada ou dono do grupo). Bloquear alguém também gera uma linha, com motivo BLOCKED.
CREATE TABLE content_reports (
    id uuid PRIMARY KEY,
    reporter_id uuid NOT NULL REFERENCES access_users(id),
    group_id uuid NOT NULL REFERENCES access_groups(id),
    target_type varchar(16) NOT NULL CHECK (target_type IN ('USER', 'GROUP', 'MESSAGE')),
    target_id uuid NOT NULL,
    target_user_id uuid REFERENCES access_users(id),
    excerpt varchar(2000),
    reason varchar(16) NOT NULL CHECK (reason IN ('SPAM', 'OFFENSIVE', 'HARASSMENT', 'OTHER', 'BLOCKED')),
    details varchar(1000),
    created_at timestamptz NOT NULL DEFAULT now(),
    alerted_at timestamptz,
    alert_attempts integer NOT NULL DEFAULT 0 CHECK (alert_attempts >= 0),
    resolved_at timestamptz
);
CREATE INDEX ix_content_reports_pending_alert ON content_reports(created_at) WHERE alerted_at IS NULL;
CREATE INDEX ix_content_reports_open ON content_reports(created_at DESC) WHERE resolved_at IS NULL;

-- Bloqueio é de pessoa para pessoa e vale em todos os grupos: quem bloqueia deixa de ver os
-- avisos e mensagens do bloqueado e de ser notificado por eles. O bloqueado não é avisado.
CREATE TABLE user_blocks (
    blocker_id uuid NOT NULL REFERENCES access_users(id),
    blocked_id uuid NOT NULL REFERENCES access_users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX ix_user_blocks_blocked ON user_blocks(blocked_id);
