ALTER TABLE access_users
    ADD COLUMN platform_admin boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN access_users.platform_admin IS
    'Admin de plataforma (adm-web): lista explícita, concedida manualmente via SQL.';
