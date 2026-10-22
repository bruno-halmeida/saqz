-- DEB-129 / PRES-016: "Faltou" = confirmou e não apareceu. Marca à parte na presença do jogo encerrado;
-- o status segue CONFIRMED (promoção e cobrança não mudam). Só o gestor marca e vê.
ALTER TABLE game_attendance
    ADD COLUMN no_show_marked_at timestamptz,
    ADD COLUMN no_show_marked_by uuid,
    ADD CONSTRAINT ck_game_attendance_no_show_pair
        CHECK ((no_show_marked_at IS NULL) = (no_show_marked_by IS NULL)),
    ADD CONSTRAINT ck_game_attendance_no_show_confirmed
        CHECK (no_show_marked_at IS NULL OR status = 'CONFIRMED');

-- "Jogos realizados" (JOGO-006) deixa de contar quem foi marcado como falta, também no perfil da própria pessoa.
CREATE OR REPLACE VIEW member_played_games AS
SELECT attendance.group_id, attendance.member_user_id AS user_id, attendance.game_id
FROM game_attendance attendance
JOIN games ON games.id = attendance.game_id AND games.group_id = attendance.group_id
WHERE games.status = 'COMPLETED'
  AND attendance.status = 'CONFIRMED'
  AND attendance.no_show_marked_at IS NULL
  AND attendance.guest_seq = 0;

-- DEB-128 / JOGO-011: "Jogos anteriores" do gestor, do mais recente para trás, por cursor (starts_at, id).
CREATE INDEX ix_games_past_by_group ON games (group_id, starts_at DESC, id DESC)
    WHERE status IN ('COMPLETED', 'CANCELLED');
