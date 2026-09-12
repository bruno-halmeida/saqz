-- Financial identities intentionally do not cascade with users or groups.
-- CPF/CNPJ lookup is a keyed digest; legal data and provider credentials are encrypted.
CREATE TABLE receivable_accounts (
    id uuid PRIMARY KEY,
    owner_user_id uuid NOT NULL UNIQUE,
    legal_identity_digest varchar(128) NOT NULL UNIQUE,
    legal_data_encrypted text NOT NULL,
    provider_account_id varchar(128) UNIQUE,
    provider_wallet_id varchar(128) UNIQUE,
    credentials_encrypted text,
    registration varchar(24) NOT NULL CHECK (registration IN ('INCOMPLETE','UNDER_REVIEW','CORRECTION_REQUIRED','APPROVED','REJECTED')),
    new_operations_enabled boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0)
);

CREATE TABLE receivable_terms (
    version varchar(64) PRIMARY KEY,
    content text NOT NULL,
    content_sha256 varchar(64) NOT NULL,
    effective_at timestamptz NOT NULL,
    published_at timestamptz NOT NULL
);

CREATE TABLE receivable_terms_acceptances (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    actor_user_id uuid NOT NULL,
    terms_version varchar(64) NOT NULL REFERENCES receivable_terms(version),
    purpose varchar(32) NOT NULL CHECK (purpose IN ('ACCOUNT','DELEGATION','CHARGE','RECURRENCE')),
    request_id uuid NOT NULL,
    accepted_at timestamptz NOT NULL,
    UNIQUE(account_id, request_id),
    UNIQUE(account_id, id)
);

CREATE TABLE receivable_group_links (
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    group_id uuid NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    pix_enabled boolean NOT NULL DEFAULT false,
    card_enabled boolean NOT NULL DEFAULT false,
    activated_by uuid NOT NULL,
    activated_at timestamptz NOT NULL,
    disabled_at timestamptz,
    PRIMARY KEY(account_id, group_id),
    CHECK (NOT enabled OR pix_enabled OR card_enabled)
);
CREATE UNIQUE INDEX receivable_one_active_account_per_group ON receivable_group_links(group_id) WHERE enabled;

CREATE TABLE receivable_delegations (
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    user_id uuid NOT NULL,
    granted_by uuid NOT NULL,
    acceptance_id uuid NOT NULL,
    granted_at timestamptz NOT NULL,
    revoked_at timestamptz,
    PRIMARY KEY(account_id, user_id),
    FOREIGN KEY(account_id, acceptance_id) REFERENCES receivable_terms_acceptances(account_id, id)
);

CREATE TABLE receivable_fee_schedules (
    id uuid PRIMARY KEY,
    method varchar(8) NOT NULL CHECK (method IN ('PIX','CARD')),
    provider_rate numeric(12,10) NOT NULL CHECK (provider_rate >= 0 AND provider_rate < 1),
    provider_fixed_cents bigint NOT NULL CHECK (provider_fixed_cents >= 0),
    commission_rate numeric(12,10) NOT NULL CHECK (commission_rate >= 0 AND commission_rate <= 1),
    commission_fixed_cents bigint NOT NULL CHECK (commission_fixed_cents >= 0),
    terms_version varchar(64) NOT NULL REFERENCES receivable_terms(version),
    effective_at timestamptz NOT NULL,
    published_at timestamptz NOT NULL,
    created_by uuid NOT NULL,
    UNIQUE(method, effective_at)
);

CREATE TABLE receivable_recurrences (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    group_id uuid NOT NULL,
    member_user_id uuid NOT NULL,
    method varchar(8) NOT NULL CHECK (method IN ('PIX','CARD')),
    fee_schedule_id uuid NOT NULL REFERENCES receivable_fee_schedules(id),
    acceptance_id uuid NOT NULL,
    base_cents bigint NOT NULL CHECK (base_cents > 0),
    total_cents bigint NOT NULL CHECK (total_cents >= base_cents),
    first_due_date date NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('AUTHORIZING','ACTIVE','STOP_PENDING','STOPPED')),
    provider_subscription_id varchar(128),
    cutoff_at timestamptz,
    created_at timestamptz NOT NULL,
    UNIQUE(account_id, id),
    UNIQUE(account_id, provider_subscription_id),
    FOREIGN KEY(account_id, acceptance_id) REFERENCES receivable_terms_acceptances(account_id, id)
);
CREATE UNIQUE INDEX receivable_one_live_recurrence ON receivable_recurrences(group_id, member_user_id)
    WHERE status IN ('AUTHORIZING','ACTIVE','STOP_PENDING');

