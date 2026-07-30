-- V16 only backfilled ACTIVE rows; a subscription that was already PAST_DUE when V16 ran
-- also earned a confirmed payment at some point and should keep the 7-day grace.
UPDATE subscriptions
SET first_confirmed_at = created_at
WHERE status = 'PAST_DUE'
  AND first_confirmed_at IS NULL;
