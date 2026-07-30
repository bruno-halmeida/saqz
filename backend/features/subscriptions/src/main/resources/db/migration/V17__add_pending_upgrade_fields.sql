ALTER TABLE subscriptions
    ADD COLUMN pending_upgrade_plan varchar(16),
    ADD COLUMN pending_upgrade_charge_id varchar(64);

ALTER TABLE subscriptions
    ADD CONSTRAINT ck_subscriptions_pending_upgrade_plan
        CHECK (pending_upgrade_plan IS NULL OR pending_upgrade_plan IN ('TITULAR', 'ORGANIZADOR', 'ILIMITADO'));

CREATE UNIQUE INDEX uq_subscriptions_pending_upgrade_charge_id
    ON subscriptions (pending_upgrade_charge_id)
    WHERE pending_upgrade_charge_id IS NOT NULL;
