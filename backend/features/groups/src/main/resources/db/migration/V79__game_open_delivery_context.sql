-- O aviso de jogo liberado só existe como push/central: o WhatsApp segue restrito a CHARGE
-- (V75), e o grupo vinculado segue recebendo só NOTICE/REMINDER (V76). Como o REMINDER, é
-- reavaliado no envio: quem já respondeu não recebe, e prazo encerrado derruba a entrega.
CREATE OR REPLACE VIEW notification_delivery_context AS
SELECT n.sequence, n.recipient_id, m.group_id, m.channel, m.body, m.game_id, g.name AS group_name, u.phone,
    CASE m.channel WHEN 'NOTICE' THEN coalesce(p.push_notices, true)
        WHEN 'CHAT' THEN coalesce(p.push_messages, true)
        WHEN 'REMINDER' THEN coalesce(p.push_reminders, true)
        WHEN 'GAME_OPEN' THEN coalesce(p.push_reminders, true)
        WHEN 'CHARGE' THEN coalesce(p.push_charges, true) END AS push_enabled,
    CASE m.channel WHEN 'CHARGE' THEN coalesce(p.whatsapp_charges, false) ELSE false END AS whatsapp_enabled
FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
JOIN group_memberships gm ON gm.group_id = g.id AND gm.user_id = n.recipient_id AND gm.active
JOIN access_users u ON u.id = n.recipient_id
LEFT JOIN group_notification_preferences p ON p.user_id = n.recipient_id
LEFT JOIN group_charges c ON c.id = m.charge_id
LEFT JOIN games game ON game.id = m.game_id AND game.group_id = m.group_id
WHERE (m.channel <> 'CHARGE' OR c.status = 'PENDING')
  AND (m.channel NOT IN ('REMINDER', 'GAME_OPEN') OR (game.status = 'PUBLISHED' AND game.confirmation_deadline > now()
       AND game.starts_at > now() AND NOT EXISTS (SELECT 1 FROM game_attendance a
           WHERE a.game_id = m.game_id AND a.member_user_id = n.recipient_id)));

CREATE OR REPLACE FUNCTION notification_in_app_preference() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    SELECT CASE m.channel WHEN 'NOTICE' THEN coalesce(p.notices, true)
        WHEN 'CHAT' THEN coalesce(p.messages, true)
        WHEN 'REMINDER' THEN coalesce(p.reminders, true)
        WHEN 'GAME_OPEN' THEN coalesce(p.reminders, true) ELSE true END INTO NEW.in_app
    FROM group_messages m LEFT JOIN group_notification_preferences p ON p.user_id = NEW.recipient_id
    WHERE m.id = NEW.message_id;
    RETURN NEW;
END;
$$;
