CREATE TABLE group_photos (
    group_id uuid PRIMARY KEY,
    photo_bytes bytea NOT NULL,
    media_type varchar(32) NOT NULL,
    byte_size bigint NOT NULL,
    width integer NOT NULL,
    height integer NOT NULL,
    sha256_digest bytea NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    updated_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_group_photos_group FOREIGN KEY (group_id) REFERENCES access_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_photos_actor FOREIGN KEY (updated_by) REFERENCES access_users (id),
    CONSTRAINT ck_group_photos_media_type CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_group_photos_byte_size CHECK (byte_size BETWEEN 1 AND 5242880),
    CONSTRAINT ck_group_photos_dimensions CHECK (width BETWEEN 1 AND 4096 AND height BETWEEN 1 AND 4096),
    CONSTRAINT ck_group_photos_digest CHECK (octet_length(sha256_digest) = 32),
    CONSTRAINT ck_group_photos_version CHECK (version >= 1),
    CONSTRAINT ck_group_photos_bytes_match CHECK (octet_length(photo_bytes) = byte_size)
);

