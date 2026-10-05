-- Assinatura comprada pela App Store (In-App Purchase). Fica fora de `subscriptions`, que é
-- toda do Asaas (ids do Asaas obrigatórios, uma linha por dono). Uma linha por transação
-- original da Apple: o mesmo dono pode ter mais de uma se assinar com dois Apple IDs.
CREATE TABLE app_store_subscriptions (
    original_transaction_id varchar(64) PRIMARY KEY,
    owner_user_id uuid NOT NULL REFERENCES access_users(id),
    environment varchar(16) NOT NULL,
    product_id varchar(128) NOT NULL,
    plan subscription_plan NOT NULL,
    cycle subscription_cycle NOT NULL,
    latest_transaction_id varchar(64) NOT NULL,
    latest_purchase_date timestamptz NOT NULL,
    latest_signed_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    -- Renovação automática: nulo até a primeira notificação com o renewal info.
    auto_renew boolean,
    auto_renew_product_id varchar(128),
    auto_renew_plan subscription_plan,
    auto_renew_changed_at timestamptz,
    in_billing_retry boolean NOT NULL DEFAULT false,
    grace_period_expires_at timestamptz,
    renewal_signed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX app_store_subscriptions_owner_idx ON app_store_subscriptions (owner_user_id);

-- Cada cobrança da App Store (compra e renovações). Alimenta recibos e o histórico de pagamento
-- que tira a elegibilidade ao trial.
CREATE TABLE app_store_transactions (
    transaction_id varchar(64) PRIMARY KEY,
    original_transaction_id varchar(64) NOT NULL
        REFERENCES app_store_subscriptions (original_transaction_id),
    owner_user_id uuid NOT NULL REFERENCES access_users(id),
    product_id varchar(128) NOT NULL,
    purchase_date timestamptz NOT NULL,
    expires_at timestamptz,
    -- Preço em milésimos da moeda, como a Apple manda (59900 = R$ 59,90).
    price_millis bigint,
    currency varchar(3),
    revoked_at timestamptz,
    environment varchar(16) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX app_store_transactions_owner_idx ON app_store_transactions (owner_user_id, purchase_date DESC);

-- App Store Server Notifications V2 já processadas: a Apple reenvia a mesma notificação
-- (mesmo notificationUUID) até receber 200.
CREATE TABLE app_store_notifications (
    notification_uuid uuid PRIMARY KEY,
    notification_type varchar(64) NOT NULL,
    subtype varchar(64),
    original_transaction_id varchar(64),
    environment varchar(16),
    signed_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now()
);
