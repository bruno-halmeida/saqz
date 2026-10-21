-- Valor de cada pedido do Google Play, lido da Orders API (orders.batchGet) do Android Publisher.
--   gross_cents   Order.total: o que o comprador pagou, com descontos e impostos, em centavos de currency.
--   tax_cents     Order.tax: o imposto contido no total.
--   order_state   Order.state (PROCESSED, REFUNDED...): REFUNDED conta como estorno na receita.
--   amount_checked_at  ultima tentativa de leitura; nulo = nunca tentou. O job tenta de novo os sem valor.
-- Nulos ate a primeira leitura com sucesso: a receita nunca infere o valor pelo preco do plano.
ALTER TABLE google_play_orders
    ADD COLUMN gross_cents bigint,
    ADD COLUMN tax_cents bigint,
    ADD COLUMN currency varchar(3),
    ADD COLUMN order_state varchar(32),
    ADD COLUMN amount_checked_at timestamptz;

CREATE INDEX google_play_orders_without_amount_idx ON google_play_orders (amount_checked_at NULLS FIRST)
    WHERE gross_cents IS NULL AND test_purchase = false;

-- Igual a V176, exceto a linha do GOOGLE_PLAY: agora com bruto (Order.total), moeda e estorno (state REFUNDED).
-- Liquido continua nulo nas lojas (o Play so informa developerRevenueInBuyerCurrency, que nao e o repasse final).
CREATE OR REPLACE VIEW subscription_payments
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
SELECT 'GOOGLE_PLAY', o.owner_user_id, o.order_id, o.recorded_at, o.gross_cents, NULL::bigint, o.currency::text, o.test_purchase,
       COALESCE(o.order_state = 'REFUNDED', false)
FROM google_play_orders o;
