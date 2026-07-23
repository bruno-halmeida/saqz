ALTER TABLE access_users ADD COLUMN phone varchar(20);

ALTER TABLE access_users ADD CONSTRAINT ck_access_users_phone CHECK (
    phone IS NULL OR phone ~ '^\+55[0-9]{10,11}$'
);
