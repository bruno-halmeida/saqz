ALTER TABLE receivable_operation_audit DROP CONSTRAINT receivable_operation_audit_action_check;
ALTER TABLE receivable_operation_audit ADD CONSTRAINT receivable_operation_audit_action_check
    CHECK (action IN ('RECONCILE','REPROCESS','REJECT','RELEASE','RECOVER'));
ALTER TABLE receivable_operation_audit ADD COLUMN result varchar(32);
ALTER TABLE receivable_operation_audit ADD COLUMN operation_status varchar(16);

CREATE TABLE receivable_operational_recoveries (
    request_id uuid PRIMARY KEY,
    operation_id uuid NOT NULL REFERENCES receivable_operations(id),
    actor_user_id uuid NOT NULL,
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) BETWEEN 3 AND 500),
    request_digest varchar(64) NOT NULL,
    state varchar(16) NOT NULL CHECK (state IN ('RUNNING','COMPLETED')),
    lease_token uuid NOT NULL,
    lease_until timestamptz NOT NULL,
    result varchar(32),
    operation_status varchar(16),
    created_at timestamptz NOT NULL,
    completed_at timestamptz
);
CREATE INDEX receivable_operational_recovery_active
    ON receivable_operational_recoveries(operation_id, lease_until) WHERE state='RUNNING';
CREATE TRIGGER receivable_operational_recoveries_no_delete BEFORE DELETE ON receivable_operational_recoveries
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
