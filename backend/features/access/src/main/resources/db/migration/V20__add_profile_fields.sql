ALTER TABLE access_users
    ADD COLUMN nickname varchar(40),
    ADD COLUMN city varchar(80),
    ADD COLUMN phone_visibility varchar(16) NOT NULL DEFAULT 'ADMINS';

ALTER TABLE access_users
    ADD CONSTRAINT ck_access_users_nickname CHECK (
        nickname IS NULL OR (
            nickname = btrim(nickname)
            AND char_length(nickname) BETWEEN 2 AND 40
            AND nickname !~ '[[:cntrl:]]'
        )
    ),
    ADD CONSTRAINT ck_access_users_phone_visibility CHECK (
        phone_visibility IN ('EVERYONE', 'ADMINS', 'NOBODY')
    );
