-- One-time login codes for the purchase-information e-mail. The mailbox gets the
-- raw code; this table keeps only the SHA-256 digest so a dump cannot sign anyone in.
CREATE TABLE subscription_checkout_login_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    token_digest bytea NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    CONSTRAINT fk_subscription_checkout_login_tokens_user
        FOREIGN KEY (user_id) REFERENCES access_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_subscription_checkout_login_tokens_digest_len
        CHECK (octet_length(token_digest) = 32),
    CONSTRAINT uq_subscription_checkout_login_tokens_digest UNIQUE (token_digest)
);

CREATE INDEX ix_subscription_checkout_login_tokens_user_open
    ON subscription_checkout_login_tokens (user_id)
    WHERE consumed_at IS NULL;
