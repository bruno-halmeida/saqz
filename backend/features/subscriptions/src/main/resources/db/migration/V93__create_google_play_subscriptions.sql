-- Assinatura comprada pelo Google Play (Play Billing). Uma linha por purchase token; o estado
-- é sempre o que a Google Play Developer API devolve (purchases.subscriptionsv2).
CREATE TABLE google_play_subscriptions (
    purchase_token text PRIMARY KEY,
    owner_user_id uuid NOT NULL REFERENCES access_users(id),
    product_id varchar(128) NOT NULL,
    base_plan_id varchar(64) NOT NULL,
    plan subscription_plan NOT NULL,
    cycle subscription_cycle NOT NULL,
    -- subscriptionState sem o prefixo SUBSCRIPTION_STATE_ (ACTIVE, CANCELED, IN_GRACE_PERIOD...).
    state varchar(48) NOT NULL,
    expires_at timestamptz NOT NULL,
    auto_renew boolean,
    canceled_at timestamptz,
    latest_order_id varchar(128),
    linked_purchase_token text,
    -- Upgrade ou recompra: a assinatura nova traz este token em linked_purchase_token.
    superseded_at timestamptz,
    acknowledged boolean NOT NULL DEFAULT false,
    test_purchase boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX google_play_subscriptions_owner_idx ON google_play_subscriptions (owner_user_id);

-- Cada pedido (compra e renovações, GPA.xxxx..0, ..1). Alimenta recibos e o histórico de
-- pagamento que tira a elegibilidade ao trial. O Play não devolve preço na subscriptionsv2.
CREATE TABLE google_play_orders (
    order_id varchar(128) PRIMARY KEY,
    purchase_token text NOT NULL REFERENCES google_play_subscriptions (purchase_token),
    owner_user_id uuid NOT NULL REFERENCES access_users(id),
    product_id varchar(128) NOT NULL,
    base_plan_id varchar(64) NOT NULL,
    test_purchase boolean NOT NULL DEFAULT false,
    recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX google_play_orders_owner_idx ON google_play_orders (owner_user_id, recorded_at DESC);

-- Real-time Developer Notifications já processadas (o Pub/Sub reenvia até receber 2xx).
CREATE TABLE google_play_notifications (
    message_id varchar(128) PRIMARY KEY,
    notification_type integer,
    purchase_token text,
    received_at timestamptz NOT NULL DEFAULT now()
);
