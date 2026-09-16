-- O aviso de jogo liberado também é uma mensagem de jogo: carrega game_id, como o REMINDER.
-- É o game_id que filtra os destinatários (só quem não respondeu) e que revalida o prazo na entrega.
ALTER TABLE group_messages DROP CONSTRAINT group_messages_check;
ALTER TABLE group_messages ADD CONSTRAINT group_messages_check
    CHECK ((channel IN ('REMINDER', 'GAME_OPEN')) = (game_id IS NOT NULL));