CREATE TABLE receivable_orders (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    group_id uuid NOT NULL,
    member_user_id uuid NOT NULL,
    group_charge_id uuid NOT NULL UNIQUE,
    recurrence_id uuid,
    billing_month date CHECK (billing_month = date_trunc('month', billing_month)::date),
    due_date date NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('ISSUED','CANCEL_PENDING','CANCELLED','PAID','REFUNDED','CHARGEBACK')),
    base_cents bigint NOT NULL CHECK (base_cents > 0),
    request_id uuid NOT NULL,
    issued_at timestamptz NOT NULL,
    UNIQUE(account_id, id),
    UNIQUE(account_id, request_id),
    FOREIGN KEY(account_id, recurrence_id) REFERENCES receivable_recurrences(account_id, id)
);
CREATE UNIQUE INDEX receivable_one_monthly_order ON receivable_orders(group_id, member_user_id, billing_month)
    WHERE billing_month IS NOT NULL;
CREATE INDEX receivable_payer_history ON receivable_orders(member_user_id, issued_at DESC, id);

CREATE TABLE receivable_instruments (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    order_id uuid NOT NULL,
    method varchar(8) NOT NULL CHECK (method IN ('PIX','CARD')),
    fee_schedule_id uuid NOT NULL REFERENCES receivable_fee_schedules(id),
    base_cents bigint NOT NULL CHECK (base_cents > 0),
    fees_cents bigint NOT NULL CHECK (fees_cents >= 0),
    total_cents bigint NOT NULL CHECK (total_cents = base_cents + fees_cents),
    commission_cents bigint NOT NULL CHECK (commission_cents >= 0),
    expected_provider_fee_cents bigint NOT NULL CHECK (expected_provider_fee_cents >= 0),
    expected_net_cents bigint NOT NULL CHECK (expected_net_cents >= base_cents),
    acceptance_id uuid NOT NULL,
    provider_payment_id varchar(128),
    provider_checkout_id varchar(128),
    provider_split_id varchar(128),
    status varchar(24) NOT NULL CHECK (status IN ('CREATING','ACTIVE','UNKNOWN','CANCEL_PENDING','CANCELLED','EXPIRED','CONFIRMED','SETTLED','AVAILABLE','REFUNDED','CHARGEBACK')),
    expires_at timestamptz,
    created_at timestamptz NOT NULL,
    UNIQUE(account_id, id),
    UNIQUE(account_id, provider_payment_id),
    UNIQUE(account_id, provider_checkout_id),
    FOREIGN KEY(account_id, order_id) REFERENCES receivable_orders(account_id, id),
    FOREIGN KEY(account_id, acceptance_id) REFERENCES receivable_terms_acceptances(account_id, id),
    CHECK (expected_net_cents = total_cents - commission_cents - expected_provider_fee_cents)
);
CREATE UNIQUE INDEX receivable_one_live_instrument ON receivable_instruments(order_id)
    WHERE status NOT IN ('CANCELLED','EXPIRED');

CREATE TABLE receivable_operations (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    request_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    kind varchar(32) NOT NULL,
    resource_id uuid NOT NULL,
    request_digest varchar(64) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('READY','RUNNING','UNKNOWN','SUCCEEDED','REJECTED')),
    provider_reference varchar(128),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    lease_token uuid,
    lease_until timestamptz,
    next_attempt_at timestamptz NOT NULL,
    failure_code varchar(64),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE(account_id, request_id),
    UNIQUE(account_id, id)
);
CREATE INDEX receivable_operation_recovery ON receivable_operations(status, next_attempt_at);

CREATE TABLE receivable_provider_events (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    provider_event_id varchar(128) NOT NULL,
    event_type varchar(128) NOT NULL,
    payload_encrypted text NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz,
    failure_code varchar(64),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    UNIQUE(account_id, provider_event_id)
);

