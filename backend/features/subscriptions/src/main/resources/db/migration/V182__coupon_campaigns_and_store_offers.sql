-- Cupom de desconto como campanha: vale no site (Asaas) e, quando tem duração, vira oferta no
-- Google Play e código de oferta na App Store (docs/subscriptions/google-play.md e app-store.md).
ALTER TABLE coupons
    ADD COLUMN campaign varchar(120),
    ADD COLUMN max_uses integer CHECK (max_uses IS NULL OR max_uses > 0),
    -- Quem pode usar: NEVER_SUBSCRIBED, FORMER_SUBSCRIBER (e, no futuro, CURRENT_SUBSCRIBER).
    -- Vazio nos cupons antigos = qualquer pessoa.
    ADD COLUMN eligibility varchar(32)[] NOT NULL DEFAULT '{}',
    -- Produtos que valem, no formato PLANO:CICLO (ex.: ORGANIZADOR:MONTHLY). Vazio = todos.
    ADD COLUMN products varchar(32)[] NOT NULL DEFAULT '{}',
    ADD COLUMN stores boolean NOT NULL DEFAULT false,
    ADD COLUMN ended_at timestamptz;

ALTER TABLE coupons ADD CONSTRAINT ck_coupons_stores_need_duration
    CHECK (NOT stores OR (duration_cycles IS NOT NULL AND discount_percent < 100));

-- Uma linha por cupom × loja × produto (plano + ciclo).
CREATE TABLE coupon_store_offers (
    id uuid PRIMARY KEY,
    coupon_id uuid NOT NULL REFERENCES coupons (id),
    store varchar(16) NOT NULL CHECK (store IN ('GOOGLE_PLAY', 'APP_STORE')),
    plan varchar(32) NOT NULL,
    cycle varchar(16) NOT NULL,
    -- Google Play
    product_id varchar(128) NOT NULL,
    base_plan_id varchar(64),
    offer_id varchar(64),
    -- App Store
    asc_subscription_id varchar(64),
    asc_offer_code_id varchar(64),
    custom_code varchar(64),
    asc_custom_code_id varchar(64),
    -- Preço cobrado pela loja durante o desconto, em centavos (Apple: price point escolhido).
    store_price_cents bigint,
    state varchar(16) NOT NULL CHECK (state IN
        ('PENDING', 'CREATING', 'WAITING_STORE', 'ACTIVE', 'FAILED', 'DEACTIVATING', 'DEACTIVATED')),
    message text,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    store_created_at timestamptz,
    activated_at timestamptz,
    deactivated_at timestamptz,
    CONSTRAINT uq_coupon_store_offers UNIQUE (coupon_id, store, plan, cycle)
);
CREATE UNIQUE INDEX uq_coupon_store_offers_custom_code ON coupon_store_offers (custom_code)
    WHERE custom_code IS NOT NULL AND state <> 'DEACTIVATED';
CREATE INDEX coupon_store_offers_work ON coupon_store_offers (state, next_attempt_at)
    WHERE state IN ('PENDING', 'CREATING', 'WAITING_STORE', 'DEACTIVATING');

ALTER TABLE coupon_redemptions
    ADD COLUMN platform varchar(16) NOT NULL DEFAULT 'WEB' CHECK (platform IN ('WEB', 'ANDROID', 'IOS'));
