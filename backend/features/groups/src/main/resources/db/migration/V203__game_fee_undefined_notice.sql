-- Aviso aos grupos sem valor por jogo (DEB-146, JOGO-008): reaproveita o canal FIRST_MEMBER (aviso de sistema só de
-- dono e admins; canal novo quebraria os apps das lojas) com a marca `event_kind = 'GAME_FEE_UNDEFINED'`.
-- O índice do primeiro atleta passa a valer só sem marca, e o aviso novo tem o próprio: no máximo um por grupo.
ALTER TABLE group_messages DROP CONSTRAINT group_messages_event_kind_check;
ALTER TABLE group_messages ADD CONSTRAINT group_messages_event_kind_check
    CHECK (event_kind IN ('GAME_CANCELLED', 'GAME_SCHEDULE_CHANGED', 'MEMBER_JOINED_BY_LINK', 'SETTLE_TIME', 'GAME_FEE_UNDEFINED'));

DROP INDEX ux_group_messages_first_member;
CREATE UNIQUE INDEX ux_group_messages_first_member ON group_messages(group_id)
    WHERE channel = 'FIRST_MEMBER' AND event_kind IS NULL;
CREATE UNIQUE INDEX ux_group_messages_game_fee_undefined ON group_messages(group_id)
    WHERE event_kind = 'GAME_FEE_UNDEFINED';