CREATE TABLE receivable_bank_destinations (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    details_encrypted text NOT NULL,
    legal_identity_digest varchar(128) NOT NULL,
    verified_at timestamptz,
    disabled_at timestamptz,
    created_at timestamptz NOT NULL,
    UNIQUE(account_id, id)
);

CREATE TABLE receivable_transfers (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    destination_id uuid NOT NULL,
    operation_id uuid NOT NULL UNIQUE,
    amount_cents bigint NOT NULL CHECK (amount_cents > 0),
    fee_cents bigint NOT NULL CHECK (fee_cents >= 0),
    provider_transfer_id varchar(128),
    status varchar(24) NOT NULL CHECK (status IN ('REQUESTED','UNKNOWN','PROCESSING','COMPLETED','REJECTED','CANCELLED')),
    created_at timestamptz NOT NULL,
    UNIQUE(account_id, provider_transfer_id),
    FOREIGN KEY(account_id, operation_id) REFERENCES receivable_operations(account_id, id),
    FOREIGN KEY(account_id, destination_id) REFERENCES receivable_bank_destinations(account_id, id)
);

CREATE TABLE receivable_refunds (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    instrument_id uuid NOT NULL UNIQUE,
    operation_id uuid NOT NULL UNIQUE,
    total_cents bigint NOT NULL CHECK (total_cents > 0),
    manager_residual_cost_cents bigint CHECK (manager_residual_cost_cents >= 0),
    status varchar(24) NOT NULL CHECK (status IN ('REQUESTED','UNKNOWN','PROCESSING','COMPLETED','REJECTED')),
    created_at timestamptz NOT NULL,
    FOREIGN KEY(account_id, operation_id) REFERENCES receivable_operations(account_id, id),
    FOREIGN KEY(account_id, instrument_id) REFERENCES receivable_instruments(account_id, id)
);

CREATE TABLE receivable_movements (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    instrument_id uuid,
    kind varchar(24) NOT NULL CHECK (kind IN ('PAYMENT','PROVIDER_FEE','COMMISSION','SETTLEMENT','AVAILABILITY','TRANSFER','TRANSFER_FEE','REFUND','CHARGEBACK','RESIDUAL_COST')),
    amount_cents bigint NOT NULL,
    provider_reference varchar(128) NOT NULL,
    occurred_at timestamptz NOT NULL,
    UNIQUE(account_id, kind, provider_reference),
    FOREIGN KEY(account_id, instrument_id) REFERENCES receivable_instruments(account_id, id)
);
CREATE INDEX receivable_statement ON receivable_movements(account_id, occurred_at DESC, id);

CREATE TABLE receivable_cash_effects (
    order_id uuid NOT NULL REFERENCES receivable_orders(id),
    effect varchar(16) NOT NULL CHECK (effect IN ('PAYMENT','REVERSAL')),
    applied_at timestamptz,
    PRIMARY KEY(order_id, effect)
);

CREATE TABLE receivable_operation_audit (
    id uuid PRIMARY KEY,
    operation_id uuid NOT NULL REFERENCES receivable_operations(id),
    actor_user_id uuid NOT NULL,
    request_id uuid NOT NULL UNIQUE,
    action varchar(32) NOT NULL CHECK (action IN ('RECONCILE','REPROCESS','REJECT','RELEASE')),
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    created_at timestamptz NOT NULL
);

CREATE FUNCTION reject_receivable_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'receivable history is immutable'; END $$;
CREATE TRIGGER receivable_movements_immutable BEFORE UPDATE OR DELETE ON receivable_movements
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
CREATE TRIGGER receivable_terms_immutable BEFORE UPDATE OR DELETE ON receivable_terms
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
CREATE TRIGGER receivable_fees_immutable BEFORE UPDATE OR DELETE ON receivable_fee_schedules
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
CREATE TRIGGER receivable_acceptances_immutable BEFORE UPDATE OR DELETE ON receivable_terms_acceptances
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
CREATE TRIGGER receivable_operation_audit_immutable BEFORE UPDATE OR DELETE ON receivable_operation_audit
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
