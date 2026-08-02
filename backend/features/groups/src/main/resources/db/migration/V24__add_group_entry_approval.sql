ALTER TABLE access_groups
    ADD COLUMN entry_requires_approval boolean NOT NULL DEFAULT false;

CREATE TABLE group_entry_requests (
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    requested_at timestamptz NOT NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_group_entry_requests_group
        FOREIGN KEY (group_id) REFERENCES access_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_entry_requests_user
        FOREIGN KEY (user_id) REFERENCES access_users (id)
);
