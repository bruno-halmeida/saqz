-- Contatos da equipe do Saqz com uma pessoa, registrados no adm (DEB-162). Só a equipe vê; nunca aparece no app.
-- A linha mais recente decide a situação: DO_NOT_CONTACT bloqueia o contato no adm até alguém registrar outro resultado,
-- o que só acontece se a própria pessoa voltar a falar com o Saqz.
-- As linhas sobre a pessoa saem com a exclusão da conta (removeDeletedAccountPersonalData).
CREATE TABLE admin_user_contacts (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES access_users (id) ON DELETE CASCADE,
    result varchar(24) NOT NULL,
    note varchar(1000),
    created_by uuid NOT NULL REFERENCES access_users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_admin_user_contacts_result CHECK (
        result IN ('NO_ANSWER', 'WILL_SUBSCRIBE', 'TOO_EXPENSIVE', 'DID_NOT_USE', 'OTHER', 'DO_NOT_CONTACT')
    ),
    CONSTRAINT ck_admin_user_contacts_note CHECK (note IS NULL OR (note = btrim(note) AND char_length(note) BETWEEN 1 AND 1000))
);

CREATE INDEX admin_user_contacts_latest ON admin_user_contacts (user_id, created_at DESC);
