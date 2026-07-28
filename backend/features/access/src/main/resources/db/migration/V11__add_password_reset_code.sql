-- Um código por e-mail: pedir um novo sobrescreve a linha, e com ela o código e o
-- token anteriores. O e-mail é a chave primária, então "invalida o anterior" é o
-- comportamento do UPSERT, não uma limpeza separada que pode falhar.
--
-- `code_digest` e `token_digest` são mutuamente exclusivos: trocar o código pelo
-- token apaga o código no mesmo UPDATE, então um código já usado não volta a ser
-- verificável nem acumula tentativa que apagaria o token já emitido.
CREATE TABLE password_reset_codes (
    email varchar(320) PRIMARY KEY,
    code_digest bytea,
    attempts integer NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    token_digest bytea,
    token_expires_at timestamptz,
    CONSTRAINT ck_password_reset_codes_email CHECK (
        email = lower(btrim(email))
        AND email ~ '^[^@[:space:]]+@[^@[:space:].]+(\.[^@[:space:].]+)+$'
    ),
    CONSTRAINT ck_password_reset_codes_code_digest CHECK (
        code_digest IS NULL OR octet_length(code_digest) = 32
    ),
    -- O incremento é atômico e guardado pelo próprio teto no WHERE, então o contador
    -- nunca passa de 5 e a invariante do teto vive também no banco.
    CONSTRAINT ck_password_reset_codes_attempts CHECK (attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_password_reset_codes_expires_at CHECK (expires_at > created_at),
    CONSTRAINT ck_password_reset_codes_token_digest CHECK (
        token_digest IS NULL OR octet_length(token_digest) = 32
    ),
    CONSTRAINT ck_password_reset_codes_token_pairing CHECK (
        (token_digest IS NULL) = (token_expires_at IS NULL)
        AND (token_expires_at IS NULL OR token_expires_at > created_at)
    ),
    CONSTRAINT ck_password_reset_codes_single_secret CHECK (
        (code_digest IS NULL) <> (token_digest IS NULL)
    ),
    CONSTRAINT uq_password_reset_codes_token_digest UNIQUE (token_digest)
);

-- Janela deslizante por balde. O balde é `passo:ip` — pedido e verificação contam
-- separado para o tráfego de um não consumir a cota do outro. O teto mora no caso de
-- uso porque a contagem precisa passar dele para o pedido ser recusado; aqui só se
-- garante que ela nunca é negativa.
CREATE TABLE password_reset_rate_limits (
    bucket varchar(64) PRIMARY KEY,
    window_started_at timestamptz NOT NULL,
    request_count integer NOT NULL,
    CONSTRAINT ck_password_reset_rate_limits_count CHECK (request_count >= 0)
);
