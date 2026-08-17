-- Native enums for the subscription closed sets. CHECK on varchar already rejected
-- unknown labels; the type makes the same contract visible in the catalog and in
-- the Supabase table editor.
--
-- Types are created only when missing. If a homonymous enum already exists
-- (manual Supabase type), its labels must match exactly — including order —
-- because ORDER BY on an enum follows declaration order.

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $ensure$
BEGIN
    IF to_regtype('public.subscription_plan') IS NULL THEN
        CREATE TYPE public.subscription_plan AS ENUM ('TITULAR', 'ORGANIZADOR', 'ILIMITADO');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.subscription_plan'::regtype
    ) IS DISTINCT FROM ARRAY['TITULAR', 'ORGANIZADOR', 'ILIMITADO']::text[] THEN
        RAISE EXCEPTION 'public.subscription_plan exists with unexpected labels';
    END IF;

    IF to_regtype('public.subscription_cycle') IS NULL THEN
        CREATE TYPE public.subscription_cycle AS ENUM ('MONTHLY', 'ANNUAL');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.subscription_cycle'::regtype
    ) IS DISTINCT FROM ARRAY['MONTHLY', 'ANNUAL']::text[] THEN
        RAISE EXCEPTION 'public.subscription_cycle exists with unexpected labels';
    END IF;

    IF to_regtype('public.subscription_status') IS NULL THEN
        CREATE TYPE public.subscription_status AS ENUM ('ACTIVE', 'PAST_DUE', 'CANCELED');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.subscription_status'::regtype
    ) IS DISTINCT FROM ARRAY['ACTIVE', 'PAST_DUE', 'CANCELED']::text[] THEN
        RAISE EXCEPTION 'public.subscription_status exists with unexpected labels';
    END IF;

    IF to_regtype('public.subscription_billing_type') IS NULL THEN
        CREATE TYPE public.subscription_billing_type AS ENUM ('PIX', 'CREDIT_CARD');
    ELSIF (
        SELECT array_agg(enumlabel::text ORDER BY enumsortorder)
        FROM pg_enum
        WHERE enumtypid = 'public.subscription_billing_type'::regtype
    ) IS DISTINCT FROM ARRAY['PIX', 'CREDIT_CARD']::text[] THEN
        RAISE EXCEPTION 'public.subscription_billing_type exists with unexpected labels';
    END IF;
END
$ensure$;

DO $preflight$
BEGIN
    IF EXISTS (
        SELECT 1 FROM subscriptions
        WHERE plan NOT IN ('TITULAR', 'ORGANIZADOR', 'ILIMITADO')
           OR cycle NOT IN ('MONTHLY', 'ANNUAL')
           OR status NOT IN ('ACTIVE', 'PAST_DUE', 'CANCELED')
           OR pending_plan NOT IN ('TITULAR', 'ORGANIZADOR', 'ILIMITADO')
           OR pending_upgrade_plan NOT IN ('TITULAR', 'ORGANIZADOR', 'ILIMITADO')
           OR billing_type NOT IN ('PIX', 'CREDIT_CARD')
    ) THEN
        RAISE EXCEPTION 'subscriptions has values that cannot become native enums';
    END IF;
END
$preflight$;

ALTER TABLE subscriptions ALTER COLUMN status DROP DEFAULT;

ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscriptions_plan;
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscriptions_pending_plan;
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscriptions_cycle;
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscriptions_status;
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscriptions_pending_upgrade_plan;
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscriptions_billing_type;

ALTER TABLE subscriptions
    ALTER COLUMN plan TYPE public.subscription_plan
        USING plan::text::public.subscription_plan,
    ALTER COLUMN pending_plan TYPE public.subscription_plan
        USING pending_plan::text::public.subscription_plan,
    ALTER COLUMN pending_upgrade_plan TYPE public.subscription_plan
        USING pending_upgrade_plan::text::public.subscription_plan,
    ALTER COLUMN cycle TYPE public.subscription_cycle
        USING cycle::text::public.subscription_cycle,
    ALTER COLUMN status TYPE public.subscription_status
        USING status::text::public.subscription_status,
    ALTER COLUMN billing_type TYPE public.subscription_billing_type
        USING billing_type::text::public.subscription_billing_type;

ALTER TABLE subscriptions
    ALTER COLUMN status SET DEFAULT 'ACTIVE'::public.subscription_status;
