CREATE TYPE group_message_channel AS ENUM ('CHAT', 'NOTICE', 'REMINDER');

CREATE TABLE group_messages (
    id uuid PRIMARY KEY,
    sequence bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    group_id uuid NOT NULL REFERENCES access_groups(id),
    author_id uuid NOT NULL REFERENCES access_users(id),
    author_name varchar(120) NOT NULL,
    channel group_message_channel NOT NULL,
    body varchar(2000) NOT NULL CHECK (char_length(btrim(body)) BETWEEN 1 AND 2000),
    request_id uuid NOT NULL,
    game_id uuid,
    recipient_count integer NOT NULL DEFAULT 0 CHECK (recipient_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(group_id, author_id, channel, request_id),
    FOREIGN KEY (group_id, game_id) REFERENCES games(group_id, id),
    CHECK ((channel = 'REMINDER') = (game_id IS NOT NULL))
);
CREATE INDEX ix_group_messages_page ON group_messages(group_id, channel, sequence DESC);

CREATE TABLE group_notification_preferences (
    user_id uuid PRIMARY KEY REFERENCES access_users(id),
    notices boolean NOT NULL DEFAULT true,
    messages boolean NOT NULL DEFAULT true,
    reminders boolean NOT NULL DEFAULT true
);

CREATE TABLE group_notifications (
    sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES access_users(id),
    message_id uuid NOT NULL REFERENCES group_messages(id),
    read_at timestamptz,
    UNIQUE(recipient_id, message_id)
);
CREATE INDEX ix_group_notifications_inbox ON group_notifications(recipient_id, sequence DESC);
