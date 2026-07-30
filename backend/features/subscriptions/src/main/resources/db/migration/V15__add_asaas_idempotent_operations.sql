CREATE TABLE asaas_idempotent_operations (
    idempotency_key text PRIMARY KEY,
    resource_id text,
    created_at timestamptz NOT NULL
);
