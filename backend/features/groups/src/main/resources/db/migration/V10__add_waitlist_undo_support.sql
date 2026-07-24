ALTER TABLE attendance_events
    ADD COLUMN previous_waitlist_sequence bigint,
    ADD CONSTRAINT ck_attendance_events_previous_waitlist_sequence CHECK (
        previous_waitlist_sequence IS NULL
        OR (old_status = 'WAITLISTED' AND previous_waitlist_sequence >= 1)
    );

-- Undoing an automatic promotion restores the athlete to their original
-- waitlist position, which is normally an already-allocated (and by now
-- freed) sequence number below the current allocator. The original trigger
-- only accepted the exact next value; loosen it to accept any value up to
-- (and including) the next monotonic one, and only advance the allocator
-- when a genuinely new position is taken.
CREATE OR REPLACE FUNCTION enforce_monotonic_waitlist_sequence() RETURNS trigger LANGUAGE plpgsql AS $$
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

        IF NEW.waitlist_sequence > allocated + 1 THEN
            RAISE EXCEPTION 'waitlist sequence must not exceed the next monotonic value';
        END IF;

        IF NEW.waitlist_sequence > allocated THEN
            UPDATE games
            SET waitlist_sequence_allocator = NEW.waitlist_sequence
            WHERE group_id = NEW.group_id AND id = NEW.game_id;
        END IF;
    END IF;
    RETURN NEW;
END
$$;
