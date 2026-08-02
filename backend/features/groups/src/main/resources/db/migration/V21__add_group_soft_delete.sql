ALTER TABLE access_groups
    ADD COLUMN deleted_at timestamptz DEFAULT NULL;

CREATE INDEX ix_access_groups_active_owner
    ON access_groups (owner_user_id)
    WHERE deleted_at IS NULL;
