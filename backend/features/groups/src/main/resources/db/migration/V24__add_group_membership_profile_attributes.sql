ALTER TABLE group_memberships
    ADD COLUMN level varchar(16),
    ADD COLUMN secondary_position varchar(16),
    ADD COLUMN preferred_side varchar(16),
    ADD COLUMN height_cm smallint,
    ADD COLUMN nickname varchar(40),
    ADD COLUMN monthly_fee_cents bigint,
    ADD COLUMN monthly_due_day smallint,
    ADD CONSTRAINT ck_group_memberships_level CHECK (
        level IS NULL OR level IN ('INICIANTE', 'INTERMEDIARIO', 'AVANCADO')
    ),
    ADD CONSTRAINT ck_group_memberships_secondary_position CHECK (
        secondary_position IS NULL OR (
            secondary_position IN ('LIBERO', 'PONTA', 'CENTRAL', 'OPOSTO', 'LEVANTADOR')
            AND secondary_position IS DISTINCT FROM position
        )
    ),
    ADD CONSTRAINT ck_group_memberships_preferred_side CHECK (
        preferred_side IS NULL OR preferred_side IN ('DIREITA', 'ESQUERDA', 'TANTO_FAZ')
    ),
    ADD CONSTRAINT ck_group_memberships_height_cm CHECK (
        height_cm IS NULL OR height_cm BETWEEN 100 AND 250
    ),
    ADD CONSTRAINT ck_group_memberships_nickname CHECK (
        nickname IS NULL OR (
            nickname = btrim(nickname)
            AND char_length(nickname) BETWEEN 2 AND 40
            AND nickname !~ '[[:cntrl:]]'
        )
    ),
    ADD CONSTRAINT ck_group_memberships_monthly_fee_cents CHECK (
        monthly_fee_cents IS NULL OR monthly_fee_cents > 0
    ),
    ADD CONSTRAINT ck_group_memberships_monthly_due_day CHECK (
        monthly_due_day IS NULL OR monthly_due_day BETWEEN 1 AND 28
    );
