-- Parceiros (ex.: uma quadra) com cupons de desconto e de teste. Origem "veio do parceiro" na
-- customer_acquisition: o primeiro uso de um cupom de qualquer parceiro (primeiro toque); usar
-- depois o cupom de outro parceiro nao muda a origem.
CREATE TABLE partners (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    contact_name varchar(120),
    contact_phone varchar(20),
    contact_email varchar(254),
    notes text,
    active boolean NOT NULL DEFAULT true,
    ended_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_partners_name ON partners (lower(name));

ALTER TABLE coupons ADD COLUMN partner_id uuid REFERENCES partners (id);
ALTER TABLE trial_coupons ADD COLUMN partner_id uuid REFERENCES partners (id);
CREATE INDEX coupons_partner ON coupons (partner_id) WHERE partner_id IS NOT NULL;
CREATE INDEX trial_coupons_partner ON trial_coupons (partner_id) WHERE partner_id IS NOT NULL;

-- Igual a V184, com duas colunas no fim:
--   partner_id            parceiro do primeiro cupom de parceiro que a pessoa usou (nulo = nao veio de parceiro).
--   partner_first_used_at quando esse cupom foi usado; pagamentos do parceiro contam a partir dele.
-- As colunas antigas (primeiro cupom de qualquer tipo) nao mudam.
CREATE OR REPLACE VIEW customer_acquisition
    (owner_user_id, coupon_type, coupon_id, coupon_code, campaign, first_used_at, partner_id, partner_first_used_at) AS
WITH uses AS (
    SELECT t.owner_user_id, 'TRIAL'::text AS coupon_type, t.coupon_id, t.started_at AS used_at
    FROM organizer_trials t
    WHERE t.coupon_id IS NOT NULL
    UNION ALL
    SELECT r.user_id, 'DISCOUNT'::text, r.coupon_id, r.redeemed_at
    FROM coupon_redemptions r
),
partner_uses AS (
    SELECT DISTINCT ON (u.owner_user_id) u.owner_user_id, COALESCE(tc.partner_id, c.partner_id) AS partner_id, u.used_at
    FROM uses u
    LEFT JOIN trial_coupons tc ON u.coupon_type = 'TRIAL' AND tc.id = u.coupon_id
    LEFT JOIN coupons c ON u.coupon_type = 'DISCOUNT' AND c.id = u.coupon_id
    WHERE COALESCE(tc.partner_id, c.partner_id) IS NOT NULL
    ORDER BY u.owner_user_id, u.used_at, u.coupon_type DESC, u.coupon_id
)
SELECT DISTINCT ON (u.owner_user_id)
       u.owner_user_id, u.coupon_type, u.coupon_id, COALESCE(tc.code, c.code)::text, COALESCE(tc.campaign, c.campaign)::text, u.used_at,
       pu.partner_id, pu.used_at
FROM uses u
LEFT JOIN trial_coupons tc ON u.coupon_type = 'TRIAL' AND tc.id = u.coupon_id
LEFT JOIN coupons c ON u.coupon_type = 'DISCOUNT' AND c.id = u.coupon_id
LEFT JOIN partner_uses pu ON pu.owner_user_id = u.owner_user_id
ORDER BY u.owner_user_id, u.used_at, u.coupon_type DESC, u.coupon_id;
