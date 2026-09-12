CREATE TABLE organizer_trials (
    owner_user_id uuid PRIMARY KEY REFERENCES access_users (id),
    started_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    CONSTRAINT organizer_trial_duration CHECK (ends_at = started_at + interval '336 hours')
);
