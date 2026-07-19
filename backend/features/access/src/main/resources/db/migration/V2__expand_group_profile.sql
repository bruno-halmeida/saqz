ALTER TABLE access_groups
    ADD COLUMN privacy varchar(16) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN currency char(3) NOT NULL DEFAULT 'BRL',
    ADD COLUMN profile_status varchar(16) NOT NULL DEFAULT 'INCOMPLETE',
    ADD COLUMN modality varchar(32),
    ADD COLUMN composition varchar(16),
    ADD COLUMN description varchar(500),
    ADD COLUMN city varchar(80),
    ADD COLUMN level varchar(20),
    ADD COLUMN custom_level varchar(40),
    ADD COLUMN play_style varchar(16),
    ADD COLUMN custom_play_style varchar(40),
    ADD COLUMN default_capacity integer,
    ADD COLUMN default_confirmation_lead_minutes integer,
    ADD COLUMN default_game_fee_cents bigint,
    ADD COLUMN monthly_fee_cents bigint,
    ADD COLUMN monthly_due_day integer;

ALTER TABLE access_groups
    ADD CONSTRAINT ck_access_groups_privacy CHECK (privacy = 'PRIVATE'),
    ADD CONSTRAINT ck_access_groups_currency CHECK (currency = 'BRL'),
    ADD CONSTRAINT ck_access_groups_profile_status CHECK (
        profile_status IN ('INCOMPLETE', 'COMPLETE')
        AND (
            profile_status = 'INCOMPLETE'
            OR (modality IS NOT NULL AND composition IS NOT NULL)
        )
    ),
    ADD CONSTRAINT ck_access_groups_modality CHECK (
        modality IS NULL OR modality IN ('COURT_VOLLEYBALL', 'BEACH_VOLLEYBALL', 'FOOTVOLLEY')
    ),
    ADD CONSTRAINT ck_access_groups_composition CHECK (
        composition IS NULL OR composition IN ('WOMEN', 'MEN', 'MIXED')
    ),
    ADD CONSTRAINT ck_access_groups_description CHECK (
        description IS NULL OR (
            description = btrim(description)
            AND char_length(description) BETWEEN 2 AND 500
            AND description !~ '[[:cntrl:]]'
        )
    ),
    ADD CONSTRAINT ck_access_groups_city CHECK (
        city IS NULL OR (
            city = btrim(city)
            AND char_length(city) BETWEEN 2 AND 80
            AND city !~ '[[:cntrl:]]'
        )
    ),
    ADD CONSTRAINT ck_access_groups_level CHECK (
        level IS NULL OR level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'MIXED_LEVELS', 'CUSTOM')
    ),
    ADD CONSTRAINT ck_access_groups_custom_level CHECK (
        (
            level = 'CUSTOM'
            AND custom_level IS NOT NULL
            AND custom_level = btrim(custom_level)
            AND char_length(custom_level) BETWEEN 2 AND 40
            AND custom_level !~ '[[:cntrl:]]'
        )
        OR (
            level IS DISTINCT FROM 'CUSTOM'
            AND custom_level IS NULL
        )
    ),
    ADD CONSTRAINT ck_access_groups_play_style CHECK (
        play_style IS NULL OR play_style IN ('SIX_ZERO', 'FOUR_TWO', 'FIVE_ONE', 'CUSTOM')
    ),
    ADD CONSTRAINT ck_access_groups_court_play_style CHECK (
        modality = 'COURT_VOLLEYBALL'
        OR (play_style IS NULL AND custom_play_style IS NULL)
    ),
    ADD CONSTRAINT ck_access_groups_custom_play_style CHECK (
        (
            play_style = 'CUSTOM'
            AND custom_play_style IS NOT NULL
            AND custom_play_style = btrim(custom_play_style)
            AND char_length(custom_play_style) BETWEEN 2 AND 40
            AND custom_play_style !~ '[[:cntrl:]]'
        )
        OR (
            play_style IS DISTINCT FROM 'CUSTOM'
            AND custom_play_style IS NULL
        )
    ),
    ADD CONSTRAINT ck_access_groups_default_capacity CHECK (
        default_capacity IS NULL OR default_capacity BETWEEN 2 AND 100
    ),
    ADD CONSTRAINT ck_access_groups_default_confirmation_lead CHECK (
        default_confirmation_lead_minutes IS NULL
        OR default_confirmation_lead_minutes BETWEEN 0 AND 10080
    ),
    ADD CONSTRAINT ck_access_groups_default_game_fee CHECK (
        default_game_fee_cents IS NULL OR default_game_fee_cents BETWEEN 1 AND 99999999
    ),
    ADD CONSTRAINT ck_access_groups_monthly_fee CHECK (
        monthly_fee_cents IS NULL OR monthly_fee_cents BETWEEN 1 AND 99999999
    ),
    ADD CONSTRAINT ck_access_groups_monthly_due_day CHECK (
        (
            monthly_fee_cents IS NULL
            AND monthly_due_day IS NULL
        )
        OR (
            monthly_fee_cents IS NOT NULL
            AND monthly_due_day IS NOT NULL
            AND monthly_due_day BETWEEN 1 AND 28
        )
    );

CREATE TABLE group_venues (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL,
    name varchar(120) NOT NULL,
    address varchar(300) NOT NULL,
    court varchar(80),
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_group_venues_group FOREIGN KEY (group_id) REFERENCES access_groups (id) ON DELETE CASCADE,
    CONSTRAINT uq_group_venues_group_id UNIQUE (group_id, id),
    CONSTRAINT ck_group_venues_name CHECK (
        name = btrim(name)
        AND char_length(name) BETWEEN 2 AND 120
        AND name !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_group_venues_address CHECK (
        address = btrim(address)
        AND char_length(address) BETWEEN 5 AND 300
        AND address !~ '[[:cntrl:]]'
    ),
    CONSTRAINT ck_group_venues_court CHECK (
        court IS NULL OR (
            court = btrim(court)
            AND char_length(court) BETWEEN 1 AND 80
            AND court !~ '[[:cntrl:]]'
        )
    ),
    CONSTRAINT ck_group_venues_version CHECK (version >= 1)
);

ALTER TABLE access_groups
    ADD COLUMN default_venue_id uuid,
    ADD CONSTRAINT fk_access_groups_default_venue
        FOREIGN KEY (id, default_venue_id) REFERENCES group_venues (group_id, id);

CREATE TABLE group_regular_slots (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL,
    venue_id uuid,
    weekday smallint NOT NULL,
    start_time time NOT NULL,
    duration_minutes integer NOT NULL,
    position integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_group_regular_slots_group FOREIGN KEY (group_id) REFERENCES access_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_regular_slots_venue FOREIGN KEY (group_id, venue_id) REFERENCES group_venues (group_id, id),
    CONSTRAINT ck_group_regular_slots_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_group_regular_slots_duration CHECK (duration_minutes BETWEEN 15 AND 480),
    CONSTRAINT ck_group_regular_slots_position CHECK (position >= 0),
    CONSTRAINT ck_group_regular_slots_version CHECK (version >= 1)
);

CREATE INDEX ix_group_venues_group_id ON group_venues (group_id);
CREATE INDEX ix_group_regular_slots_group_id ON group_regular_slots (group_id, position, weekday, start_time);
