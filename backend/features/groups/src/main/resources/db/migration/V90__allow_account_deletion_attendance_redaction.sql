-- Keep attendance history immutable except for erasing free-text personal data
-- after the associated account has actually been deleted. All audit fields stay.
CREATE OR REPLACE FUNCTION reject_attendance_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
        AND NEW.reason = 'Conta excluída'
        AND (to_jsonb(NEW) - 'reason') = (to_jsonb(OLD) - 'reason')
        AND EXISTS (
            SELECT 1 FROM access_users
            WHERE id IN (OLD.actor_user_id, OLD.member_user_id)
              AND deleted_at IS NOT NULL
        )
    THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'attendance events are append only';
END
$$;
