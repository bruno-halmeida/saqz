CREATE TABLE group_membership_removals (
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    removed_at timestamptz NOT NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_group_membership_removals_group
        FOREIGN KEY (group_id) REFERENCES access_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_membership_removals_user
        FOREIGN KEY (user_id) REFERENCES access_users (id)
);

CREATE INDEX ix_group_membership_removals_recent
    ON group_membership_removals (group_id, removed_at);

ALTER TABLE access_groups
    ADD COLUMN deleted_at timestamptz DEFAULT NULL;

CREATE INDEX ix_access_groups_active_owner
    ON access_groups (owner_user_id)
    WHERE deleted_at IS NULL;
