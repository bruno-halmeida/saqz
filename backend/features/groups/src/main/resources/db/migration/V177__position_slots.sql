-- DEB-102: vaga por posição (sistema de jogo 5-1, 6-0). A configuração do grupo guarda as vagas por posição; cada jogo
-- recebe uma cópia quando nasce (gatilho, para cobrir todos os pontos que fazem INSERT em games). Jogo sem linhas em
-- game_position_slots é legado: total de vagas e uma espera só, como antes.
ALTER TABLE access_groups
    ADD COLUMN game_system varchar(16),
    ADD CONSTRAINT ck_access_groups_game_system CHECK (game_system IS NULL OR game_system IN ('FIVE_ONE', 'SIX_ZERO'));

CREATE TABLE group_position_slots (
    group_id uuid NOT NULL REFERENCES access_groups (id) ON DELETE CASCADE,
    position public.athlete_position NOT NULL,
    slots integer NOT NULL,
    PRIMARY KEY (group_id, position),
    CONSTRAINT ck_group_position_slots_slots CHECK (slots >= 0)
);

CREATE TABLE game_position_slots (
    game_id uuid NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    position public.athlete_position NOT NULL,
    slots integer NOT NULL,
    PRIMARY KEY (game_id, position),
    CONSTRAINT ck_game_position_slots_slots CHECK (slots >= 0)
);

ALTER TABLE game_attendance
    ADD COLUMN position public.athlete_position,
    ADD COLUMN forced boolean NOT NULL DEFAULT false;

CREATE INDEX ix_game_attendance_position ON game_attendance (game_id, position, status);

-- O total do jogo é a soma das posições: em grupo com posições, o jogo nasce com essa capacidade.
CREATE FUNCTION games_apply_position_capacity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE total integer;
BEGIN
    SELECT sum(slots) INTO total FROM group_position_slots WHERE group_id = NEW.group_id;
    IF total IS NOT NULL AND total > 0 THEN
        NEW.capacity := total;
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION games_copy_position_slots() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO game_position_slots (game_id, position, slots)
    SELECT NEW.id, position, slots FROM group_position_slots WHERE group_id = NEW.group_id;
    RETURN NULL;
END $$;

CREATE TRIGGER games_position_capacity BEFORE INSERT ON games
    FOR EACH ROW EXECUTE FUNCTION games_apply_position_capacity();
CREATE TRIGGER games_position_slots_copy AFTER INSERT ON games
    FOR EACH ROW EXECUTE FUNCTION games_copy_position_slots();
