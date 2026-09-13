CREATE TABLE receivable_operational_notices (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL UNIQUE,
    actor_user_id uuid NOT NULL,
    audience varchar(24) NOT NULL CHECK (audience IN ('OPERATIONS','PLAN_OWNERS')),
    title varchar(120) NOT NULL CHECK (length(btrim(title)) BETWEEN 3 AND 120),
    message varchar(2000) NOT NULL CHECK (length(btrim(message)) BETWEEN 3 AND 2000),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz,
    request_digest varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX receivable_operational_notices_page
    ON receivable_operational_notices(created_at DESC, id DESC);
CREATE INDEX receivable_operational_notices_active
    ON receivable_operational_notices(audience, starts_at, ends_at);
CREATE TRIGGER receivable_operational_notices_immutable BEFORE UPDATE OR DELETE ON receivable_operational_notices
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
