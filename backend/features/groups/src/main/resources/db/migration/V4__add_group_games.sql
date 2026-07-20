CREATE TABLE game_series (
    id uuid PRIMARY KEY,
    lineage_id uuid NOT NULL,
    group_id uuid NOT NULL,
    previous_revision_id uuid,
    revision_number integer NOT NULL,
    zone_id varchar(80) NOT NULL,
    local_start_date date NOT NULL,
    local_end_date date,
    active_through_date date,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_game_series_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
    CONSTRAINT uq_game_series_group_revision UNIQUE (group_id, id),
    CONSTRAINT uq_game_series_group_lineage_revision UNIQUE (group_id, lineage_id, id),
    CONSTRAINT uq_game_series_lineage_number UNIQUE (lineage_id, revision_number),
    CONSTRAINT ck_game_series_revision CHECK (revision_number >= 1),
    CONSTRAINT ck_game_series_revision_predecessor CHECK (
        (revision_number = 1 AND previous_revision_id IS NULL)
        OR (revision_number > 1 AND previous_revision_id IS NOT NULL)
    ),
    CONSTRAINT ck_game_series_zone CHECK (
        zone_id = btrim(zone_id)
        AND char_length(zone_id) BETWEEN 1 AND 80
        AND zone_id !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_game_series_date_range CHECK (
        local_end_date IS NULL OR local_end_date >= local_start_date
    ),
    CONSTRAINT ck_game_series_active_boundary CHECK (
        active_through_date IS NULL OR (
            active_through_date >= local_start_date - 1
            AND (local_end_date IS NULL OR active_through_date <= local_end_date)
        )
    ),
    CONSTRAINT ck_game_series_version CHECK (version >= 1)
);

ALTER TABLE game_series
    ADD CONSTRAINT fk_game_series_previous_revision
    FOREIGN KEY (group_id, lineage_id, previous_revision_id)
    REFERENCES game_series (group_id, lineage_id, id);

CREATE TABLE game_series_slots (
    series_revision_id uuid NOT NULL,
    group_id uuid NOT NULL,
    slot_key uuid NOT NULL,
    title varchar(120) NOT NULL,
    weekday smallint NOT NULL,
    local_time time NOT NULL,
    duration_minutes integer NOT NULL,
    venue_id uuid,
    venue_name varchar(120) NOT NULL,
    venue_address varchar(300) NOT NULL,
    venue_court varchar(80),
    capacity integer NOT NULL,
    confirmation_lead_minutes integer NOT NULL,
    game_fee_cents bigint,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (series_revision_id, slot_key),
    CONSTRAINT fk_game_series_slots_revision
        FOREIGN KEY (group_id, series_revision_id) REFERENCES game_series (group_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_game_series_slots_venue
        FOREIGN KEY (group_id, venue_id) REFERENCES group_venues (group_id, id),
    CONSTRAINT ck_game_series_slots_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_game_series_slots_duration CHECK (duration_minutes BETWEEN 15 AND 480),
    CONSTRAINT ck_game_series_slots_title CHECK (
        title = btrim(title)
        AND char_length(title) BETWEEN 2 AND 120
        AND title !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_game_series_slots_venue_name CHECK (
        venue_name = btrim(venue_name)
        AND char_length(venue_name) BETWEEN 2 AND 120
        AND venue_name !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_game_series_slots_venue_address CHECK (
        venue_address = btrim(venue_address)
        AND char_length(venue_address) BETWEEN 5 AND 300
        AND venue_address !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_game_series_slots_venue_court CHECK (
        venue_court IS NULL OR (
            venue_court = btrim(venue_court)
            AND char_length(venue_court) BETWEEN 1 AND 80
            AND venue_court !~ '[[:cntrl:]]'
        )
    ),
    CONSTRAINT ck_game_series_slots_capacity CHECK (capacity BETWEEN 2 AND 100),
    CONSTRAINT ck_game_series_slots_confirmation_lead CHECK (
        confirmation_lead_minutes BETWEEN 0 AND 10080
    ),
    CONSTRAINT ck_game_series_slots_fee CHECK (
        game_fee_cents IS NULL OR game_fee_cents BETWEEN 1 AND 99999999
    )
);

CREATE TABLE games (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL,
    series_id uuid,
    series_revision_id uuid,
    slot_key uuid,
    title varchar(120) NOT NULL,
    local_date date NOT NULL,
    local_time time NOT NULL,
    zone_id varchar(80) NOT NULL,
    starts_at timestamptz NOT NULL,
    duration_minutes integer NOT NULL,
    confirmation_deadline timestamptz NOT NULL,
    venue_id uuid,
    venue_name varchar(120) NOT NULL,
    venue_address varchar(300) NOT NULL,
    venue_court varchar(80),
    capacity integer NOT NULL,
    game_fee_cents bigint,
    notes varchar(500),
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    detached_from_series boolean NOT NULL DEFAULT false,
    finance_review_required boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_games_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
    CONSTRAINT fk_games_venue FOREIGN KEY (group_id, venue_id) REFERENCES group_venues (group_id, id),
    CONSTRAINT fk_games_series_revision
        FOREIGN KEY (group_id, series_id, series_revision_id)
        REFERENCES game_series (group_id, lineage_id, id),
    CONSTRAINT fk_games_series_slot
        FOREIGN KEY (series_revision_id, slot_key)
        REFERENCES game_series_slots (series_revision_id, slot_key),
    CONSTRAINT ck_games_series_identity CHECK (
        (series_id IS NULL AND series_revision_id IS NULL AND slot_key IS NULL)
        OR (series_id IS NOT NULL AND series_revision_id IS NOT NULL AND slot_key IS NOT NULL)
    ),
    CONSTRAINT ck_games_detached_identity CHECK (NOT detached_from_series OR series_id IS NOT NULL),
    CONSTRAINT ck_games_title CHECK (
        title = btrim(title)
        AND char_length(title) BETWEEN 2 AND 120
        AND title !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_games_zone CHECK (
        zone_id = btrim(zone_id)
        AND char_length(zone_id) BETWEEN 1 AND 80
        AND zone_id !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_games_duration CHECK (duration_minutes BETWEEN 15 AND 480),
    CONSTRAINT ck_games_deadline CHECK (confirmation_deadline <= starts_at),
    CONSTRAINT ck_games_venue_name CHECK (
        venue_name = btrim(venue_name)
        AND char_length(venue_name) BETWEEN 2 AND 120
        AND venue_name !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_games_venue_address CHECK (
        venue_address = btrim(venue_address)
        AND char_length(venue_address) BETWEEN 5 AND 300
        AND venue_address !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_games_venue_court CHECK (
        venue_court IS NULL OR (
            venue_court = btrim(venue_court)
            AND char_length(venue_court) BETWEEN 1 AND 80
            AND venue_court !~ '[[:cntrl:]]'
        )
    ),
    CONSTRAINT ck_games_capacity CHECK (capacity BETWEEN 2 AND 100),
    CONSTRAINT ck_games_fee CHECK (game_fee_cents IS NULL OR game_fee_cents BETWEEN 1 AND 99999999),
    CONSTRAINT ck_games_notes CHECK (
        notes IS NULL OR (
            notes = btrim(notes)
            AND char_length(notes) BETWEEN 2 AND 500
            AND notes !~ '[[:cntrl:]]'
        )
    ),
    CONSTRAINT ck_games_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_games_version CHECK (version >= 1),
    CONSTRAINT uq_games_series_occurrence UNIQUE (series_id, local_date, slot_key)
);

CREATE INDEX ix_game_series_group_lineage ON game_series (group_id, lineage_id, revision_number);
CREATE INDEX ix_game_series_slots_group ON game_series_slots (group_id, series_revision_id, weekday, local_time);
CREATE INDEX ix_games_group_start ON games (group_id, starts_at, id);
CREATE INDEX ix_games_series_revision ON games (series_revision_id, local_date, slot_key);
