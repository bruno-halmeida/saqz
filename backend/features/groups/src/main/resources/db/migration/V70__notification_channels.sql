ALTER TABLE group_notification_preferences
    ADD COLUMN push_notices boolean NOT NULL DEFAULT true,
    ADD COLUMN push_messages boolean NOT NULL DEFAULT true,
    ADD COLUMN push_reminders boolean NOT NULL DEFAULT true,
    ADD COLUMN push_charges boolean NOT NULL DEFAULT true,
    ADD COLUMN whatsapp_notices boolean NOT NULL DEFAULT false,
    ADD COLUMN whatsapp_reminders boolean NOT NULL DEFAULT false,
    ADD COLUMN whatsapp_charges boolean NOT NULL DEFAULT false;
UPDATE group_notification_preferences SET
    push_notices = notices, push_messages = messages, push_reminders = reminders, push_charges = reminders;
ALTER TABLE group_notifications ADD COLUMN in_app boolean NOT NULL DEFAULT true;
CREATE TABLE notification_whatsapp_queue (
    notification_id bigint PRIMARY KEY REFERENCES group_notifications(sequence),
    phone varchar(20) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    status varchar(12) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'FAILED', 'CANCELLED')),
    completed_at timestamptz
);
CREATE INDEX ix_whatsapp_pending ON notification_whatsapp_queue(next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_push_pending ON notification_push_queue(next_attempt_at) WHERE completed_at IS NULL;

-- Eligibility is shared by workers and checked again at delivery time.
CREATE VIEW notification_delivery_context AS
SELECT n.sequence, n.recipient_id, m.group_id, m.channel, m.body, m.game_id, g.name AS group_name, u.phone,
    CASE m.channel WHEN 'NOTICE' THEN coalesce(p.push_notices, true)
        WHEN 'CHAT' THEN coalesce(p.push_messages, true)
        WHEN 'REMINDER' THEN coalesce(p.push_reminders, true)
        WHEN 'CHARGE' THEN coalesce(p.push_charges, true) END AS push_enabled,
    CASE m.channel WHEN 'NOTICE' THEN coalesce(p.whatsapp_notices, false)
        WHEN 'REMINDER' THEN coalesce(p.whatsapp_reminders, false)
        WHEN 'CHARGE' THEN coalesce(p.whatsapp_charges, false) ELSE false END AS whatsapp_enabled
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

CREATE FUNCTION notification_in_app_preference() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    SELECT CASE m.channel WHEN 'NOTICE' THEN coalesce(p.notices, true)
        WHEN 'CHAT' THEN coalesce(p.messages, true)
        WHEN 'REMINDER' THEN coalesce(p.reminders, true) ELSE true END INTO NEW.in_app
    FROM group_messages m LEFT JOIN group_notification_preferences p ON p.user_id = NEW.recipient_id
    WHERE m.id = NEW.message_id;
    RETURN NEW;
END;
$$;
CREATE TRIGGER notification_in_app BEFORE INSERT ON group_notifications
    FOR EACH ROW EXECUTE FUNCTION notification_in_app_preference();

CREATE FUNCTION enqueue_notification_channels() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO notification_push_queue(notification_id)
        SELECT sequence FROM notification_delivery_context WHERE sequence = NEW.sequence AND push_enabled;
    INSERT INTO notification_whatsapp_queue(notification_id, phone)
        SELECT sequence, phone FROM notification_delivery_context
        WHERE sequence = NEW.sequence AND whatsapp_enabled AND phone IS NOT NULL;
    RETURN NEW;
END;
$$;
CREATE TRIGGER notification_channels AFTER INSERT ON group_notifications
    FOR EACH ROW EXECUTE FUNCTION enqueue_notification_channels();
