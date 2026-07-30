-- Nullable: historical rows have no known billing type, and inventing one (e.g. defaulting to
-- PIX) would make the checkout-recovery retry check compare against a fabricated value.
ALTER TABLE subscriptions
    ADD COLUMN billing_type varchar(16);

ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_billing_type
        CHECK (billing_type IS NULL OR billing_type IN ('PIX', 'CREDIT_CARD'));
