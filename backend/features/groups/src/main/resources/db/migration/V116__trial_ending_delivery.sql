-- TRIAL_ENDING: aviso de sistema ao dono sobre o fim do teste do Organizador, na central e no push pela preferência
-- de avisos. Parte da V107 (mantém ENTRY_REQUEST, REMINDER até o início do jogo e WhatsApp de cobrança ligado).
-- Sem WhatsApp pela fila: o WhatsApp particular sai direto do job, uma vez por marco.
CREATE OR REPLACE VIEW notification_delivery_context AS
SELECT n.sequence, n.recipient_id, m.group_id, m.channel, m.body, m.game_id, g.name AS group_name, u.phone,
    CASE m.channel WHEN 'NOTICE' THEN coalesce(p.push_notices, true)
        WHEN 'CHAT' THEN coalesce(p.push_messages, true)
        WHEN 'REMINDER' THEN coalesce(p.push_reminders, true)
        WHEN 'GAME_OPEN' THEN coalesce(p.push_reminders, true)
        WHEN 'ATTENDANCE_WINDOW' THEN coalesce(p.push_reminders, true)
        WHEN 'WAITLIST_DEADLINE' THEN coalesce(p.push_reminders, true)
        WHEN 'FIRST_MEMBER' THEN coalesce(p.push_notices, true)
        WHEN 'FIRST_CONFIRMATION' THEN coalesce(p.push_notices, true)
        WHEN 'ENTRY_REQUEST' THEN coalesce(p.push_notices, true)
        WHEN 'TRIAL_ENDING' THEN coalesce(p.push_notices, true)
        WHEN 'CHARGE' THEN coalesce(p.push_charges, true) END AS push_enabled,
    CASE m.channel WHEN 'CHARGE' THEN coalesce(p.whatsapp_charges, true) ELSE false END AS whatsapp_enabled,
    CASE m.channel WHEN 'ATTENDANCE_WINDOW'
        THEN least(game.starts_at - interval '22 hours', game.confirmation_deadline) END AS window_ends_at
FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
JOIN group_memberships gm ON gm.group_id = g.id AND gm.user_id = n.recipient_id AND gm.active
JOIN access_users u ON u.id = n.recipient_id
LEFT JOIN group_notification_preferences p ON p.user_id = n.recipient_id
LEFT JOIN group_charges c ON c.id = m.charge_id
LEFT JOIN games game ON game.id = m.game_id AND game.group_id = m.group_id
WHERE (m.channel <> 'CHARGE' OR c.status = 'PENDING')
  AND (m.channel NOT IN ('WAITLIST_DEADLINE', 'FIRST_CONFIRMATION') OR (game.status = 'PUBLISHED' AND game.starts_at > now()))
  AND (m.channel NOT IN ('REMINDER', 'GAME_OPEN') OR (game.status = 'PUBLISHED'
       AND (m.channel = 'REMINDER' OR game.confirmation_deadline > now()) AND game.starts_at > now()
       AND (NOT EXISTS (SELECT 1 FROM game_attendance a
                        WHERE a.game_id = m.game_id AND a.member_user_id = n.recipient_id
                          AND a.guest_seq = 0)
            OR (m.channel = 'GAME_OPEN' AND NOT EXISTS (
                SELECT 1 FROM group_messages earlier
                WHERE earlier.game_id = m.game_id AND earlier.channel = 'GAME_OPEN'
                  AND earlier.sequence < m.sequence)))))
  AND (m.channel <> 'ATTENDANCE_WINDOW' OR (game.status = 'PUBLISHED'
       AND now() < least(game.starts_at - interval '22 hours', game.confirmation_deadline)
       AND NOT EXISTS (SELECT 1 FROM game_attendance a
                       WHERE a.game_id = m.game_id AND a.member_user_id = n.recipient_id
                         AND a.guest_seq = 0)))
  AND (m.channel NOT IN ('REMINDER', 'GAME_OPEN') OR NOT EXISTS (
       SELECT 1 FROM group_notifications wn JOIN group_messages wm ON wm.id = wn.message_id
       WHERE wm.channel = 'ATTENDANCE_WINDOW' AND wm.game_id = m.game_id
         AND wn.recipient_id = n.recipient_id
         AND now() < least(game.starts_at - interval '22 hours', game.confirmation_deadline)));

CREATE OR REPLACE FUNCTION notification_in_app_preference() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    SELECT CASE m.channel WHEN 'NOTICE' THEN coalesce(p.notices, true)
        WHEN 'CHAT' THEN coalesce(p.messages, true)
        WHEN 'REMINDER' THEN coalesce(p.reminders, true)
        WHEN 'GAME_OPEN' THEN coalesce(p.reminders, true)
        WHEN 'ATTENDANCE_WINDOW' THEN coalesce(p.reminders, true)
        WHEN 'WAITLIST_DEADLINE' THEN coalesce(p.reminders, true)
        WHEN 'FIRST_MEMBER' THEN coalesce(p.notices, true)
        WHEN 'FIRST_CONFIRMATION' THEN coalesce(p.notices, true)
        WHEN 'ENTRY_REQUEST' THEN coalesce(p.notices, true)
        WHEN 'TRIAL_ENDING' THEN coalesce(p.notices, true) ELSE true END INTO NEW.in_app
    FROM group_messages m LEFT JOIN group_notification_preferences p ON p.user_id = NEW.recipient_id
    WHERE m.id = NEW.message_id;
    RETURN NEW;
END;
$$;
