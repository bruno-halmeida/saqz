-- DEB-065 (NOT-002): jogo cancelado e mudança de dia, hora ou local chegam sempre por push, mesmo com "Avisos dos
-- grupos" desligado. A marca é `group_messages.event_kind`, o tipo do evento (nulo na maioria das mensagens): o
-- cancelamento (canal NOTICE, com jogo) e a mudança (canal AUTO_CONFIRMATION) mantêm o canal que os apps das lojas já
-- conhecem. A view lê a marca e ignora `push_*` para elas; a DEB-071 lê a mesma marca na fila do grupo de WhatsApp.
-- O resto da view é o da V131 (a mais nova), sem mudança.
ALTER TABLE group_messages ADD COLUMN event_kind varchar(24)
    CHECK (event_kind IN ('GAME_CANCELLED', 'GAME_SCHEDULE_CHANGED'));
-- Cancelamentos já gravados (NOTICE com jogo vinculado) ganham a marca: o push deles ainda pode estar na fila.
UPDATE group_messages SET event_kind = 'GAME_CANCELLED' WHERE channel = 'NOTICE' AND game_id IS NOT NULL;

CREATE OR REPLACE VIEW notification_delivery_context AS
SELECT n.sequence, n.recipient_id, m.group_id, m.channel, m.body, m.game_id, g.name AS group_name, u.phone,
    CASE WHEN m.event_kind IS NOT NULL THEN true ELSE
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
            WHEN 'AUTO_CONFIRMATION' THEN coalesce(p.push_notices, true)
            WHEN 'CHARGE' THEN coalesce(p.push_charges, true) END END AS push_enabled,
    CASE m.channel WHEN 'CHARGE' THEN coalesce(p.whatsapp_charges, true) ELSE false END AS whatsapp_enabled,
    CASE m.channel WHEN 'ATTENDANCE_WINDOW'
        THEN least(game.starts_at - interval '22 hours', game.confirmation_deadline) END AS window_ends_at
FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
JOIN access_groups g ON g.id = m.group_id AND (g.deleted_at IS NULL OR m.request_id = md5('group-deleted:' || m.group_id::text)::uuid)
JOIN group_memberships gm ON gm.group_id = g.id AND gm.user_id = n.recipient_id
    AND (gm.active OR m.request_id = md5('group-deleted:' || m.group_id::text)::uuid)
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
