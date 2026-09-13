-- Correction values are recoverable after timeout but never stored in plaintext.
CREATE TABLE receivable_registration_corrections (
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    operation_id uuid NOT NULL,
    payload_encrypted text NOT NULL,
    identity_snapshot_encrypted text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY(account_id, operation_id),
    FOREIGN KEY(account_id, operation_id) REFERENCES receivable_operations(account_id, id)
);
