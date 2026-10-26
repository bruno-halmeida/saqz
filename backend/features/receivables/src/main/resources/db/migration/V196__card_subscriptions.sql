-- Mensalidade no cartão, "modo Netflix" (FIN-012, DEB-140 F3). Substitui, para o que é novo, a recorrência de setembro
-- (receivable_recurrences fica, sem uso novo). Uma assinatura viva por pessoa por grupo; trocar o cartão abre um novo
-- checkout (replaces_id) e a antiga é encerrada quando a nova ativa.
CREATE TABLE card_subscriptions (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    group_id uuid NOT NULL,
    member_user_id uuid NOT NULL,
    status varchar(12) NOT NULL CHECK (status IN ('AUTHORIZING', 'ACTIVE', 'CANCELLED', 'ENDED', 'ABANDONED')),
    amount_cents bigint NOT NULL CHECK (amount_cents > 0),
    commission_cents bigint NOT NULL CHECK (commission_cents >= 0),
    -- Reajuste (FIN-012): valor novo e o primeiro vencimento em que ele vale; o aviso sai uma vez.
    next_amount_cents bigint CHECK (next_amount_cents IS NULL OR next_amount_cents > 0),
    next_amount_from date,
    change_notified_at timestamptz,
    first_due_date date NOT NULL,
    request_id uuid NOT NULL,
    replaces_id uuid REFERENCES card_subscriptions(id),
    provider_checkout_id varchar(128),
    checkout_url varchar(1000),
    provider_subscription_id varchar(128),
    refused_charge_id uuid,
    cancel_reason varchar(16) CHECK (cancel_reason IS NULL OR cancel_reason IN
        ('STOP_PLAYING', 'PREFER_PIX', 'CARD_PROBLEM', 'TOO_EXPENSIVE', 'OTHER', 'PREFER_NOT_SAY')),
    end_reason varchar(24) CHECK (end_reason IS NULL OR end_reason IN
        ('PAYER_CANCELLED', 'CARD_REPLACED', 'NOT_MENSALISTA', 'MEMBER_LEFT', 'GROUP_DISABLED', 'GROUP_DELETED', 'INELIGIBLE')),
    remote_cancel_pending boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    ended_at timestamptz,
    UNIQUE (member_user_id, request_id),
    UNIQUE (account_id, provider_subscription_id),
    UNIQUE (account_id, provider_checkout_id)
);
CREATE UNIQUE INDEX card_subscriptions_one_active ON card_subscriptions(group_id, member_user_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX card_subscriptions_one_authorizing ON card_subscriptions(group_id, member_user_id) WHERE status = 'AUTHORIZING';

-- Cobranças que a assinatura gerou no Asaas: isenção (remover antes do vencimento), recusa avisada uma vez e o prazo
-- para o pagamento sem mensalidade virar revisão.
CREATE TABLE card_subscription_payments (
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    provider_payment_id varchar(128) NOT NULL,
    subscription_id uuid NOT NULL REFERENCES card_subscriptions(id),
    due_date date NOT NULL,
    value_cents bigint NOT NULL,
    first_seen_at timestamptz NOT NULL,
    refused_notified_at timestamptz,
    exempt_removed_at timestamptz,
    PRIMARY KEY (account_id, provider_payment_id)
);
