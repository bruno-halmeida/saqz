CREATE TABLE receivable_condition_publications (
    request_id uuid PRIMARY KEY,
    actor_user_id uuid NOT NULL,
    kind varchar(16) NOT NULL CHECK (kind IN ('TERMS','FEE_SCHEDULE')),
    resource_id varchar(64) NOT NULL,
    request_digest varchar(64) NOT NULL,
    effective_at timestamptz NOT NULL,
    published_at timestamptz NOT NULL,
    CHECK (effective_at >= published_at)
);
CREATE TRIGGER receivable_condition_publications_immutable
    BEFORE UPDATE OR DELETE ON receivable_condition_publications
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
