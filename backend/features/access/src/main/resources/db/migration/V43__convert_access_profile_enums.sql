-- Native enums for access/group profile closed sets. CHECK on varchar already
-- rejected unknown labels; the type makes the same contract visible in the
-- catalog and in the Supabase table editor.
--
-- Types are created only when missing. If a homonymous enum already exists
-- (manual Supabase type), its labels must match exactly — including order —
-- because ORDER BY on an enum follows declaration order.
--
-- group_role stores ADMIN/ATHLETE only. OWNER is resolved in queries and is
-- not a persisted label. group_level stays separate from athlete_level.
-- privacy and currency stay varchar: they are currently singletons.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $ensure$
BEGIN
    IF to_regtype('public.group_role') IS NULL THEN
        CREATE TYPE public.group_role AS ENUM ('ADMIN', 'ATHLETE');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.group_role'::regtype
    ) IS DISTINCT FROM ARRAY['ADMIN', 'ATHLETE']::text[] THEN
        RAISE EXCEPTION 'public.group_role exists with unexpected labels';
    END IF;

    IF to_regtype('public.phone_visibility') IS NULL THEN
        CREATE TYPE public.phone_visibility AS ENUM ('EVERYONE', 'ADMINS', 'NOBODY');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.phone_visibility'::regtype
    ) IS DISTINCT FROM ARRAY['EVERYONE', 'ADMINS', 'NOBODY']::text[] THEN
        RAISE EXCEPTION 'public.phone_visibility exists with unexpected labels';
    END IF;

    IF to_regtype('public.group_profile_status') IS NULL THEN
        CREATE TYPE public.group_profile_status AS ENUM ('INCOMPLETE', 'COMPLETE');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.group_profile_status'::regtype
    ) IS DISTINCT FROM ARRAY['INCOMPLETE', 'COMPLETE']::text[] THEN
        RAISE EXCEPTION 'public.group_profile_status exists with unexpected labels';
    END IF;

    IF to_regtype('public.group_modality') IS NULL THEN
        CREATE TYPE public.group_modality AS ENUM (
            'COURT_VOLLEYBALL', 'BEACH_VOLLEYBALL', 'FOOTVOLLEY'
        );
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.group_modality'::regtype
    ) IS DISTINCT FROM ARRAY['COURT_VOLLEYBALL', 'BEACH_VOLLEYBALL', 'FOOTVOLLEY']::text[] THEN
        RAISE EXCEPTION 'public.group_modality exists with unexpected labels';
    END IF;

    IF to_regtype('public.group_composition') IS NULL THEN
        CREATE TYPE public.group_composition AS ENUM ('WOMEN', 'MEN', 'MIXED');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.group_composition'::regtype
    ) IS DISTINCT FROM ARRAY['WOMEN', 'MEN', 'MIXED']::text[] THEN
        RAISE EXCEPTION 'public.group_composition exists with unexpected labels';
    END IF;

    IF to_regtype('public.group_level') IS NULL THEN
        CREATE TYPE public.group_level AS ENUM (
            'BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'MIXED_LEVELS', 'CUSTOM'
        );
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.group_level'::regtype
    ) IS DISTINCT FROM ARRAY['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'MIXED_LEVELS', 'CUSTOM']::text[] THEN
        RAISE EXCEPTION 'public.group_level exists with unexpected labels';
    END IF;

    IF to_regtype('public.court_play_style') IS NULL THEN
        CREATE TYPE public.court_play_style AS ENUM (
            'SIX_ZERO', 'FOUR_TWO', 'FIVE_ONE', 'CUSTOM'
        );
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.court_play_style'::regtype
    ) IS DISTINCT FROM ARRAY['SIX_ZERO', 'FOUR_TWO', 'FIVE_ONE', 'CUSTOM']::text[] THEN
        RAISE EXCEPTION 'public.court_play_style exists with unexpected labels';
    END IF;

    IF to_regtype('public.promotion_mode') IS NULL THEN
        CREATE TYPE public.promotion_mode AS ENUM ('FIFO', 'MANUAL');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.promotion_mode'::regtype
    ) IS DISTINCT FROM ARRAY['FIFO', 'MANUAL']::text[] THEN
        RAISE EXCEPTION 'public.promotion_mode exists with unexpected labels';
    END IF;
END
$ensure$;

