-- Aviso de jogo cancelado nasce no canal NOTICE (reaproveita preferências, push, central e a fila do grupo de
-- WhatsApp) e vem ligado ao jogo, que antes só os canais de jogo podiam ter. Parte da regra da V98 e só
-- acrescenta a exceção do NOTICE; sem jogo, o NOTICE continua como era.
ALTER TABLE group_messages DROP CONSTRAINT group_messages_check;
ALTER TABLE group_messages ADD CONSTRAINT group_messages_check
    CHECK (channel = 'NOTICE'
        OR (channel IN ('REMINDER', 'GAME_OPEN', 'ATTENDANCE_WINDOW', 'WAITLIST_DEADLINE', 'FIRST_CONFIRMATION')) = (game_id IS NOT NULL));
