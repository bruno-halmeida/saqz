-- Asaas emits PAYMENT_CONFIRMED and PAYMENT_RECEIVED for the same charge under different event
-- ids, so the uq_subscription_events_asaas_event_id gate cannot collapse the pair. Applying a
-- confirmation now records the charge here; a second confirming event for the same charge is a
-- no-op instead of advancing current_period_end by another cycle.
-- Nullable and not backfilled: existing rows have no known confirming charge, and inventing one
-- would suppress the next legitimate confirmation for that subscription.
ALTER TABLE subscriptions
    ADD COLUMN last_confirmed_payment_id varchar(64);

-- Unique like uq_subscriptions_pending_upgrade_charge_id: one Asaas charge belongs to exactly one
-- subscription, and the webhook resolves the sibling event through this column.
CREATE UNIQUE INDEX uq_subscriptions_last_confirmed_payment_id
    ON subscriptions (last_confirmed_payment_id)
    WHERE last_confirmed_payment_id IS NOT NULL;
