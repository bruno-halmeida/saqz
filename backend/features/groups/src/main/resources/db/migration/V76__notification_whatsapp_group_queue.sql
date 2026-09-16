CREATE TABLE notification_whatsapp_group_queue (
    message_id uuid PRIMARY KEY REFERENCES group_messages(id),
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    status varchar(12) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','ACCEPTED','FAILED','CANCELLED')),
    completed_at timestamptz
);
CREATE INDEX ix_whatsapp_group_pending
    ON notification_whatsapp_group_queue(next_attempt_at) WHERE status = 'PENDING';

CREATE FUNCTION enqueue_notification_whatsapp_group() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO notification_whatsapp_group_queue(message_id)
    SELECT NEW.id FROM group_whatsapp_bindings b
    JOIN access_groups g ON g.id = b.group_id AND g.deleted_at IS NULL
    WHERE b.group_id = NEW.group_id AND b.enabled AND b.broken_at IS NULL
      AND NEW.channel IN ('NOTICE','REMINDER')
    ON CONFLICT (message_id) DO NOTHING;
    RETURN NEW;
END;
$$;
CREATE TRIGGER notification_whatsapp_group AFTER INSERT ON group_messages
    FOR EACH ROW EXECUTE FUNCTION enqueue_notification_whatsapp_group();
