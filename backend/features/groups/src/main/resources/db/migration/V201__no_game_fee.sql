-- "Sem diária" (DEB-146, JOGO-008): valor vazio + marca. Vazio sem a marca segue "não definido" (app antigo e grupo legado).
-- Aditivo: tudo nasce `false`, então nada muda para quem já existe.
ALTER TABLE access_groups ADD COLUMN default_no_game_fee boolean NOT NULL DEFAULT false;
ALTER TABLE access_groups ADD CONSTRAINT ck_access_groups_no_game_fee
    CHECK (NOT default_no_game_fee OR default_game_fee_cents IS NULL);

ALTER TABLE games ADD COLUMN no_game_fee boolean NOT NULL DEFAULT false;
ALTER TABLE games ADD CONSTRAINT ck_games_no_game_fee CHECK (NOT no_game_fee OR game_fee_cents IS NULL);

ALTER TABLE game_series_slots ADD COLUMN no_game_fee boolean NOT NULL DEFAULT false;
ALTER TABLE game_series_slots ADD CONSTRAINT ck_game_series_slots_no_game_fee CHECK (NOT no_game_fee OR game_fee_cents IS NULL);
