-- Um aviso de fim de teste por marco (3, 1 e 0 dias para o fim): a linha é a reserva que torna o job idempotente.
CREATE TABLE organizer_trial_notices (
    owner_user_id uuid NOT NULL REFERENCES organizer_trials (owner_user_id),
    milestone smallint NOT NULL CHECK (milestone IN (0, 1, 3)),
    sent_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_user_id, milestone)
);
