CREATE TABLE app_onboarding_login_tokens (
    id uuid PRIMARY KEY,
    owner_user_id uuid NOT NULL REFERENCES access_users(id),
    token_digest bytea NOT NULL UNIQUE,
    purpose text NOT NULL DEFAULT 'app-onboarding',
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz DEFAULT NULL,
    CONSTRAINT app_onboarding_login_tokens_purpose CHECK (purpose = 'app-onboarding'),
    CONSTRAINT app_onboarding_login_tokens_digest_size CHECK (octet_length(token_digest) = 32),
    CONSTRAINT app_onboarding_login_tokens_expiry_after_creation CHECK (expires_at > created_at)
);

CREATE INDEX idx_app_onboarding_login_tokens_owner_open
    ON app_onboarding_login_tokens (owner_user_id)
    WHERE consumed_at IS NULL;

ALTER TABLE access_users
    ADD COLUMN onboarding_completed_at timestamptz DEFAULT NULL;
