ALTER TABLE access_groups
    ADD COLUMN default_duration_minutes integer CHECK (default_duration_minutes BETWEEN 1 AND 1440),
    ADD COLUMN schedule_paused boolean NOT NULL DEFAULT false;
