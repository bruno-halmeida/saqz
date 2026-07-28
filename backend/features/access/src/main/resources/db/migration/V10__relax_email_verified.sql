-- A verificação de e-mail deixou de ser recusa e virou sinal na sessão: a coluna
-- passa a guardar o claim real do Firebase, então ela não pode mais exigir true.
-- A coluna continua NOT NULL — ausência de claim é gravada como false.
ALTER TABLE access_users DROP CONSTRAINT ck_access_users_email_verified;
