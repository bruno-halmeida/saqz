ALTER TABLE subscriptions
    ADD COLUMN billing_type varchar(16) NOT NULL DEFAULT 'PIX';

ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_billing_type
        CHECK (billing_type IN ('PIX', 'CREDIT_CARD'));

ALTER TABLE subscriptions
    ALTER COLUMN billing_type DROP DEFAULT;
