-- DEB-076 (ASS-006): aviso de atraso de pagamento, uma vez por marco de cada atraso. O marco é o dia da carência: 0 (vencimento),
-- 3 (três dias depois) e 7 (último dia, "congelam hoje"). A linha é a reserva que torna o job idempotente. Vale para o site
-- (Asaas) e para as lojas, que não têm linha em `subscriptions`, por isso não há FK para ela. `past_due_since` é a âncora do atraso
-- (o vencimento): um atraso novo, depois de pagar, tem outra âncora e recomeça os marcos.
CREATE TABLE subscription_past_due_notices (
    owner_user_id uuid NOT NULL REFERENCES access_users (id),
    provider varchar(16) NOT NULL CHECK (provider IN ('ASAAS', 'APP_STORE', 'GOOGLE_PLAY')),
    past_due_since timestamptz NOT NULL,
    milestone smallint NOT NULL CHECK (milestone IN (0, 3, 7)),
    sent_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_user_id, provider, past_due_since, milestone)
);
