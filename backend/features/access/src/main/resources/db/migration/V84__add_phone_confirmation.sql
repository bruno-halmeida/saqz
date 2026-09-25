-- Conta confirmada = e-mail confirmado no Firebase OU telefone confirmado aqui. Guardar o
-- número confirmado (e não um booleano) desconfirma sozinho quando o telefone muda.
ALTER TABLE access_users ADD COLUMN verified_phone varchar(20);

CREATE TABLE access_phone_confirmations (
    token_digest bytea PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES access_users(id),
    phone varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT access_phone_confirmations_digest_size CHECK (octet_length(token_digest) = 32)
);

CREATE INDEX idx_access_phone_confirmations_user ON access_phone_confirmations (user_id);
