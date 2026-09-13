CREATE TABLE receivable_rollout (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    backend_mode varchar(24) NOT NULL DEFAULT 'OFF' CHECK (backend_mode IN ('OFF','SELECTED_USERS','ALL_USERS')),
    mobile_mode varchar(24) NOT NULL DEFAULT 'OFF' CHECK (mobile_mode IN ('OFF','SELECTED_USERS','ALL_USERS'))
);
INSERT INTO receivable_rollout(singleton) VALUES (true);
CREATE TABLE receivable_rollout_users (
    user_id uuid PRIMARY KEY,
    version bigint NOT NULL CHECK (version > 0)
);
CREATE TABLE receivable_rollout_overrides (
    user_id uuid NOT NULL REFERENCES receivable_rollout_users(user_id),
    system varchar(8) NOT NULL CHECK (system IN ('BACKEND','MOBILE')),
    decision varchar(8) NOT NULL CHECK (decision IN ('ALLOW','DENY')),
    PRIMARY KEY(user_id, system)
);
CREATE TABLE receivable_rollout_history (
    request_id uuid PRIMARY KEY,
    actor_user_id uuid NOT NULL,
    user_id uuid,
    request_content text NOT NULL,
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) BETWEEN 3 AND 500),
    created_at timestamptz NOT NULL,
    before_state jsonb NOT NULL,
    after_state jsonb NOT NULL
);
CREATE INDEX receivable_rollout_history_page ON receivable_rollout_history(created_at DESC, request_id);
CREATE TRIGGER receivable_rollout_history_immutable BEFORE UPDATE OR DELETE ON receivable_rollout_history
    FOR EACH ROW EXECUTE FUNCTION reject_receivable_history_mutation();
