-- Confirmação automática do mensalista nasce DESLIGADA no grupo; quem decide ligar é o gestor. A preferência
-- do atleta continua nascendo ligada: desligada, o gestor ligaria a função e ninguém seria confirmado nem
-- avisado. Só o DEFAULT muda: grupos que já existem ficam como estão.
ALTER TABLE access_groups ALTER COLUMN auto_confirm_enabled SET DEFAULT false;

-- O gestor liga a confirmação automática do grupo: cada mensalista com a preferência ligada fica sabendo.
-- O dono do grupo assina o aviso. No máximo um por pessoa e grupo a cada 24 h, para o liga e desliga não
-- virar spam.
CREATE FUNCTION notify_auto_confirmation(p_group uuid, p_user uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    v_message uuid := gen_random_uuid();
    v_owner uuid;
    v_group_name varchar;
BEGIN
    SELECT g.owner_user_id, g.name INTO v_owner, v_group_name
    FROM access_groups g WHERE g.id = p_group AND g.deleted_at IS NULL;
    IF v_owner IS NULL THEN RETURN; END IF;
    IF EXISTS (
        SELECT 1 FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
        WHERE m.group_id = p_group AND m.channel = 'AUTO_CONFIRMATION' AND n.recipient_id = p_user
          AND m.created_at > now() - interval '24 hours'
    ) THEN RETURN; END IF;
    INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body)
    SELECT v_message, p_group, v_owner, u.display_name, 'AUTO_CONFIRMATION', gen_random_uuid(),
           'Você vai estar confirmado automaticamente nos jogos do ' || v_group_name
               || '. Se não puder ir, toque Não vou.'
    FROM access_users u WHERE u.id = v_owner;
    INSERT INTO group_notifications (recipient_id, message_id) VALUES (p_user, v_message);
    UPDATE group_messages SET recipient_count = (
        SELECT count(*) FROM group_notifications WHERE message_id = v_message AND in_app)
    WHERE id = v_message;
END;
$$;

CREATE FUNCTION notify_group_auto_confirmation_on() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_member uuid;
BEGIN
    FOR v_member IN
        SELECT m.user_id FROM group_memberships m
        WHERE m.group_id = NEW.id AND m.active AND m.membership_type = 'MENSALISTA' AND m.auto_confirm_enabled
    LOOP
        PERFORM notify_auto_confirmation(NEW.id, v_member);
    END LOOP;
    RETURN NULL;
END;
$$;
CREATE TRIGGER group_auto_confirmation_on AFTER UPDATE OF auto_confirm_enabled ON access_groups
    FOR EACH ROW WHEN (NEW.auto_confirm_enabled AND NOT OLD.auto_confirm_enabled)
    EXECUTE FUNCTION notify_group_auto_confirmation_on();

-- O atleta vira mensalista, com a preferência ligada, num grupo que já tem a confirmação automática ligada.
CREATE FUNCTION notify_member_auto_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM access_groups g WHERE g.id = NEW.group_id AND g.deleted_at IS NULL AND g.auto_confirm_enabled) THEN
        PERFORM notify_auto_confirmation(NEW.group_id, NEW.user_id);
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER member_auto_confirmation AFTER UPDATE OF membership_type ON group_memberships
    FOR EACH ROW WHEN (NEW.active AND NEW.membership_type = 'MENSALISTA' AND NEW.auto_confirm_enabled
                       AND OLD.membership_type IS DISTINCT FROM 'MENSALISTA')
    EXECUTE FUNCTION notify_member_auto_confirmation();
