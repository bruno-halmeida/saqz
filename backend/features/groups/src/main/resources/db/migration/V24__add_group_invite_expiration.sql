ALTER TABLE group_invites ADD COLUMN expires_at timestamptz;

UPDATE group_invites
SET expires_at = created_at + interval '7 days'
WHERE expires_at IS NULL;

ALTER TABLE group_invites
    ALTER COLUMN expires_at SET NOT NULL;
