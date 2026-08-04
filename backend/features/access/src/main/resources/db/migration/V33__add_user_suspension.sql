ALTER TABLE access_users
    ADD COLUMN suspended_at timestamptz DEFAULT NULL;

COMMENT ON COLUMN access_users.suspended_at IS
    'Suspensão de plataforma (adm-web): bloqueia o bootstrap de sessão. Null = ativo.';
