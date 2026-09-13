ALTER TABLE receivable_transfers
    ADD COLUMN explicitly_authorized_at timestamptz,
    ADD COLUMN reserved_cents bigint NOT NULL DEFAULT 0,
    ADD COLUMN failure_code varchar(64),
    ADD COLUMN updated_at timestamptz;

UPDATE receivable_transfers
SET updated_at = created_at;
ALTER TABLE receivable_transfers ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE receivable_transfers
    ADD CONSTRAINT receivable_transfer_reservation_valid CHECK (
        reserved_cents >= 0 AND reserved_cents <= amount_cents AND
        (explicitly_authorized_at IS NULL OR
         (status IN ('REQUESTED','UNKNOWN') AND reserved_cents = amount_cents) OR
         (status NOT IN ('REQUESTED','UNKNOWN') AND reserved_cents = 0))
    );

CREATE INDEX receivable_active_transfer_reservations
    ON receivable_transfers(account_id) WHERE reserved_cents > 0;
