CREATE TABLE receivable_pix_renewals (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL,
    order_id uuid NOT NULL,
    instrument_id uuid NOT NULL,
    request_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    request_digest varchar(64) NOT NULL,
    due_date date NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('READY','RUNNING','UNKNOWN','SUCCEEDED','REJECTED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    lease_token uuid,
    lease_until timestamptz,
    next_attempt_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE(account_id, request_id),
    FOREIGN KEY(account_id, order_id) REFERENCES receivable_orders(account_id, id),
    FOREIGN KEY(account_id, instrument_id) REFERENCES receivable_instruments(account_id, id)
);
CREATE INDEX receivable_pix_renewal_recovery ON receivable_pix_renewals(status, next_attempt_at);
