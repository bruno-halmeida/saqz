CREATE TABLE charge_reminder_requests (
    group_id uuid NOT NULL REFERENCES access_groups(id),
    actor_id uuid NOT NULL REFERENCES access_users(id),
    request_id uuid NOT NULL,
    charge_ids text NOT NULL,
    notification_count integer NOT NULL,
    PRIMARY KEY (group_id, actor_id, request_id)
);
ALTER TABLE group_messages ADD COLUMN charge_id uuid REFERENCES group_charges(id);
CREATE TABLE notification_push_queue (
    notification_id bigint PRIMARY KEY REFERENCES group_notifications(sequence),
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);
CREATE TABLE notification_devices (
    installation_id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES access_users(id),
    token varchar(4096) NOT NULL UNIQUE,
    platform varchar(7) NOT NULL CHECK (platform IN ('ANDROID', 'IOS')),
    updated_at timestamptz NOT NULL DEFAULT now()
);
