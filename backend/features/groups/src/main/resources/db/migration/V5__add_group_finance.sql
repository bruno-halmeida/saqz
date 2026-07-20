ALTER TABLE games ADD CONSTRAINT uq_games_group_id UNIQUE (group_id, id);

CREATE TABLE group_charges (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL,
    member_user_id uuid NOT NULL,
    kind varchar(16) NOT NULL,
    game_id uuid,
    billing_month date,
    amount_cents bigint NOT NULL,
    due_date date NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    created_by_user_id uuid NOT NULL,
    changed_by_user_id uuid NOT NULL,
    review_required boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_group_charges_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
    CONSTRAINT fk_group_charges_member FOREIGN KEY (member_user_id) REFERENCES access_users (id),
    CONSTRAINT fk_group_charges_creator FOREIGN KEY (created_by_user_id) REFERENCES access_users (id),
    CONSTRAINT fk_group_charges_changer FOREIGN KEY (changed_by_user_id) REFERENCES access_users (id),
    CONSTRAINT fk_group_charges_game FOREIGN KEY (group_id, game_id) REFERENCES games (group_id, id),
    CONSTRAINT ck_group_charges_kind CHECK (kind IN ('GAME', 'MONTHLY')),
    CONSTRAINT ck_group_charges_identity CHECK (
        (kind = 'GAME' AND game_id IS NOT NULL AND billing_month IS NULL)
        OR (kind = 'MONTHLY' AND game_id IS NULL AND billing_month IS NOT NULL AND billing_month = date_trunc('month', billing_month)::date)
    ),
    CONSTRAINT ck_group_charges_amount CHECK (amount_cents BETWEEN 1 AND 99999999),
    CONSTRAINT ck_group_charges_status CHECK (status IN ('PENDING', 'PAID', 'WAIVED', 'CANCELLED')),
    CONSTRAINT ck_group_charges_version CHECK (version >= 1)
);

CREATE UNIQUE INDEX uq_group_charges_game_member ON group_charges (group_id, game_id, member_user_id) WHERE kind = 'GAME';
CREATE UNIQUE INDEX uq_group_charges_month_member ON group_charges (group_id, billing_month, member_user_id) WHERE kind = 'MONTHLY';
CREATE INDEX ix_group_charges_organizer ON group_charges (group_id, status, due_date, id);
CREATE INDEX ix_group_charges_member ON group_charges (group_id, member_user_id, due_date, id);

CREATE TABLE group_charge_events (
    id uuid PRIMARY KEY,
    charge_id uuid NOT NULL,
    group_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    old_status varchar(16),
    new_status varchar(16) NOT NULL,
    note varchar(500),
    occurred_at timestamptz NOT NULL,
    CONSTRAINT fk_group_charge_events_charge FOREIGN KEY (charge_id) REFERENCES group_charges (id),
    CONSTRAINT fk_group_charge_events_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
    CONSTRAINT fk_group_charge_events_actor FOREIGN KEY (actor_user_id) REFERENCES access_users (id),
    CONSTRAINT ck_group_charge_events_old CHECK (old_status IS NULL OR old_status IN ('PENDING', 'PAID', 'WAIVED', 'CANCELLED')),
    CONSTRAINT ck_group_charge_events_new CHECK (new_status IN ('PENDING', 'PAID', 'WAIVED', 'CANCELLED')),
    CONSTRAINT ck_group_charge_events_change CHECK (old_status IS NULL OR old_status <> new_status),
    CONSTRAINT ck_group_charge_events_note CHECK (note IS NULL OR (note = btrim(note) AND char_length(note) BETWEEN 2 AND 500 AND note !~ '[[:cntrl:]]'))
);
CREATE INDEX ix_group_charge_events_history ON group_charge_events (group_id, charge_id, occurred_at, id);

CREATE TABLE group_expenses (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL,
    description varchar(160) NOT NULL,
    amount_cents bigint NOT NULL,
    expense_date date NOT NULL,
    category varchar(24) NOT NULL,
    custom_category varchar(40),
    notes varchar(500),
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id uuid NOT NULL,
    changed_by_user_id uuid NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_group_expenses_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
    CONSTRAINT fk_group_expenses_creator FOREIGN KEY (created_by_user_id) REFERENCES access_users (id),
    CONSTRAINT fk_group_expenses_changer FOREIGN KEY (changed_by_user_id) REFERENCES access_users (id),
    CONSTRAINT ck_group_expenses_description CHECK (description = btrim(description) AND char_length(description) BETWEEN 2 AND 160 AND description !~ '[[:cntrl:]]'),
    CONSTRAINT ck_group_expenses_amount CHECK (amount_cents BETWEEN 1 AND 99999999),
    CONSTRAINT ck_group_expenses_category CHECK (category IN ('VENUE', 'EQUIPMENT', 'REFEREE', 'OTHER')),
    CONSTRAINT ck_group_expenses_custom CHECK ((category = 'OTHER' AND custom_category IS NOT NULL AND custom_category = btrim(custom_category) AND char_length(custom_category) BETWEEN 2 AND 40 AND custom_category !~ '[[:cntrl:]]') OR (category <> 'OTHER' AND custom_category IS NULL)),
    CONSTRAINT ck_group_expenses_notes CHECK (notes IS NULL OR (notes = btrim(notes) AND char_length(notes) BETWEEN 2 AND 500 AND notes !~ '[[:cntrl:]]')),
    CONSTRAINT ck_group_expenses_status CHECK (status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT ck_group_expenses_version CHECK (version >= 1)
);
CREATE INDEX ix_group_expenses_organizer ON group_expenses (group_id, expense_date DESC, id);

CREATE TABLE group_expense_events (
    id uuid PRIMARY KEY,
    expense_id uuid NOT NULL,
    group_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    action varchar(16) NOT NULL,
    description varchar(160) NOT NULL,
    amount_cents bigint NOT NULL,
    expense_date date NOT NULL,
    category varchar(24) NOT NULL,
    custom_category varchar(40),
    notes varchar(500),
    status varchar(16) NOT NULL,
    version bigint NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT fk_group_expense_events_expense FOREIGN KEY (expense_id) REFERENCES group_expenses (id),
    CONSTRAINT fk_group_expense_events_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
    CONSTRAINT fk_group_expense_events_actor FOREIGN KEY (actor_user_id) REFERENCES access_users (id),
    CONSTRAINT ck_group_expense_events_action CHECK (action IN ('CREATED', 'EDITED', 'VOIDED'))
);
CREATE INDEX ix_group_expense_events_history ON group_expense_events (group_id, expense_id, occurred_at, id);

CREATE FUNCTION reject_finance_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'finance events are append only'; END $$;
CREATE TRIGGER group_charge_events_append_only BEFORE UPDATE OR DELETE ON group_charge_events FOR EACH ROW EXECUTE FUNCTION reject_finance_event_mutation();
CREATE TRIGGER group_expense_events_append_only BEFORE UPDATE OR DELETE ON group_expense_events FOR EACH ROW EXECUTE FUNCTION reject_finance_event_mutation();
