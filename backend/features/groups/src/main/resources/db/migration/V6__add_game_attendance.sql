ALTER TABLE games
    ADD COLUMN waitlist_sequence_allocator bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_games_waitlist_allocator CHECK (waitlist_sequence_allocator >= 0);

CREATE TABLE game_attendance (
    game_id uuid NOT NULL,
    group_id uuid NOT NULL,
    member_user_id uuid NOT NULL,
    status varchar(16) NOT NULL,
    waitlist_sequence bigint,
    responded_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    PRIMARY KEY (game_id, member_user_id),
    CONSTRAINT fk_game_attendance_game
        FOREIGN KEY (group_id, game_id) REFERENCES games (group_id, id),
    CONSTRAINT fk_game_attendance_member
        FOREIGN KEY (group_id, member_user_id) REFERENCES group_memberships (group_id, user_id),
    CONSTRAINT ck_game_attendance_status
        CHECK (status IN ('CONFIRMED', 'DECLINED', 'WAITLISTED')),
    CONSTRAINT ck_game_attendance_waitlist_identity CHECK (
        (status = 'WAITLISTED' AND waitlist_sequence IS NOT NULL AND waitlist_sequence >= 1)
        OR (status <> 'WAITLISTED' AND waitlist_sequence IS NULL)
    ),
    CONSTRAINT ck_game_attendance_times CHECK (updated_at >= responded_at),
    CONSTRAINT ck_game_attendance_version CHECK (version >= 1)
);

CREATE UNIQUE INDEX uq_game_attendance_waitlist_sequence
    ON game_attendance (game_id, waitlist_sequence)
    WHERE status = 'WAITLISTED';
CREATE INDEX ix_game_attendance_counts ON game_attendance (game_id, status);

CREATE FUNCTION enforce_monotonic_waitlist_sequence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    allocated bigint;
BEGIN
    IF NEW.status = 'WAITLISTED'
        AND (TG_OP = 'INSERT' OR OLD.status <> 'WAITLISTED' OR OLD.waitlist_sequence <> NEW.waitlist_sequence)
    THEN
        SELECT waitlist_sequence_allocator INTO allocated
        FROM games
        WHERE group_id = NEW.group_id AND id = NEW.game_id
        FOR UPDATE;

        IF NEW.waitlist_sequence <> allocated + 1 THEN
            RAISE EXCEPTION 'waitlist sequence must be the next monotonic value';
        END IF;

        UPDATE games
        SET waitlist_sequence_allocator = NEW.waitlist_sequence
        WHERE group_id = NEW.group_id AND id = NEW.game_id;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER game_attendance_monotonic_waitlist
    BEFORE INSERT OR UPDATE ON game_attendance
    FOR EACH ROW EXECUTE FUNCTION enforce_monotonic_waitlist_sequence();

CREATE TABLE attendance_events (
    id uuid PRIMARY KEY,
    game_id uuid NOT NULL,
    group_id uuid NOT NULL,
    member_user_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    source varchar(16) NOT NULL,
    old_status varchar(16),
    new_status varchar(16) NOT NULL,
    reason varchar(500),
    occurred_at timestamptz NOT NULL,
    CONSTRAINT fk_attendance_events_game
        FOREIGN KEY (group_id, game_id) REFERENCES games (group_id, id),
    CONSTRAINT fk_attendance_events_member
        FOREIGN KEY (group_id, member_user_id) REFERENCES group_memberships (group_id, user_id),
    CONSTRAINT fk_attendance_events_actor
        FOREIGN KEY (actor_user_id) REFERENCES access_users (id),
    CONSTRAINT ck_attendance_events_source
        CHECK (source IN ('SELF', 'ORGANIZER', 'SYSTEM')),
    CONSTRAINT ck_attendance_events_old_status
        CHECK (old_status IS NULL OR old_status IN ('CONFIRMED', 'DECLINED', 'WAITLISTED')),
    CONSTRAINT ck_attendance_events_new_status
        CHECK (new_status IN ('CONFIRMED', 'DECLINED', 'WAITLISTED')),
    CONSTRAINT ck_attendance_events_change
        CHECK (old_status IS NULL OR old_status <> new_status),
    CONSTRAINT ck_attendance_events_reason CHECK (
        reason IS NULL OR (
            reason = btrim(reason)
            AND char_length(reason) BETWEEN 2 AND 500
            AND reason !~ '[[:cntrl:]]'
        )
    ),
    CONSTRAINT ck_attendance_events_organizer_reason CHECK (
        source <> 'ORGANIZER' OR reason IS NOT NULL
    )
);

CREATE INDEX ix_attendance_events_history
    ON attendance_events (group_id, game_id, member_user_id, occurred_at, id);

CREATE FUNCTION reject_attendance_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'attendance events are append only';
END
$$;

CREATE TRIGGER attendance_events_append_only
    BEFORE UPDATE OR DELETE ON attendance_events
    FOR EACH ROW EXECUTE FUNCTION reject_attendance_event_mutation();
