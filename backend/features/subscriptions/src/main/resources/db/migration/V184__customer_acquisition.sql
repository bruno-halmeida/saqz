-- Origem do cliente: uma linha por pessoa que usou cupom. Somente leitura.
--   owner_user_id  a pessoa (access_users.id).
--   coupon_type    TRIAL (trial_coupons, uso = organizer_trials.started_at com coupon_id) ou
--                  DISCOUNT (coupons, uso = coupon_redemptions.redeemed_at).
--   coupon_id, coupon_code, campaign  o cupom do primeiro uso; campaign de trial_coupons ou de coupons (V182).
--   first_used_at  quando o primeiro cupom foi usado.
-- A primeira origem vence: quem usou dois cupons fica com o mais antigo (empate: TRIAL, depois o menor id).
-- Quem nunca usou cupom nao aparece ("Sem cupom"). Pagamento antes de first_used_at tambem e "Sem cupom".
-- Ponto de extensao: outras origens (ex.: parceiro) devem entrar como novas linhas em `uses`, mantendo as colunas.
CREATE VIEW customer_acquisition (owner_user_id, coupon_type, coupon_id, coupon_code, campaign, first_used_at) AS
WITH uses AS (
    SELECT t.owner_user_id, 'TRIAL'::text AS coupon_type, t.coupon_id, t.started_at AS used_at
    FROM organizer_trials t
    WHERE t.coupon_id IS NOT NULL
    UNION ALL
    SELECT r.user_id, 'DISCOUNT'::text, r.coupon_id, r.redeemed_at
    FROM coupon_redemptions r
)
SELECT DISTINCT ON (u.owner_user_id)
       u.owner_user_id, u.coupon_type, u.coupon_id, COALESCE(tc.code, c.code)::text, COALESCE(tc.campaign, c.campaign)::text, u.used_at
FROM uses u
LEFT JOIN trial_coupons tc ON u.coupon_type = 'TRIAL' AND tc.id = u.coupon_id
LEFT JOIN coupons c ON u.coupon_type = 'DISCOUNT' AND c.id = u.coupon_id
ORDER BY u.owner_user_id, u.used_at, u.coupon_type DESC, u.coupon_id;