DO $preflight$
BEGIN
    IF EXISTS (
        SELECT 1 FROM group_memberships
        WHERE role NOT IN ('ADMIN', 'ATHLETE')
    ) THEN
        RAISE EXCEPTION 'group_memberships has values that cannot become group_role';
    END IF;
    IF EXISTS (
        SELECT 1 FROM access_users
        WHERE phone_visibility NOT IN ('EVERYONE', 'ADMINS', 'NOBODY')
    ) THEN
        RAISE EXCEPTION 'access_users has values that cannot become phone_visibility';
    END IF;
    IF EXISTS (
        SELECT 1 FROM access_groups
        WHERE profile_status NOT IN ('INCOMPLETE', 'COMPLETE')
           OR modality NOT IN ('COURT_VOLLEYBALL', 'BEACH_VOLLEYBALL', 'FOOTVOLLEY')
           OR composition NOT IN ('WOMEN', 'MEN', 'MIXED')
           OR level NOT IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'MIXED_LEVELS', 'CUSTOM')
           OR play_style NOT IN ('SIX_ZERO', 'FOUR_TWO', 'FIVE_ONE', 'CUSTOM')
           OR promotion_mode NOT IN ('FIFO', 'MANUAL')
    ) THEN
        RAISE EXCEPTION 'access_groups has values that cannot become native enums';
    END IF;
END
$preflight$;

ALTER TABLE access_users ALTER COLUMN phone_visibility DROP DEFAULT;
ALTER TABLE access_groups ALTER COLUMN profile_status DROP DEFAULT;
ALTER TABLE access_groups ALTER COLUMN promotion_mode DROP DEFAULT;

ALTER TABLE group_memberships DROP CONSTRAINT IF EXISTS ck_group_memberships_role;
ALTER TABLE access_users DROP CONSTRAINT IF EXISTS ck_access_users_phone_visibility;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS ck_access_groups_profile_status;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS ck_access_groups_modality;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS ck_access_groups_composition;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS ck_access_groups_level;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS ck_access_groups_play_style;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS ck_access_groups_promotion_mode;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS ck_access_groups_custom_level;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS ck_access_groups_court_play_style;
ALTER TABLE access_groups DROP CONSTRAINT IF EXISTS ck_access_groups_custom_play_style;

ALTER TABLE group_memberships
    ALTER COLUMN role TYPE public.group_role
        USING role::text::public.group_role;

ALTER TABLE access_users
    ALTER COLUMN phone_visibility TYPE public.phone_visibility
        USING phone_visibility::text::public.phone_visibility;

ALTER TABLE access_groups
    ALTER COLUMN profile_status TYPE public.group_profile_status
        USING profile_status::text::public.group_profile_status,
    ALTER COLUMN modality TYPE public.group_modality
        USING modality::text::public.group_modality,
    ALTER COLUMN composition TYPE public.group_composition
        USING composition::text::public.group_composition,
    ALTER COLUMN level TYPE public.group_level
        USING level::text::public.group_level,
    ALTER COLUMN play_style TYPE public.court_play_style
        USING play_style::text::public.court_play_style,
    ALTER COLUMN promotion_mode TYPE public.promotion_mode
        USING promotion_mode::text::public.promotion_mode;

ALTER TABLE access_users
    ALTER COLUMN phone_visibility SET DEFAULT 'ADMINS'::public.phone_visibility;
ALTER TABLE access_groups
    ALTER COLUMN profile_status SET DEFAULT 'INCOMPLETE'::public.group_profile_status,
    ALTER COLUMN promotion_mode SET DEFAULT 'FIFO'::public.promotion_mode;

ALTER TABLE access_groups
    ADD CONSTRAINT ck_access_groups_profile_status CHECK (
        profile_status = 'INCOMPLETE'
        OR (modality IS NOT NULL AND composition IS NOT NULL)
    ),
    ADD CONSTRAINT ck_access_groups_custom_level CHECK (
        (
            level = 'CUSTOM'
            AND custom_level IS NOT NULL
            AND custom_level = btrim(custom_level)
            AND char_length(custom_level) BETWEEN 2 AND 40
            AND custom_level !~ '[[:cntrl:]]'
        )
        OR (
            level IS DISTINCT FROM 'CUSTOM'
            AND custom_level IS NULL
        )
    ),
    ADD CONSTRAINT ck_access_groups_court_play_style CHECK (
        modality = 'COURT_VOLLEYBALL'
        OR (play_style IS NULL AND custom_play_style IS NULL)
    ),
    ADD CONSTRAINT ck_access_groups_custom_play_style CHECK (
        (
            play_style = 'CUSTOM'
            AND custom_play_style IS NOT NULL
            AND custom_play_style = btrim(custom_play_style)
            AND char_length(custom_play_style) BETWEEN 2 AND 40
            AND custom_play_style !~ '[[:cntrl:]]'
        )
        OR (
            play_style IS DISTINCT FROM 'CUSTOM'
            AND custom_play_style IS NULL
        )
    );
