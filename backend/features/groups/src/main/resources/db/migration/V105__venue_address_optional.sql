-- O endereço da quadra passa a ser opcional: ausente vira '' (a coluna segue NOT NULL).
ALTER TABLE group_venues DROP CONSTRAINT IF EXISTS ck_group_venues_address;
ALTER TABLE group_venues ADD CONSTRAINT ck_group_venues_address CHECK (
    address = btrim(address)
    AND (address = '' OR char_length(address) BETWEEN 5 AND 300)
    AND address !~ '[[:cntrl:]]'
);

ALTER TABLE games DROP CONSTRAINT IF EXISTS ck_games_venue_address;
ALTER TABLE games ADD CONSTRAINT ck_games_venue_address CHECK (
    venue_address = btrim(venue_address)
    AND (venue_address = '' OR char_length(venue_address) BETWEEN 5 AND 300)
    AND venue_address !~ '[[:cntrl:]]'
);

ALTER TABLE game_series_slots DROP CONSTRAINT IF EXISTS ck_game_series_slots_venue_address;
ALTER TABLE game_series_slots ADD CONSTRAINT ck_game_series_slots_venue_address CHECK (
    venue_address = btrim(venue_address)
    AND (venue_address = '' OR char_length(venue_address) BETWEEN 5 AND 300)
    AND venue_address !~ '[[:cntrl:]]'
);
