-- Dia (de São Paulo) em que o app já abriu a folha de contagem do teste: no máximo uma por dia, em qualquer aparelho.
CREATE TABLE organizer_trial_prompts (
    owner_user_id uuid PRIMARY KEY REFERENCES organizer_trials (owner_user_id),
    last_prompt_date date NOT NULL
);
