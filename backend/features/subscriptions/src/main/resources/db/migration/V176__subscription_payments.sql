-- Pagamentos de assinatura num lugar so (DEB-100), para a receita real x teste do painel administrativo. Somente leitura.
--   SITE        um pagamento por payment.id do Asaas: PAYMENT_CONFIRMED e PAYMENT_RECEIVED do mesmo id contam uma vez (data = o primeiro
--               dos dois); bruto = payment.value (ja com desconto de cupom), liquido = payment.netValue; estornado se houver
--               PAYMENT_REFUNDED ou PAYMENT_RECEIVED_IN_CASH_UNDONE do mesmo id. So entra pagamento com valor > 0.
--   APP_STORE   uma linha por cobranca (app_store_transactions); bruto = price_millis / 10 (centavos da moeda); sem liquido; teste = SANDBOX.
--   GOOGLE_PLAY um pedido por linha (google_play_orders); a tabela nao guarda valor, entao bruto e liquido ficam nulos; teste = test_purchase.
-- Espelha ProcessAsaasWebhook.CONFIRMING_EVENT_TYPES / REVOKING_EVENT_TYPES: quem mudar la muda aqui.
-- O cast numerico fica dentro do CASE com a mesma regex das outras consultas, para texto nao numerico nao derrubar a leitura.
CREATE VIEW subscription_payments
    (source, owner_user_id, payment_ref, paid_at, gross_cents, net_cents, currency, test, refunded) AS
WITH site_events AS (
    SELECT owner_user_id, asaas_event_id, type, processed_at, payload::jsonb AS p
    FROM subscription_events
    WHERE processed_at IS NOT NULL
      AND type IN ('PAYMENT_CONFIRMED', 'PAYMENT_RECEIVED', 'PAYMENT_REFUNDED', 'PAYMENT_RECEIVED_IN_CASH_UNDONE')
),
site_values AS (
    SELECT owner_user_id, type, processed_at,
           COALESCE(p #>> '{payment,id}', asaas_event_id) AS payment_ref,
           CASE WHEN p #>> '{payment,value}' ~ '^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?$'
                THEN round((p #>> '{payment,value}')::numeric * 100)::bigint END AS gross_cents,
           CASE WHEN p #>> '{payment,netValue}' ~ '^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?$'
                THEN round((p #>> '{payment,netValue}')::numeric * 100)::bigint END AS net_cents
    FROM site_events
),
site_paid AS (
    SELECT payment_ref,
           (array_agg(owner_user_id ORDER BY processed_at) FILTER (WHERE owner_user_id IS NOT NULL))[1] AS owner_user_id,
           min(processed_at) AS paid_at,
           (array_agg(gross_cents ORDER BY processed_at))[1] AS gross_cents,
           (array_agg(net_cents ORDER BY processed_at))[1] AS net_cents
    FROM site_values
    WHERE type IN ('PAYMENT_CONFIRMED', 'PAYMENT_RECEIVED') AND gross_cents > 0
    GROUP BY payment_ref
)
SELECT 'SITE'::text, sp.owner_user_id, sp.payment_ref, sp.paid_at, sp.gross_cents, sp.net_cents, 'BRL'::text, false,
       EXISTS (SELECT 1 FROM site_values r
               WHERE r.payment_ref = sp.payment_ref
                 AND r.type IN ('PAYMENT_REFUNDED', 'PAYMENT_RECEIVED_IN_CASH_UNDONE'))
FROM site_paid sp
UNION ALL
SELECT 'APP_STORE', t.owner_user_id, t.transaction_id, t.purchase_date, round(t.price_millis / 10.0)::bigint, NULL::bigint,
       t.currency::text, upper(t.environment) = 'SANDBOX', t.revoked_at IS NOT NULL
FROM app_store_transactions t
UNION ALL
SELECT 'GOOGLE_PLAY', o.owner_user_id, o.order_id, o.recorded_at, NULL::bigint, NULL::bigint, NULL::text, o.test_purchase, false
FROM google_play_orders o;
