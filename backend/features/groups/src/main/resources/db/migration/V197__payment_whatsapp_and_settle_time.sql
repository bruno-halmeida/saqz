-- Recebimento pelo app v1, F3/F4 (DEB-140, FIN-012/FIN-013). Mensagem PAYMENT pode levar um WhatsApp particular (texto,
-- botão de URL e uma segunda mensagem só com o copia e cola), pela chave de WhatsApp de cobranças, com o telefone
-- confirmado como a cobrança. settle_game_id/settle_month: destino do toque no push ("Hora do acerto", "Cobrar"), sem
-- usar game_id (que nos apps antigos põe os botões de presença). SETTLE_TIME marca a hora do acerto no grupo do WhatsApp.
ALTER TABLE group_messages
    ADD COLUMN whatsapp_body varchar(2000),
    ADD COLUMN whatsapp_button_label varchar(40),
    ADD COLUMN whatsapp_button_url varchar(500),
    ADD COLUMN whatsapp_follow_up varchar(2000),
    ADD COLUMN settle_game_id uuid,
    ADD COLUMN settle_month date;
ALTER TABLE group_messages DROP CONSTRAINT group_messages_event_kind_check;
ALTER TABLE group_messages ADD CONSTRAINT group_messages_event_kind_check
    CHECK (event_kind IN ('GAME_CANCELLED', 'GAME_SCHEDULE_CHANGED', 'MEMBER_JOINED_BY_LINK', 'SETTLE_TIME'));

-- Gatilho do grupo do WhatsApp: o da V187 mais a hora do acerto.
CREATE OR REPLACE FUNCTION enqueue_notification_whatsapp_group() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO notification_whatsapp_group_queue(message_id)
    SELECT NEW.id FROM group_whatsapp_bindings b
    JOIN access_groups g ON g.id = b.group_id AND g.deleted_at IS NULL
    WHERE b.group_id = NEW.group_id AND b.enabled AND b.broken_at IS NULL
      AND (NEW.channel = 'NOTICE'
           OR (NEW.channel = 'REMINDER' AND (NEW.reminder_origin IS NULL OR NEW.reminder_origin = g.attendance_updates_mode))
           OR NEW.event_kind IN ('GAME_SCHEDULE_CHANGED', 'SETTLE_TIME'))
    ON CONFLICT (message_id) DO NOTHING;
    RETURN NEW;
END;
$$;

-- View: a da V195 com o WhatsApp particular do canal PAYMENT e o destino do toque.
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
            WHEN 'CHARGE' THEN coalesce(p.push_charges, true)
            WHEN 'PAYMENT' THEN coalesce(p.push_charges, true) END END AS push_enabled,
    CASE WHEN m.event_kind = 'MEMBER_JOINED_BY_LINK' THEN coalesce(p.push_notices, true)
        WHEN m.channel = 'CHARGE' THEN coalesce(p.whatsapp_charges, true)
        WHEN m.channel = 'PAYMENT' AND m.whatsapp_body IS NOT NULL THEN coalesce(p.whatsapp_charges, true)
        ELSE false END AS whatsapp_enabled,
    CASE m.channel WHEN 'ATTENDANCE_WINDOW'
        THEN coalesce(game.confirmation_deadline, game.starts_at) END AS window_ends_at,
    m.push_body,
    m.push_title,
    m.settle_game_id,
    m.settle_month
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
