-- DEB-106 (NOT-004): aviso a dono e admins quando alguém entra ou vira mensalista pelo link de mensalistas. Nasce no
-- canal ENTRY_REQUEST (só dono e admins, sem quem entrou) com a marca `event_kind = 'MEMBER_JOINED_BY_LINK'`, que só
-- nesta view muda três coisas: o push obedece "Avisos dos grupos" (push_notices) em vez de sempre sair, como os dois
-- eventos da V141; o WhatsApp particular segue a mesma chave; e o telefone é o cadastrado (`access_users.phone`), sem
-- exigir confirmação (exceção ao DEB-081 decidida pelo dono em 20/10). O pedido de entrada comum (ENTRY_REQUEST sem
-- marca) continua sem WhatsApp, e a cobrança segue só para número confirmado. O gatilho da fila de WhatsApp (V70) já
-- enfileira quando a view diz `whatsapp_enabled` com telefone; o do grupo do WhatsApp (V142) não aceita ENTRY_REQUEST.
-- O resto da view é o da V178 (a mais nova: janela de presença até o prazo, DEB-113), sem mudança.
ALTER TABLE group_messages DROP CONSTRAINT group_messages_event_kind_check;
ALTER TABLE group_messages ADD CONSTRAINT group_messages_event_kind_check
    CHECK (event_kind IN ('GAME_CANCELLED', 'GAME_SCHEDULE_CHANGED', 'MEMBER_JOINED_BY_LINK'));

CREATE OR REPLACE VIEW notification_delivery_context AS
SELECT n.sequence, n.recipient_id, m.group_id, m.channel, m.body, m.game_id, g.name AS group_name,
    CASE WHEN m.event_kind = 'MEMBER_JOINED_BY_LINK' THEN u.phone
        WHEN u.phone IS NOT NULL AND u.phone = u.verified_phone THEN u.phone END::varchar(20) AS phone,
    CASE WHEN m.event_kind IN ('GAME_CANCELLED', 'GAME_SCHEDULE_CHANGED') THEN true ELSE
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
    CASE WHEN m.event_kind = 'MEMBER_JOINED_BY_LINK' THEN coalesce(p.push_notices, true)
        WHEN m.channel = 'CHARGE' THEN coalesce(p.whatsapp_charges, true) ELSE false END AS whatsapp_enabled,
    CASE m.channel WHEN 'ATTENDANCE_WINDOW'
        THEN coalesce(game.confirmation_deadline, game.starts_at) END AS window_ends_at,
    m.push_body
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
       AND now() < coalesce(game.confirmation_deadline, game.starts_at)
       AND NOT EXISTS (SELECT 1 FROM game_attendance a
                       WHERE a.game_id = m.game_id AND a.member_user_id = n.recipient_id
                         AND a.guest_seq = 0)))
  AND (m.channel NOT IN ('REMINDER', 'GAME_OPEN') OR NOT EXISTS (
       SELECT 1 FROM group_notifications wn JOIN group_messages wm ON wm.id = wn.message_id
       WHERE wm.channel = 'ATTENDANCE_WINDOW' AND wm.game_id = m.game_id
         AND wn.recipient_id = n.recipient_id
         AND now() < coalesce(game.confirmation_deadline, game.starts_at)))
  AND NOT EXISTS (SELECT 1 FROM content_reports r
                  WHERE r.reporter_id = n.recipient_id AND r.target_type = 'MESSAGE' AND r.target_id = m.id);
