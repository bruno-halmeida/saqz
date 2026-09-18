-- O primeiro aviso de jogo liberado alcança o grupo inteiro: o mensalista entra no jogo já
-- confirmado pelo auto-confirm e, pela regra antiga ("quem já respondeu não recebe"), nunca
-- ficava sabendo que o jogo abriu. Do segundo aviso em diante o canal volta a ser o toque em
-- quem ainda não respondeu, então ninguém leva o mesmo push todo dia depois de confirmar.
-- Prazo encerrado, jogo despublicado ou jogo já iniciado continuam derrubando a entrega.
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
  AND (m.channel NOT IN ('REMINDER', 'GAME_OPEN') OR (game.status = 'PUBLISHED'
       AND game.confirmation_deadline > now() AND game.starts_at > now()
       AND (NOT EXISTS (SELECT 1 FROM game_attendance a
                        WHERE a.game_id = m.game_id AND a.member_user_id = n.recipient_id
                          AND a.guest_seq = 0)
            OR (m.channel = 'GAME_OPEN' AND NOT EXISTS (
                SELECT 1 FROM group_messages earlier
                WHERE earlier.game_id = m.game_id AND earlier.channel = 'GAME_OPEN'
                  AND earlier.sequence < m.sequence)))));

-- Sustenta o "este é o primeiro aviso deste jogo", avaliado a cada entrega e a cada publicação.
CREATE INDEX ix_group_messages_game_open ON group_messages(game_id, sequence) WHERE channel = 'GAME_OPEN';
