ALTER TABLE group_memberships
    ADD COLUMN auto_confirm_enabled boolean NOT NULL DEFAULT false;
