-- Athlete registration filled in while the entry request is still pending; copied to the
-- membership when the organizer approves. Same types and limits as group_memberships.
ALTER TABLE group_entry_requests
    ADD COLUMN athlete_registration_completed boolean NOT NULL DEFAULT false,
    ADD COLUMN nickname varchar(40),
    ADD COLUMN position public.athlete_position,
    ADD COLUMN secondary_position public.athlete_position,
    ADD COLUMN level public.athlete_level,
    ADD COLUMN preferred_side public.athlete_preferred_side,
    ADD COLUMN height_cm smallint,
    ADD CONSTRAINT ck_group_entry_requests_secondary_position
        CHECK (secondary_position IS NULL OR secondary_position IS DISTINCT FROM position),
    ADD CONSTRAINT ck_group_entry_requests_height_cm CHECK (
        height_cm IS NULL OR height_cm BETWEEN 100 AND 250
    ),
    ADD CONSTRAINT ck_group_entry_requests_nickname CHECK (
        nickname IS NULL OR (
            nickname = btrim(nickname)
            AND char_length(nickname) BETWEEN 2 AND 40
            AND nickname !~ '[[:cntrl:]]'
        )
    );
