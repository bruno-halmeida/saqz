-- DEB-049 / JOGO-006: "jogos realizados" de uma pessoa = jogos encerrados (COMPLETED) em que ela
-- estava confirmada. Espera, "Não vou", jogo cancelado ou futuro não contam. Convidado
-- (guest_seq > 0) não é pessoa do grupo. Não olha group_memberships: quem saiu mantém o histórico.
-- Perfil, detalhe do atleta e cartão do grupo leem daqui; o cálculo mora só nesta view.
CREATE VIEW member_played_games AS
SELECT attendance.group_id, attendance.member_user_id AS user_id, attendance.game_id
FROM game_attendance attendance
JOIN games ON games.id = attendance.game_id AND games.group_id = attendance.group_id
WHERE games.status = 'COMPLETED'
  AND attendance.status = 'CONFIRMED'
  AND attendance.guest_seq = 0;
