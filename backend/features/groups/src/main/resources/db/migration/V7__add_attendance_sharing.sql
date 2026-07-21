CREATE TABLE game_attendance_links (
    game_id uuid PRIMARY KEY,
    group_id uuid NOT NULL,
    token_digest bytea NOT NULL,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_game_attendance_links_game
        FOREIGN KEY (group_id, game_id) REFERENCES games (group_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_game_attendance_links_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
    CONSTRAINT fk_game_attendance_links_creator FOREIGN KEY (created_by_user_id) REFERENCES access_users (id),
    CONSTRAINT uq_game_attendance_links_token_digest UNIQUE (token_digest),
    CONSTRAINT ck_game_attendance_links_digest CHECK (octet_length(token_digest) = 32),
    CONSTRAINT ck_game_attendance_links_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX ix_game_attendance_links_group_updated
    ON game_attendance_links (group_id, updated_at DESC, game_id);

CREATE TABLE attendance_link_resolution_limits (
    user_id uuid PRIMARY KEY,
    window_started_at timestamptz NOT NULL,
    invalid_count integer NOT NULL,
    CONSTRAINT fk_attendance_link_resolution_limits_user
        FOREIGN KEY (user_id) REFERENCES access_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_attendance_link_resolution_limits_count CHECK (invalid_count BETWEEN 0 AND 10)
);
