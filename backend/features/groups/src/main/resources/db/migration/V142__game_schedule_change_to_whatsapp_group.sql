-- DEB-071 (NOT-007): a mudança de dia, hora ou local de jogo publicado também vai ao grupo do WhatsApp. Ela nasce no
-- canal AUTO_CONFIRMATION, que também carrega aprovação de entrada, vaga confirmada, grupo excluído, promoção da
-- espera e promoção a administrador: nada disso vai ao grupo. Só a marca `event_kind` da V141 deixa entrar na fila,
-- sem canal novo. O resto do gatilho é o da V76.
CREATE OR REPLACE FUNCTION enqueue_notification_whatsapp_group() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO notification_whatsapp_group_queue(message_id)
    SELECT NEW.id FROM group_whatsapp_bindings b
    JOIN access_groups g ON g.id = b.group_id AND g.deleted_at IS NULL
    WHERE b.group_id = NEW.group_id AND b.enabled AND b.broken_at IS NULL
      AND (NEW.channel IN ('NOTICE','REMINDER') OR NEW.event_kind = 'GAME_SCHEDULE_CHANGED')
    ON CONFLICT (message_id) DO NOTHING;
    RETURN NEW;
END;
$$;
