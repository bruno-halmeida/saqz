CREATE TABLE trial_offer_settings (
    id boolean PRIMARY KEY DEFAULT true CHECK (id),
    mode varchar(16) NOT NULL CHECK (mode IN ('ON', 'OFF', 'COUPON_ONLY')),
    updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO trial_offer_settings (mode) VALUES ('ON');

CREATE TABLE trial_coupons (
    id uuid PRIMARY KEY,
    code varchar(32) NOT NULL UNIQUE CHECK (code ~ '^[A-Z0-9]{1,32}$'),
    campaign varchar(120),
    trial_days integer NOT NULL CHECK (trial_days BETWEEN 1 AND 365),
    valid_until timestamptz,
    max_uses integer CHECK (max_uses > 0),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL
);
CREATE TABLE trial_coupon_selections (
    owner_user_id uuid PRIMARY KEY REFERENCES access_users(id),
    coupon_id uuid NOT NULL REFERENCES trial_coupons(id),
    selected_at timestamptz NOT NULL
);
ALTER TABLE organizer_trials
    ADD COLUMN coupon_id uuid REFERENCES trial_coupons(id),
    ADD COLUMN coupon_code varchar(32),
    ADD COLUMN campaign varchar(120);
CREATE INDEX organizer_trials_coupon ON organizer_trials(coupon_id) WHERE coupon_id IS NOT NULL;

ALTER TABLE organizer_trials DROP CONSTRAINT organizer_trial_duration;
ALTER TABLE organizer_trials ADD CONSTRAINT organizer_trial_duration
    CHECK (ends_at > started_at AND ends_at <= started_at + interval '8760 hours');
