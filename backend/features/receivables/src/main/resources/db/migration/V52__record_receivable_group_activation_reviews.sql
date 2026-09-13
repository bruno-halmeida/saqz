CREATE TABLE receivable_group_configurations (
    operation_id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES receivable_accounts(id),
    group_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    enabled boolean NOT NULL,
    review_fingerprint varchar(64),
    accepted_conditions jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    FOREIGN KEY(account_id,operation_id) REFERENCES receivable_operations(account_id,id),
    CHECK (NOT enabled OR review_fingerprint IS NOT NULL)
);
CREATE TRIGGER receivable_group_configurations_immutable BEFORE UPDATE OR DELETE ON receivable_group_configurations
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
