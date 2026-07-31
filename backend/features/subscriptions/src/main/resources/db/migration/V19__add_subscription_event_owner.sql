ALTER TABLE subscription_events
    ADD COLUMN owner_user_id uuid;

CREATE INDEX idx_subscription_events_owner_user_id
    ON subscription_events (owner_user_id);
