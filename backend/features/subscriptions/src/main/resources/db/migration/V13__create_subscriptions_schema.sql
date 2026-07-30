CREATE TABLE coupons (
    id uuid PRIMARY KEY,
    code varchar(32) NOT NULL,
    discount_percent integer NOT NULL,
    duration_cycles integer,
    valid_until timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_discount_percent CHECK (discount_percent BETWEEN 1 AND 100),
    CONSTRAINT ck_coupons_duration_cycles CHECK (duration_cycles IS NULL OR duration_cycles >= 1)
);

CREATE TABLE subscriptions (
    owner_user_id uuid PRIMARY KEY,
    plan varchar(16) NOT NULL,
    cycle varchar(8) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    asaas_customer_id varchar(64) NOT NULL,
    asaas_subscription_id varchar(64) NOT NULL,
    current_period_end timestamptz NOT NULL,
    canceled_at timestamptz,
    pending_plan varchar(16),
    pending_plan_effective_at timestamptz,
    coupon_id uuid,
    coupon_cycles_remaining integer,
    past_due_since timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_subscriptions_owner FOREIGN KEY (owner_user_id) REFERENCES access_users (id),
    CONSTRAINT fk_subscriptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT uq_subscriptions_asaas_subscription_id UNIQUE (asaas_subscription_id),
    CONSTRAINT ck_subscriptions_plan CHECK (plan IN ('TITULAR', 'ORGANIZADOR', 'ILIMITADO')),
    CONSTRAINT ck_subscriptions_pending_plan CHECK (pending_plan IS NULL OR pending_plan IN ('TITULAR', 'ORGANIZADOR', 'ILIMITADO')),
    CONSTRAINT ck_subscriptions_cycle CHECK (cycle IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT ck_subscriptions_status CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELED'))
);

CREATE TABLE coupon_redemptions (
    coupon_id uuid NOT NULL,
    user_id uuid NOT NULL,
    redeemed_at timestamptz NOT NULL,
    PRIMARY KEY (coupon_id, user_id),
    CONSTRAINT fk_coupon_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_coupon_redemptions_user FOREIGN KEY (user_id) REFERENCES access_users (id)
);

CREATE TABLE subscription_events (
    id uuid PRIMARY KEY,
    asaas_event_id varchar(64) NOT NULL,
    type varchar(64) NOT NULL,
    payload text NOT NULL,
    processed_at timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_subscription_events_asaas_event_id UNIQUE (asaas_event_id)
);
