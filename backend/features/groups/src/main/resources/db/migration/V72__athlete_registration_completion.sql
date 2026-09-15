-- Preserve existing athlete registrations; new invite memberships complete the athlete form.
ALTER TABLE group_memberships ADD COLUMN athlete_registration_completed boolean NOT NULL DEFAULT true;
ALTER TABLE group_memberships ALTER COLUMN athlete_registration_completed SET DEFAULT false;
