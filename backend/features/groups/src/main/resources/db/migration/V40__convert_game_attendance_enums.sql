-- Native enums for game lifecycle and attendance. CHECK on varchar already
-- rejected unknown labels; the type makes the same contract visible in the
-- catalog and in the Supabase table editor.
--
-- Types are created only when missing. If a homonymous enum already exists
-- (manual Supabase type), its labels must match exactly — including order —
-- because ORDER BY on an enum follows declaration order.
--
-- Game status spelling is CANCELLED (two L), distinct from subscription CANCELED.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $ensure$
BEGIN
    IF to_regtype('public.game_status') IS NULL THEN
        CREATE TYPE public.game_status AS ENUM ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.game_status'::regtype
    ) IS DISTINCT FROM ARRAY['DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED']::text[] THEN
        RAISE EXCEPTION 'public.game_status exists with unexpected labels';
    END IF;

    IF to_regtype('public.attendance_status') IS NULL THEN
        CREATE TYPE public.attendance_status AS ENUM ('CONFIRMED', 'DECLINED', 'WAITLISTED');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.attendance_status'::regtype
    ) IS DISTINCT FROM ARRAY['CONFIRMED', 'DECLINED', 'WAITLISTED']::text[] THEN
        RAISE EXCEPTION 'public.attendance_status exists with unexpected labels';
    END IF;

    IF to_regtype('public.attendance_source') IS NULL THEN
        CREATE TYPE public.attendance_source AS ENUM ('SELF', 'ORGANIZER', 'SYSTEM');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.attendance_source'::regtype
    ) IS DISTINCT FROM ARRAY['SELF', 'ORGANIZER', 'SYSTEM']::text[] THEN
        RAISE EXCEPTION 'public.attendance_source exists with unexpected labels';
    END IF;
END
$ensure$;

DO $preflight$
BEGIN
    IF EXISTS (
        SELECT 1 FROM games
        WHERE status NOT IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED')
    ) THEN
        RAISE EXCEPTION 'games has values that cannot become game_status';
    END IF;
    IF EXISTS (
        SELECT 1 FROM game_attendance
        WHERE status NOT IN ('CONFIRMED', 'DECLINED', 'WAITLISTED')
    ) THEN
        RAISE EXCEPTION 'game_attendance has values that cannot become attendance_status';
    END IF;
    IF EXISTS (
        SELECT 1 FROM attendance_events
        WHERE source NOT IN ('SELF', 'ORGANIZER', 'SYSTEM')
           OR new_status NOT IN ('CONFIRMED', 'DECLINED', 'WAITLISTED')
           OR old_status NOT IN ('CONFIRMED', 'DECLINED', 'WAITLISTED')
    ) THEN
        RAISE EXCEPTION 'attendance_events has values that cannot become native enums';
    END IF;
END
$preflight$;

ALTER TABLE games ALTER COLUMN status DROP DEFAULT;
ALTER TABLE games DROP CONSTRAINT IF EXISTS ck_games_status;
ALTER TABLE games DROP CONSTRAINT IF EXISTS games_schedule_start_unique;

ALTER TABLE game_attendance DROP CONSTRAINT IF EXISTS ck_game_attendance_status;
-- Indice parcial precisa cair antes da conversao: o predicado `(status)::text = 'WAITLISTED'`
-- passa a chamar enum_out, que e STABLE, e a recriacao automatica do indice reprova com
-- 42P17 (`functions in index predicate must be marked IMMUTABLE`). Recriado enum-nativo abaixo,
-- igual ao que games_schedule_start_unique ja faz.
DROP INDEX IF EXISTS uq_game_attendance_waitlist_sequence;
ALTER TABLE attendance_events DROP CONSTRAINT IF EXISTS ck_attendance_events_source;
ALTER TABLE attendance_events DROP CONSTRAINT IF EXISTS ck_attendance_events_old_status;
ALTER TABLE attendance_events DROP CONSTRAINT IF EXISTS ck_attendance_events_new_status;

ALTER TABLE games
    ALTER COLUMN status TYPE public.game_status
        USING status::text::public.game_status;

ALTER TABLE game_attendance
    ALTER COLUMN status TYPE public.attendance_status
        USING status::text::public.attendance_status;

ALTER TABLE attendance_events
    ALTER COLUMN source TYPE public.attendance_source
        USING source::text::public.attendance_source,
    ALTER COLUMN old_status TYPE public.attendance_status
        USING old_status::text::public.attendance_status,
    ALTER COLUMN new_status TYPE public.attendance_status
        USING new_status::text::public.attendance_status;

ALTER TABLE games
    ALTER COLUMN status SET DEFAULT 'DRAFT'::public.game_status;

-- Partial indexes/exclusions cannot be rewritten in place: enum input is
-- STABLE, and Postgres requires IMMUTABLE functions in index predicates.
-- Recreate after the column is already the enum so the literal is a Const.
CREATE UNIQUE INDEX uq_game_attendance_waitlist_sequence
    ON game_attendance (game_id, waitlist_sequence)
    WHERE status = 'WAITLISTED';

ALTER TABLE games
    ADD CONSTRAINT games_schedule_start_unique
    EXCLUDE USING gist (group_id WITH =, starts_at WITH =)
    WHERE (status IN ('DRAFT', 'PUBLISHED'))
    DEFERRABLE INITIALLY IMMEDIATE;
