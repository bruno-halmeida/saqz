-- Enrollment is pending until the first group and its trial commit together.
CREATE TABLE trial_enrollments (
    owner_user_id uuid PRIMARY KEY REFERENCES access_users(id),
    enrolled_at timestamptz NOT NULL
);
