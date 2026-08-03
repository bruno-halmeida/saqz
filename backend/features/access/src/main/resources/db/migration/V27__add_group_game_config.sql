ALTER TABLE access_groups
    ADD COLUMN mensalista_priority boolean NOT NULL DEFAULT true,
    ADD COLUMN promotion_mode text NOT NULL DEFAULT 'FIFO',
    ADD COLUMN auto_confirm_enabled boolean NOT NULL DEFAULT false;

ALTER TABLE access_groups
    ADD CONSTRAINT ck_access_groups_promotion_mode CHECK (promotion_mode IN ('FIFO', 'MANUAL'));