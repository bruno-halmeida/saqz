-- Lista no grupo do WhatsApp por horário (9h, 12h30, 18h; o de sempre) ou a cada mudança da lista (ON_CHANGE). Só o grupo
-- do WhatsApp muda: push, central e DM seguem as regras atuais. O default deixa os grupos existentes como estão.
ALTER TABLE access_groups
    ADD COLUMN attendance_updates_mode varchar(12) NOT NULL DEFAULT 'SCHEDULED',
    ADD CONSTRAINT ck_access_groups_attendance_updates_mode CHECK (attendance_updates_mode IN ('SCHEDULED', 'ON_CHANGE'));

-- Origem do lembrete automático. NULL = "Avisar" manual ou lembrete legado, que vai ao grupo nos dois modos.
ALTER TABLE group_messages
    ADD COLUMN reminder_origin varchar(12),
    ADD CONSTRAINT ck_group_messages_reminder_origin CHECK (reminder_origin IS NULL OR reminder_origin IN ('SCHEDULED', 'ON_CHANGE'));

-- Assinatura da última lista publicada no modo ON_CHANGE, por jogo. O freio (15 min e 12 por dia) é do grupo e sai das
-- próprias mensagens ON_CHANGE em group_messages.
CREATE TABLE group_attendance_digest (
    game_id uuid PRIMARY KEY REFERENCES games (id) ON DELETE CASCADE,
    roster_digest text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_group_messages_on_change ON group_messages (group_id, created_at) WHERE reminder_origin = 'ON_CHANGE';

-- O resto do gatilho é o da V142.
CREATE OR REPLACE FUNCTION enqueue_notification_whatsapp_group() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO notification_whatsapp_group_queue(message_id)
    SELECT NEW.id FROM group_whatsapp_bindings b
    JOIN access_groups g ON g.id = b.group_id AND g.deleted_at IS NULL
    WHERE b.group_id = NEW.group_id AND b.enabled AND b.broken_at IS NULL
      AND (NEW.channel = 'NOTICE'
           OR (NEW.channel = 'REMINDER' AND (NEW.reminder_origin IS NULL OR NEW.reminder_origin = g.attendance_updates_mode))
           OR NEW.event_kind = 'GAME_SCHEDULE_CHANGED')
    ON CONFLICT (message_id) DO NOTHING;
    RETURN NEW;
END;
$$;
