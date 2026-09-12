-- New invitations have no deadline. Preserve legacy deadlines, including expired links.
ALTER TABLE group_invites ALTER COLUMN expires_at DROP NOT NULL;

-- Non-secret identity for clients to invalidate cached URLs after a rotation.
ALTER TABLE group_invites ADD COLUMN revision uuid NOT NULL DEFAULT gen_random_uuid();
