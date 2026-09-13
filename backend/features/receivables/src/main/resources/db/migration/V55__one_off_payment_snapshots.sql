ALTER TABLE receivable_orders ADD COLUMN approval_fingerprint varchar(64);
ALTER TABLE receivable_orders ADD COLUMN approved_by uuid;
CREATE TABLE receivable_order_quotes (
    order_id uuid NOT NULL REFERENCES receivable_orders(id),
    method varchar(8) NOT NULL CHECK (method IN ('PIX','CARD')),
    quote jsonb NOT NULL,
    PRIMARY KEY(order_id,method)
);
CREATE TRIGGER receivable_order_quotes_immutable BEFORE UPDATE OR DELETE ON receivable_order_quotes
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
ALTER TABLE receivable_instruments ADD COLUMN request_id uuid;
ALTER TABLE receivable_instruments ADD COLUMN request_digest varchar(64);
ALTER TABLE receivable_instruments ADD COLUMN payload_encrypted text;
ALTER TABLE receivable_instruments ADD COLUMN confirmed boolean NOT NULL DEFAULT false;
ALTER TABLE receivable_instruments ADD COLUMN settled boolean NOT NULL DEFAULT false;
ALTER TABLE receivable_instruments ADD COLUMN available boolean NOT NULL DEFAULT false;
ALTER TABLE receivable_instruments ADD COLUMN split_settled boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX receivable_instrument_request ON receivable_instruments(account_id,request_id);
CREATE TABLE receivable_customers (
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    payer_id uuid NOT NULL,
    external_reference uuid NOT NULL UNIQUE,
    payer_data_encrypted text NOT NULL,
    provider_customer_id varchar(128),
    state varchar(16) NOT NULL CHECK (state IN ('READY','UNKNOWN','SUCCEEDED')),
    PRIMARY KEY(account_id,payer_id)
);
CREATE TABLE receivable_webhook_credentials (
    account_id uuid PRIMARY KEY REFERENCES receivable_accounts(id),
    token_encrypted text NOT NULL
);
CREATE TABLE receivable_payment_occurrences (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    instrument_id uuid,
    code varchar(64) NOT NULL,
    source_reference varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE(account_id,code,source_reference)
);
ALTER TABLE receivable_instruments ADD COLUMN next_reconcile_at timestamptz NOT NULL DEFAULT '-infinity';
ALTER TABLE receivable_provider_events ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT '-infinity';
CREATE INDEX receivable_instrument_recovery_due ON receivable_instruments(next_reconcile_at);
CREATE INDEX receivable_event_processing_due ON receivable_provider_events(next_attempt_at) WHERE processed_at IS NULL;
ALTER TABLE receivable_webhook_credentials ADD COLUMN state varchar(16) NOT NULL DEFAULT 'SUCCEEDED';
ALTER TABLE receivable_webhook_credentials ADD COLUMN provider_webhook_id varchar(128);
ALTER TABLE receivable_webhook_credentials ADD COLUMN request_id uuid;
ALTER TABLE receivable_webhook_credentials ADD COLUMN actor_user_id uuid;
ALTER TABLE receivable_customers DROP CONSTRAINT receivable_customers_state_check;
ALTER TABLE receivable_customers ADD CONSTRAINT receivable_customers_state_check CHECK (state IN ('READY','UNKNOWN','SUCCEEDED','REJECTED'));
ALTER TABLE receivable_instruments DROP CONSTRAINT receivable_instruments_status_check;
ALTER TABLE receivable_instruments ADD CONSTRAINT receivable_instruments_status_check CHECK (status IN
    ('CREATING','ACTIVE','UNKNOWN','CANCEL_PENDING','CANCELLED','EXPIRED','CONFIRMED','SETTLED','AVAILABLE','REFUNDED','CHARGEBACK','DISPUTED','RECOVERY_PENDING'));
