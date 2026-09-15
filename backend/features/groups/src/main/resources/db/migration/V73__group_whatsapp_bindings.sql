CREATE TABLE group_whatsapp_bindings (
    group_id uuid PRIMARY KEY REFERENCES access_groups(id),
    whatsapp_jid varchar(64) NOT NULL UNIQUE,
    invite_code varchar(64) NOT NULL,
    group_name varchar(200) NOT NULL,
    instance_jid varchar(64) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    broken_at timestamptz,
    created_by uuid NOT NULL REFERENCES access_users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);