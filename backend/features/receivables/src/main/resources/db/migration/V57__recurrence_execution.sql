ALTER TABLE receivable_recurrences
    ADD COLUMN request_id uuid,
    ADD COLUMN request_digest varchar(64),
    ADD COLUMN approval_fingerprint varchar(64),
    ADD COLUMN approved_by uuid,
    ADD COLUMN quote jsonb,
    ADD COLUMN payer_data_encrypted text,
    ADD COLUMN provider_checkout_id varchar(128),
    ADD COLUMN hosted_checkout_url varchar(1000),
    ADD COLUMN remote_state varchar(16) NOT NULL DEFAULT 'READY'
        CHECK (remote_state IN ('READY','RUNNING','UNKNOWN','SUCCEEDED','REJECTED')),
    ADD COLUMN remote_attempts integer NOT NULL DEFAULT 0 CHECK (remote_attempts >= 0),
    ADD COLUMN remote_lease_token uuid,
    ADD COLUMN remote_lease_until timestamptz,
    ADD COLUMN next_reconcile_at timestamptz NOT NULL DEFAULT '-infinity',
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

CREATE UNIQUE INDEX receivable_recurrence_request
    ON receivable_recurrences(account_id, request_id) WHERE request_id IS NOT NULL;
CREATE UNIQUE INDEX receivable_recurrence_actor_request
    ON receivable_recurrences(approved_by, request_id) WHERE request_id IS NOT NULL;
CREATE UNIQUE INDEX receivable_recurrence_checkout
    ON receivable_recurrences(account_id, provider_checkout_id) WHERE provider_checkout_id IS NOT NULL;
CREATE INDEX receivable_recurrence_recovery
    ON receivable_recurrences(status, remote_state, next_reconcile_at);

CREATE TABLE receivable_recurrence_cutoffs (
    recurrence_id uuid PRIMARY KEY REFERENCES receivable_recurrences(id),
    account_id uuid NOT NULL,
    request_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    reason varchar(32) NOT NULL CHECK (reason IN ('PAYER_CANCELLED','INELIGIBLE','GROUP_DISABLED','GROUP_DELETED','MEMBER_INACTIVE')),
    request_digest varchar(64) NOT NULL,
    requested_at timestamptz NOT NULL,
    completed_at timestamptz,
    UNIQUE(account_id, request_id),
    FOREIGN KEY(account_id, recurrence_id) REFERENCES receivable_recurrences(account_id, id)
);
