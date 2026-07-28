-- Um código por e-mail: pedir um novo sobrescreve a linha, e com ela o código e o
-- token anteriores. O e-mail é a chave primária, então "invalida o anterior" é o
-- comportamento do UPSERT, não uma limpeza separada que pode falhar.
CREATE TABLE password_reset_codes (
    email varchar(320) PRIMARY KEY,
    code_digest bytea NOT NULL,
    attempts integer NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    token_digest bytea,
    token_expires_at timestamptz,
    CONSTRAINT ck_password_reset_codes_email CHECK (
        email = lower(btrim(email))
        AND email ~ '^[^@[:space:]]+@[^@[:space:].]+(\.[^@[:space:].]+)+$'
    ),
    CONSTRAINT ck_password_reset_codes_code_digest CHECK (octet_length(code_digest) = 32),
    CONSTRAINT ck_password_reset_codes_attempts CHECK (attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_password_reset_codes_expires_at CHECK (expires_at > created_at),
    CONSTRAINT ck_password_reset_codes_token_digest CHECK (
        token_digest IS NULL OR octet_length(token_digest) = 32
    ),
    CONSTRAINT ck_password_reset_codes_token_pairing CHECK (
        (token_digest IS NULL) = (token_expires_at IS NULL)
        AND (token_expires_at IS NULL OR token_expires_at > created_at)
    ),
    CONSTRAINT uq_password_reset_codes_token_digest UNIQUE (token_digest)
);

-- Janela deslizante por IP. O teto mora no caso de uso porque a contagem precisa
-- passar do teto para o pedido ser recusado; aqui só se garante que ela nunca é
-- negativa.
CREATE TABLE password_reset_ip_limits (
    ip varchar(45) PRIMARY KEY,
    window_started_at timestamptz NOT NULL,
    request_count integer NOT NULL,
    CONSTRAINT ck_password_reset_ip_limits_count CHECK (request_count >= 0)
);
