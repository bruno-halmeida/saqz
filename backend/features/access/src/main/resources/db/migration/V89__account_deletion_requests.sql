-- Deletion is accepted atomically with local anonymization. External provider
-- cleanup is retried after crashes/outages. Only a UID digest remains on completion
-- to prevent an old, cached Firebase token from recreating a deleted account.
CREATE TABLE account_deletion_requests (
    user_id uuid PRIMARY KEY REFERENCES access_users(id) ON DELETE CASCADE,
    subject_digest bytea NOT NULL UNIQUE CHECK (octet_length(subject_digest) = 32),
    firebase_subject varchar(128),
    requested_at timestamptz NOT NULL DEFAULT now(),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0,
    completed_at timestamptz,
    CHECK ((completed_at IS NULL) = (firebase_subject IS NOT NULL))
);
CREATE INDEX account_deletion_pending ON account_deletion_requests(next_attempt_at)
    WHERE completed_at IS NULL;

CREATE FUNCTION prevent_deleted_account_recreation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.deleted_at IS NULL AND EXISTS (
        SELECT 1 FROM account_deletion_requests
        WHERE subject_digest = sha256(convert_to(NEW.firebase_subject, 'UTF8'))
    ) THEN
        RAISE EXCEPTION 'account deletion already requested' USING ERRCODE = '28000';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER prevent_deleted_account_recreation BEFORE INSERT OR UPDATE ON access_users
    FOR EACH ROW EXECUTE FUNCTION prevent_deleted_account_recreation();
