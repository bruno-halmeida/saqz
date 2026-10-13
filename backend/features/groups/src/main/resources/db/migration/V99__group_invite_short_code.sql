-- Código curto do convite (ex.: 44DF-8SA8), guardado em texto para qualquer admin poder mostrá-lo.
-- O token longo legado segue em token_digest e continua valendo até o grupo trocar o código.
ALTER TABLE group_invites ALTER COLUMN token_digest DROP NOT NULL;

ALTER TABLE group_invites ADD COLUMN short_code varchar(8);

ALTER TABLE group_invites
    ADD CONSTRAINT ck_group_invites_short_code CHECK (short_code ~ '^[2-9A-HJKMNP-Z]{8}$');

-- Mesma chave de busca do token longo (sha256 do texto), para preview e resgate usarem um digest só.
ALTER TABLE group_invites
    ADD COLUMN short_code_digest bytea GENERATED ALWAYS AS (sha256(short_code::text::bytea)) STORED;

CREATE UNIQUE INDEX uq_group_invites_short_code ON group_invites (short_code);
CREATE UNIQUE INDEX uq_group_invites_short_code_digest ON group_invites (short_code_digest);

ALTER TABLE group_invites
    ADD CONSTRAINT ck_group_invites_has_credential CHECK (token_digest IS NOT NULL OR short_code IS NOT NULL);
