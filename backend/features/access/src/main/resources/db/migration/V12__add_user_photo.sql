-- A foto de perfil aparece pequena em lista: o servidor recomprime o envio para
-- JPEG de no maximo 512x512 antes de gravar, entao o original de 12 MP do celular
-- nunca chega aqui. Nao existe coluna de tipo porque so existe um tipo depois da
-- recompressao; os limites de tamanho e dimensao ficam no CHECK e nao so no Kotlin.
--
-- O digest e o validador de cache da foto: contador por linha reiniciaria em 1 a
-- cada apagar-e-reenviar e faria bytes diferentes compartilharem ETag na mesma URL
-- autenticada, o que serve a foto de outra conta a partir do cache privado.
CREATE TABLE access_user_photos (
    user_id uuid PRIMARY KEY,
    photo_bytes bytea NOT NULL,
    byte_size bigint NOT NULL,
    width integer NOT NULL,
    height integer NOT NULL,
    sha256_digest bytea NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_access_user_photos_user FOREIGN KEY (user_id) REFERENCES access_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_access_user_photos_byte_size CHECK (byte_size BETWEEN 1 AND 524288),
    CONSTRAINT ck_access_user_photos_dimensions CHECK (width BETWEEN 1 AND 512 AND height BETWEEN 1 AND 512),
    CONSTRAINT ck_access_user_photos_digest CHECK (octet_length(sha256_digest) = 32),
    CONSTRAINT ck_access_user_photos_bytes_match CHECK (octet_length(photo_bytes) = byte_size)
);
