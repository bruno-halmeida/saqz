ALTER TABLE access_users
    ADD COLUMN deleted_at timestamptz DEFAULT NULL;

ALTER TABLE access_users
    DROP CONSTRAINT uq_access_users_firebase_subject;

CREATE UNIQUE INDEX uq_access_users_active_firebase_subject
    ON access_users (firebase_subject)
    WHERE deleted_at IS NULL;
