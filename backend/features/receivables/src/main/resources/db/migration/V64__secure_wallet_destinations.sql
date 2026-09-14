ALTER TABLE receivable_bank_destinations
    ADD COLUMN request_id uuid,
    ADD COLUMN actor_user_id uuid,
    ADD COLUMN request_digest varchar(64);

CREATE UNIQUE INDEX receivable_destination_request
    ON receivable_bank_destinations(account_id, request_id) WHERE request_id IS NOT NULL;

ALTER TABLE receivable_bank_destinations
    ADD CONSTRAINT receivable_destination_request_complete CHECK (
        (request_id IS NULL AND actor_user_id IS NULL AND request_digest IS NULL) OR
        (request_id IS NOT NULL AND actor_user_id IS NOT NULL AND request_digest ~ '^[a-f0-9]{64}$')
    );

