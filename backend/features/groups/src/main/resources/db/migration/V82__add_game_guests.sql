-- Convidado de jogo (VUL-239): 0 = a resposta do próprio membro; 1..n = convidados dele.
ALTER TABLE game_attendance ADD COLUMN guest_seq smallint NOT NULL DEFAULT 0;
ALTER TABLE game_attendance ADD CONSTRAINT ck_game_attendance_guest_seq CHECK (guest_seq >= 0);
ALTER TABLE game_attendance DROP CONSTRAINT game_attendance_pkey;
ALTER TABLE game_attendance ADD PRIMARY KEY (game_id, member_user_id, guest_seq);

-- attendance_events é append-only por trigger de UPDATE/DELETE; ADD COLUMN com DEFAULT não dispara.
ALTER TABLE attendance_events ADD COLUMN guest_seq smallint NOT NULL DEFAULT 0;

ALTER TABLE group_charges ADD COLUMN guest_seq smallint NOT NULL DEFAULT 0;
ALTER TABLE group_charges ADD COLUMN guest_display_name varchar(80);
ALTER TABLE group_charges ADD CONSTRAINT ck_group_charges_guest
    CHECK ((guest_seq = 0 AND guest_display_name IS NULL) OR (guest_seq > 0 AND kind = 'GAME' AND guest_display_name IS NOT NULL));
DROP INDEX uq_group_charges_game_member;
CREATE UNIQUE INDEX uq_group_charges_game_member
    ON group_charges (group_id, game_id, member_user_id, guest_seq) WHERE kind = 'GAME';
