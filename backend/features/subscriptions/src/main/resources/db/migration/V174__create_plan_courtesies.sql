-- Cortesia de plano (DEB-097): acesso dado a mao pela administracao, sem passar por Asaas, App Store ou Google Play.
-- Tabela propria: nao e linha em `subscriptions` e nao ganha valor novo em nenhum enum que os apps leem.
-- Alterar = revogar a atual e gravar outra (o historico fica). `ends_at` nulo = sem limite de data.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE plan_courtesies (
    id uuid PRIMARY KEY,
    owner_user_id uuid NOT NULL REFERENCES access_users (id),
    plan public.subscription_plan NOT NULL,
    ends_at timestamptz,
    reason text NOT NULL,
    granted_by uuid NOT NULL REFERENCES access_users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    revoked_by uuid REFERENCES access_users (id),
    revoked_reason text,
    CONSTRAINT ck_plan_courtesies_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_plan_courtesies_revoked CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);

-- Uma nao revogada por dono; a validade (`ends_at`) e checada na leitura.
CREATE UNIQUE INDEX uq_plan_courtesies_open_owner ON plan_courtesies (owner_user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_plan_courtesies_owner ON plan_courtesies (owner_user_id, created_at DESC);
