-- Native enums for charges and expenses. CHECK on varchar already rejected
-- unknown labels; the type makes the same contract visible in the catalog
-- and in the Supabase table editor.
--
-- Types are created only when missing. If a homonymous enum already exists
-- (manual Supabase type), its labels must match exactly — including order —
-- because ORDER BY on an enum follows declaration order.
--
-- Charge status spelling is CANCELLED (two L), distinct from subscription
-- CANCELED. paid_method stays separate from subscription_billing_type.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $ensure$
BEGIN
    IF to_regtype('public.charge_kind') IS NULL THEN
        CREATE TYPE public.charge_kind AS ENUM ('GAME', 'MONTHLY');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.charge_kind'::regtype
    ) IS DISTINCT FROM ARRAY['GAME', 'MONTHLY']::text[] THEN
        RAISE EXCEPTION 'public.charge_kind exists with unexpected labels';
    END IF;

    IF to_regtype('public.charge_status') IS NULL THEN
        CREATE TYPE public.charge_status AS ENUM ('PENDING', 'PAID', 'WAIVED', 'CANCELLED');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.charge_status'::regtype
    ) IS DISTINCT FROM ARRAY['PENDING', 'PAID', 'WAIVED', 'CANCELLED']::text[] THEN
        RAISE EXCEPTION 'public.charge_status exists with unexpected labels';
    END IF;

    IF to_regtype('public.charge_paid_method') IS NULL THEN
        CREATE TYPE public.charge_paid_method AS ENUM ('PIX', 'CASH', 'OTHER');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.charge_paid_method'::regtype
    ) IS DISTINCT FROM ARRAY['PIX', 'CASH', 'OTHER']::text[] THEN
        RAISE EXCEPTION 'public.charge_paid_method exists with unexpected labels';
    END IF;

    IF to_regtype('public.expense_category') IS NULL THEN
        CREATE TYPE public.expense_category AS ENUM ('VENUE', 'EQUIPMENT', 'REFEREE', 'RACHA', 'OTHER');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.expense_category'::regtype
    ) IS DISTINCT FROM ARRAY['VENUE', 'EQUIPMENT', 'REFEREE', 'RACHA', 'OTHER']::text[] THEN
        RAISE EXCEPTION 'public.expense_category exists with unexpected labels';
    END IF;

    IF to_regtype('public.expense_status') IS NULL THEN
        CREATE TYPE public.expense_status AS ENUM ('ACTIVE', 'VOIDED');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.expense_status'::regtype
    ) IS DISTINCT FROM ARRAY['ACTIVE', 'VOIDED']::text[] THEN
        RAISE EXCEPTION 'public.expense_status exists with unexpected labels';
    END IF;

    IF to_regtype('public.expense_direction') IS NULL THEN
        CREATE TYPE public.expense_direction AS ENUM ('IN', 'OUT');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.expense_direction'::regtype
    ) IS DISTINCT FROM ARRAY['IN', 'OUT']::text[] THEN
        RAISE EXCEPTION 'public.expense_direction exists with unexpected labels';
    END IF;

    IF to_regtype('public.expense_action') IS NULL THEN
        CREATE TYPE public.expense_action AS ENUM ('CREATED', 'EDITED', 'VOIDED');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.expense_action'::regtype
    ) IS DISTINCT FROM ARRAY['CREATED', 'EDITED', 'VOIDED']::text[] THEN
        RAISE EXCEPTION 'public.expense_action exists with unexpected labels';
    END IF;
END
$ensure$;

DO $preflight$
BEGIN
    IF EXISTS (
        SELECT 1 FROM group_charges
        WHERE kind NOT IN ('GAME', 'MONTHLY')
           OR status NOT IN ('PENDING', 'PAID', 'WAIVED', 'CANCELLED')
           OR paid_method NOT IN ('PIX', 'CASH', 'OTHER')
    ) THEN
        RAISE EXCEPTION 'group_charges has values that cannot become native enums';
    END IF;
    IF EXISTS (
        SELECT 1 FROM group_charge_events
        WHERE new_status NOT IN ('PENDING', 'PAID', 'WAIVED', 'CANCELLED')
           OR old_status NOT IN ('PENDING', 'PAID', 'WAIVED', 'CANCELLED')
    ) THEN
        RAISE EXCEPTION 'group_charge_events has values that cannot become charge_status';
    END IF;
    IF EXISTS (
        SELECT 1 FROM group_expenses
        WHERE category NOT IN ('VENUE', 'EQUIPMENT', 'REFEREE', 'RACHA', 'OTHER')
           OR status NOT IN ('ACTIVE', 'VOIDED')
           OR direction NOT IN ('IN', 'OUT')
    ) THEN
        RAISE EXCEPTION 'group_expenses has values that cannot become native enums';
    END IF;
    IF EXISTS (
        SELECT 1 FROM group_expense_events
        WHERE action NOT IN ('CREATED', 'EDITED', 'VOIDED')
           OR category NOT IN ('VENUE', 'EQUIPMENT', 'REFEREE', 'RACHA', 'OTHER')
           OR status NOT IN ('ACTIVE', 'VOIDED')
           OR direction NOT IN ('IN', 'OUT')
    ) THEN
        RAISE EXCEPTION 'group_expense_events has values that cannot become native enums';
    END IF;
