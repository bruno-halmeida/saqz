-- Operational import only. Candidate credentials are encrypted with account/purpose binding.
CREATE TABLE receivable_credential_recoveries (
    request_id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    creation_operation_id uuid NOT NULL REFERENCES receivable_operations(id),
    owner_user_id uuid NOT NULL,
    operator_id uuid NOT NULL,
    provider_account_id varchar(128) NOT NULL,
    provider_wallet_id varchar(128) NOT NULL,
    credential_encrypted text NOT NULL,
    account_version bigint NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('PENDING','SUCCEEDED')),
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    CHECK ((status='SUCCEEDED') = (completed_at IS NOT NULL))
);
CREATE UNIQUE INDEX receivable_one_completed_credential_recovery
    ON receivable_credential_recoveries(account_id) WHERE status='SUCCEEDED';

-- Immutable evidence has no legal data, ciphertext or credential fingerprints.
CREATE TABLE receivable_credential_recovery_events (
    request_id uuid NOT NULL REFERENCES receivable_credential_recoveries(request_id),
    event varchar(16) NOT NULL CHECK (event IN ('REQUESTED','IMPORTED')),
    account_id uuid NOT NULL,
    creation_operation_id uuid NOT NULL,
    owner_user_id uuid NOT NULL,
    operator_id uuid NOT NULL,
    provider_account_id varchar(128) NOT NULL,
    provider_wallet_id varchar(128) NOT NULL,
    occurred_at timestamptz NOT NULL,
    PRIMARY KEY(request_id,event)
);
CREATE TRIGGER receivable_credential_recovery_events_immutable
    BEFORE UPDATE OR DELETE ON receivable_credential_recovery_events
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
