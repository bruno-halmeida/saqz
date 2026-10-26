-- Baixa automática e revisão (FIN-014, DEB-140). Uma linha por pagamento do Asaas: o id do pagamento é a idempotência
-- (o aviso chega pelo menos uma vez). code_id é o código do Saqz que o pagamento quitou, se houver.
CREATE TABLE payment_settlements (
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    provider_payment_id varchar(128) NOT NULL,
    code_id uuid REFERENCES payment_codes(id),
    group_id uuid,
    method varchar(8) CHECK (method IN ('PIX', 'CARD')),
    value_cents bigint NOT NULL CHECK (value_cents >= 0),
    net_cents bigint,
    commission_cents bigint NOT NULL DEFAULT 0 CHECK (commission_cents >= 0),
    settled_charge_ids text NOT NULL DEFAULT '',
    leftover_cents bigint NOT NULL DEFAULT 0 CHECK (leftover_cents >= 0),
    commission_operation_id uuid,
    settled_at timestamptz NOT NULL,
    reversed_at timestamptz,
    PRIMARY KEY (account_id, provider_payment_id)
);

-- Pix que caiu fora de um código (ou sobra de um código já resolvido). Aparece em todos os grupos ativos da conta e
-- some de todos quando o gestor resolve. O Saqz não aprende o pagador.
CREATE TABLE payment_reviews (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    provider_payment_id varchar(128) NOT NULL,
    payer_name varchar(160) NOT NULL,
    amount_cents bigint NOT NULL CHECK (amount_cents > 0),
    fees_cents bigint NOT NULL DEFAULT 0 CHECK (fees_cents >= 0),
    received_at timestamptz NOT NULL,
    status varchar(12) NOT NULL CHECK (status IN ('OPEN', 'ASSIGNED', 'NOT_CHARGE')),
    group_id uuid,
    member_user_id uuid,
    settled_charge_ids text,
    leftover_cents bigint,
    expense_id uuid,
    resolve_request_id uuid,
    resolved_by uuid,
    resolved_at timestamptz,
    UNIQUE (account_id, provider_payment_id)
);
CREATE INDEX payment_reviews_open ON payment_reviews(account_id) WHERE status = 'OPEN';
