-- Última versão do app vista por usuário e plataforma, vinda dos cabeçalhos X-Saqz-Platform, X-Saqz-App-Version e
-- X-Saqz-App-Build que o app manda em toda chamada. Serve para achar quem ainda usa uma versão com problema e, no
-- futuro, para responder no formato certo ao app antigo. Cliente que não manda os cabeçalhos (o app antigo, anterior
-- a eles) não tem linha aqui.
--
-- Uma linha por (usuário, plataforma): versão e build são sobrescritos quando mudam. first_seen_at é a primeira vez
-- que o usuário apareceu naquela plataforma e nunca muda. last_seen_at tem resolução de ~6 h: o backend só grava de
-- novo quando a versão ou o build mudam, ou quando a última vez tem mais de 6 h (RecordClientApp), nunca uma vez
-- por requisição.
-- app_version fica nula quando o app não mandou uma versão no padrão X.Y.Z (o web app, por exemplo).
CREATE TABLE access_user_app_versions (
    user_id uuid NOT NULL,
    platform varchar(7) NOT NULL,
    app_version varchar(32),
    app_build integer,
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, platform),
    CONSTRAINT fk_access_user_app_versions_user FOREIGN KEY (user_id) REFERENCES access_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_access_user_app_versions_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    CONSTRAINT ck_access_user_app_versions_version CHECK (
        app_version IS NULL OR (app_version ~ '^[0-9]+\.[0-9]+\.[0-9]+$' AND char_length(app_version) <= 32)
    ),
    CONSTRAINT ck_access_user_app_versions_build CHECK (app_build IS NULL OR app_build >= 0)
);