END
$preflight$;

ALTER TABLE group_charges ALTER COLUMN status DROP DEFAULT;
ALTER TABLE group_expenses ALTER COLUMN status DROP DEFAULT;
ALTER TABLE group_expenses ALTER COLUMN direction DROP DEFAULT;
ALTER TABLE group_expense_events ALTER COLUMN direction DROP DEFAULT;

ALTER TABLE group_charges DROP CONSTRAINT IF EXISTS ck_group_charges_kind;
ALTER TABLE group_charges DROP CONSTRAINT IF EXISTS ck_group_charges_status;
ALTER TABLE group_charges DROP CONSTRAINT IF EXISTS ck_group_charges_paid_method;
ALTER TABLE group_charge_events DROP CONSTRAINT IF EXISTS ck_group_charge_events_old;
ALTER TABLE group_charge_events DROP CONSTRAINT IF EXISTS ck_group_charge_events_new;
ALTER TABLE group_expenses DROP CONSTRAINT IF EXISTS ck_group_expenses_category;
ALTER TABLE group_expenses DROP CONSTRAINT IF EXISTS ck_group_expenses_status;
ALTER TABLE group_expenses DROP CONSTRAINT IF EXISTS ck_group_expenses_direction;
ALTER TABLE group_expense_events DROP CONSTRAINT IF EXISTS ck_group_expense_events_action;
ALTER TABLE group_expense_events DROP CONSTRAINT IF EXISTS ck_group_expense_events_direction;

-- Partial indexes cannot be rewritten in place: enum input is STABLE, and
-- Postgres requires IMMUTABLE functions in index predicates. Recreate after
-- the column is already the enum so the literal is a Const.
DROP INDEX IF EXISTS uq_group_charges_game_member;
DROP INDEX IF EXISTS uq_group_charges_month_member;

ALTER TABLE group_charges
    ALTER COLUMN kind TYPE public.charge_kind
        USING kind::text::public.charge_kind,
    ALTER COLUMN status TYPE public.charge_status
        USING status::text::public.charge_status,
    ALTER COLUMN paid_method TYPE public.charge_paid_method
        USING paid_method::text::public.charge_paid_method;

ALTER TABLE group_charge_events
    ALTER COLUMN old_status TYPE public.charge_status
        USING old_status::text::public.charge_status,
    ALTER COLUMN new_status TYPE public.charge_status
        USING new_status::text::public.charge_status;

ALTER TABLE group_expenses
    ALTER COLUMN category TYPE public.expense_category
        USING category::text::public.expense_category,
    ALTER COLUMN status TYPE public.expense_status
        USING status::text::public.expense_status,
    ALTER COLUMN direction TYPE public.expense_direction
        USING direction::text::public.expense_direction;

ALTER TABLE group_expense_events
    ALTER COLUMN action TYPE public.expense_action
        USING action::text::public.expense_action,
    ALTER COLUMN category TYPE public.expense_category
        USING category::text::public.expense_category,
    ALTER COLUMN status TYPE public.expense_status
        USING status::text::public.expense_status,
    ALTER COLUMN direction TYPE public.expense_direction
        USING direction::text::public.expense_direction;

ALTER TABLE group_charges
    ALTER COLUMN status SET DEFAULT 'PENDING'::public.charge_status;
ALTER TABLE group_expenses
    ALTER COLUMN status SET DEFAULT 'ACTIVE'::public.expense_status,
    ALTER COLUMN direction SET DEFAULT 'OUT'::public.expense_direction;
ALTER TABLE group_expense_events
    ALTER COLUMN direction SET DEFAULT 'OUT'::public.expense_direction;

CREATE UNIQUE INDEX uq_group_charges_game_member
    ON group_charges (group_id, game_id, member_user_id)
    WHERE kind = 'GAME';
CREATE UNIQUE INDEX uq_group_charges_month_member
    ON group_charges (group_id, billing_month, member_user_id)
    WHERE kind = 'MONTHLY';
