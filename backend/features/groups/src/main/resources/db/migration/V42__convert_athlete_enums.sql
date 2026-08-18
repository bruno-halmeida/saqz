-- Native enums for athlete attributes on group_memberships. CHECK on varchar
-- already rejected unknown labels; the type makes the same contract visible
-- in the catalog and in the Supabase table editor.
--
-- Types are created only when missing. If a homonymous enum already exists
-- (manual Supabase type), its labels must match exactly — including order —
-- because ORDER BY on an enum follows declaration order.
--
-- athlete_level stays separate from access_groups.level (group_level later).
-- position and secondary_position share athlete_position.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $ensure$
BEGIN
    IF to_regtype('public.athlete_position') IS NULL THEN
        CREATE TYPE public.athlete_position AS ENUM (
            'LIBERO', 'PONTA', 'CENTRAL', 'OPOSTO', 'LEVANTADOR'
        );
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.athlete_position'::regtype
    ) IS DISTINCT FROM ARRAY['LIBERO', 'PONTA', 'CENTRAL', 'OPOSTO', 'LEVANTADOR']::text[] THEN
        RAISE EXCEPTION 'public.athlete_position exists with unexpected labels';
    END IF;

    IF to_regtype('public.athlete_membership_type') IS NULL THEN
        CREATE TYPE public.athlete_membership_type AS ENUM ('MENSALISTA', 'AVULSO');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.athlete_membership_type'::regtype
    ) IS DISTINCT FROM ARRAY['MENSALISTA', 'AVULSO']::text[] THEN
        RAISE EXCEPTION 'public.athlete_membership_type exists with unexpected labels';
    END IF;

    IF to_regtype('public.athlete_level') IS NULL THEN
        CREATE TYPE public.athlete_level AS ENUM ('INICIANTE', 'INTERMEDIARIO', 'AVANCADO');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.athlete_level'::regtype
    ) IS DISTINCT FROM ARRAY['INICIANTE', 'INTERMEDIARIO', 'AVANCADO']::text[] THEN
        RAISE EXCEPTION 'public.athlete_level exists with unexpected labels';
    END IF;

    IF to_regtype('public.athlete_preferred_side') IS NULL THEN
        CREATE TYPE public.athlete_preferred_side AS ENUM ('DIREITA', 'ESQUERDA', 'TANTO_FAZ');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.athlete_preferred_side'::regtype
    ) IS DISTINCT FROM ARRAY['DIREITA', 'ESQUERDA', 'TANTO_FAZ']::text[] THEN
        RAISE EXCEPTION 'public.athlete_preferred_side exists with unexpected labels';
    END IF;
END
$ensure$;

DO $preflight$
BEGIN
    IF EXISTS (
        SELECT 1 FROM group_memberships
        WHERE position NOT IN ('LIBERO', 'PONTA', 'CENTRAL', 'OPOSTO', 'LEVANTADOR')
           OR secondary_position NOT IN ('LIBERO', 'PONTA', 'CENTRAL', 'OPOSTO', 'LEVANTADOR')
           OR membership_type NOT IN ('MENSALISTA', 'AVULSO')
           OR level NOT IN ('INICIANTE', 'INTERMEDIARIO', 'AVANCADO')
           OR preferred_side NOT IN ('DIREITA', 'ESQUERDA', 'TANTO_FAZ')
    ) THEN
        RAISE EXCEPTION 'group_memberships has values that cannot become native enums';
    END IF;
END
$preflight$;

ALTER TABLE group_memberships ALTER COLUMN membership_type DROP DEFAULT;

ALTER TABLE group_memberships DROP CONSTRAINT IF EXISTS ck_group_memberships_position;
ALTER TABLE group_memberships DROP CONSTRAINT IF EXISTS ck_group_memberships_membership_type;
ALTER TABLE group_memberships DROP CONSTRAINT IF EXISTS ck_group_memberships_level;
ALTER TABLE group_memberships DROP CONSTRAINT IF EXISTS ck_group_memberships_preferred_side;
-- Composite: closed-set IN-list is the enum; keep the distinct-from-position rule.
ALTER TABLE group_memberships DROP CONSTRAINT IF EXISTS ck_group_memberships_secondary_position;

ALTER TABLE group_memberships
    ALTER COLUMN position TYPE public.athlete_position
        USING position::text::public.athlete_position,
    ALTER COLUMN secondary_position TYPE public.athlete_position
        USING secondary_position::text::public.athlete_position,
    ALTER COLUMN membership_type TYPE public.athlete_membership_type
        USING membership_type::text::public.athlete_membership_type,
    ALTER COLUMN level TYPE public.athlete_level
        USING level::text::public.athlete_level,
    ALTER COLUMN preferred_side TYPE public.athlete_preferred_side
        USING preferred_side::text::public.athlete_preferred_side;

ALTER TABLE group_memberships
    ALTER COLUMN membership_type SET DEFAULT 'AVULSO'::public.athlete_membership_type;

ALTER TABLE group_memberships
    ADD CONSTRAINT ck_group_memberships_secondary_position
        CHECK (secondary_position IS NULL OR secondary_position IS DISTINCT FROM position);
