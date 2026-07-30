ALTER TABLE subscriptions
    ADD COLUMN first_confirmed_at timestamptz;

-- ACTIVE rows already passed a confirmed payment path; keep 7-day PAST_DUE grace.
UPDATE subscriptions
SET first_confirmed_at = created_at
WHERE status = 'ACTIVE'
  AND first_confirmed_at IS NULL;
