-- Flags de feature (DEB-119, FLG-001 a FLG-004 e FLG-007). Toda flag nasce no código do backend
-- (FeatureFlagCatalog); aqui fica só a regra que o adm edita. As linhas das flags são criadas na subida
-- da aplicação, com o valor inicial e uma linha no histórico.

-- Regra de cada flag, em JSON (FeatureRule): interruptores, listas, estado, público, porcentagem, valores
-- e variantes. full_rollout_since marca quando a flag passou a valer 100% nas duas plataformas (alertas
-- de 30/60/90 dias); zera quando deixa de valer.
CREATE TABLE feature_flags (
    flag_key varchar(64) PRIMARY KEY CHECK (flag_key ~ '^[a-z][a-z0-9_]{1,63}$'),
    version bigint NOT NULL CHECK (version >= 1),
    rule jsonb NOT NULL,
    full_rollout_since timestamptz,
    updated_at timestamptz NOT NULL
);

-- Grupos com nome ("revisores", "testadores"), usados nas listas de qualquer flag.
CREATE TABLE feature_user_groups (
    name varchar(40) PRIMARY KEY CHECK (name ~ '^[a-z0-9][a-z0-9_-]{1,39}$'),
    version bigint NOT NULL CHECK (version >= 1),
    updated_at timestamptz NOT NULL
);
CREATE TABLE feature_user_group_members (
    group_name varchar(40) NOT NULL REFERENCES feature_user_groups(name) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES access_users(id) ON DELETE CASCADE,
    PRIMARY KEY (group_name, user_id)
);
CREATE INDEX feature_user_group_members_user ON feature_user_group_members(user_id);

-- Versão mínima e recomendada do app por plataforma (FLG-007). Nulo = sem versão.
CREATE TABLE feature_app_versions (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    version bigint NOT NULL CHECK (version >= 1),
    android_minimum varchar(32) CHECK (android_minimum ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    android_recommended varchar(32) CHECK (android_recommended ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    ios_minimum varchar(32) CHECK (ios_minimum ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    ios_recommended varchar(32) CHECK (ios_recommended ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    updated_at timestamptz NOT NULL
);
INSERT INTO feature_app_versions(singleton, version, updated_at) VALUES (true, 1, now());

-- Histórico imutável de flags, grupos e versões. actor_user_id nulo = o próprio sistema (valor inicial).
-- before_state nulo = criação. version é a versão da entidade depois da mudança.
CREATE TABLE feature_history (
    id uuid PRIMARY KEY,
    entity_type varchar(16) NOT NULL CHECK (entity_type IN ('FLAG', 'GROUP', 'APP_VERSIONS')),
    entity_key varchar(64) NOT NULL,
    version bigint NOT NULL,
    actor_user_id uuid,
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) BETWEEN 3 AND 500),
    created_at timestamptz NOT NULL,
    before_state jsonb,
    after_state jsonb NOT NULL,
    UNIQUE (entity_type, entity_key, version)
);
CREATE INDEX feature_history_page ON feature_history(entity_type, entity_key, created_at DESC, id);
CREATE INDEX feature_history_all ON feature_history(created_at DESC, id);

CREATE FUNCTION reject_feature_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'feature_history is immutable';
END;
$$;
CREATE TRIGGER feature_history_immutable BEFORE UPDATE OR DELETE ON feature_history
    FOR EACH ROW EXECUTE FUNCTION reject_feature_history_mutation();
CREATE TRIGGER feature_history_no_truncate BEFORE TRUNCATE ON feature_history
    FOR EACH STATEMENT EXECUTE FUNCTION reject_feature_history_mutation();

-- "revisores" nasce com as contas de revisão das lojas (seed-revisao-apple.sh: revisao.*@saqz.app).
INSERT INTO feature_user_groups(name, version, updated_at) VALUES ('revisores', 1, now());
INSERT INTO feature_user_group_members(group_name, user_id)
SELECT 'revisores', id FROM access_users WHERE email LIKE 'revisao.%@saqz.app' AND deleted_at IS NULL;
INSERT INTO feature_history(id, entity_type, entity_key, version, reason, created_at, after_state)
SELECT gen_random_uuid(), 'GROUP', 'revisores', 1, 'Grupo inicial com as contas de revisão das lojas', now(),
       jsonb_build_object('name', 'revisores', 'members',
           COALESCE((SELECT jsonb_agg(user_id ORDER BY user_id) FROM feature_user_group_members WHERE group_name = 'revisores'), '[]'::jsonb));
INSERT INTO feature_history(id, entity_type, entity_key, version, reason, created_at, after_state)
VALUES (gen_random_uuid(), 'APP_VERSIONS', 'app', 1, 'Valor inicial: sem versão mínima nem recomendada', now(),
        '{"android":{"minimum":null,"recommended":null},"ios":{"minimum":null,"recommended":null}}'::jsonb);
