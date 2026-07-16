CREATE TABLE access_users (
    id uuid PRIMARY KEY,
    firebase_subject varchar(128) NOT NULL,
    email varchar(320),
    email_verified boolean NOT NULL,
    display_name varchar(80),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_access_users_firebase_subject UNIQUE (firebase_subject),
    CONSTRAINT ck_access_users_email_verified CHECK (email_verified),
    CONSTRAINT ck_access_users_display_name CHECK (
        display_name IS NULL OR (
            display_name = btrim(display_name)
            AND char_length(display_name) BETWEEN 2 AND 80
            AND display_name !~ '[[:cntrl:]]'
        )
    )
);

CREATE TABLE access_groups (
    id uuid PRIMARY KEY,
    owner_user_id uuid NOT NULL,
    creation_key uuid NOT NULL,
    name varchar(80) NOT NULL,
    time_zone varchar(64) NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_access_groups_owner FOREIGN KEY (owner_user_id) REFERENCES access_users (id),
    CONSTRAINT uq_access_groups_owner_creation UNIQUE (owner_user_id, creation_key),
    CONSTRAINT ck_access_groups_name CHECK (
        name = btrim(name)
        AND char_length(name) BETWEEN 2 AND 80
        AND name !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_access_groups_version CHECK (version >= 1)
);

CREATE TABLE group_memberships (
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_group_memberships_group FOREIGN KEY (group_id) REFERENCES access_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_memberships_user FOREIGN KEY (user_id) REFERENCES access_users (id),
    CONSTRAINT ck_group_memberships_role CHECK (role IN ('ADMIN', 'ATHLETE'))
);

CREATE TABLE group_invites (
    group_id uuid PRIMARY KEY,
    token_digest bytea NOT NULL,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_group_invites_group FOREIGN KEY (group_id) REFERENCES access_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_invites_creator FOREIGN KEY (created_by_user_id) REFERENCES access_users (id),
    CONSTRAINT uq_group_invites_token_digest UNIQUE (token_digest)
);

CREATE TABLE invite_redemption_limits (
    user_id uuid PRIMARY KEY,
    window_started_at timestamptz NOT NULL,
    invalid_count integer NOT NULL,
    CONSTRAINT fk_invite_redemption_limits_user FOREIGN KEY (user_id) REFERENCES access_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_invite_redemption_limits_count CHECK (invalid_count BETWEEN 0 AND 10)
);
