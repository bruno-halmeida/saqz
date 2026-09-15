-- WhatsApp DM is reserved for CHARGE only. NOTICE and REMINDER no longer have an
-- individual WhatsApp channel (they move to the linked group, when present); their
-- per-user preferences stay persisted (whatsapp_notices/whatsapp_reminders) but inert.
CREATE OR REPLACE VIEW notification_delivery_context AS
SELECT n.sequence, n.recipient_id, m.group_id, m.channel, m.body, m.game_id, g.name AS group_name, u.phone,
    CASE m.channel WHEN 'NOTICE' THEN coalesce(p.push_notices, true)
        WHEN 'CHAT' THEN coalesce(p.push_messages, true)
        WHEN 'REMINDER' THEN coalesce(p.push_reminders, true)
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
  AND (m.channel <> 'REMINDER' OR (game.status = 'PUBLISHED' AND game.confirmation_deadline > now()
       AND game.starts_at > now() AND NOT EXISTS (SELECT 1 FROM game_attendance a
           WHERE a.game_id = m.game_id AND a.member_user_id = n.recipient_id)));