-- DEB-040: a confirmação automática do mensalista foi removida. Saem os gatilhos e as funções de aviso e a
-- função do grupo é desligada onde estava ligada. As colunas `auto_confirm_enabled` e o canal AUTO_CONFIRMATION
-- ficam: o aviso direto de presença (PRES-013) usa o canal e os apps das lojas o conhecem.
DROP TRIGGER IF EXISTS group_auto_confirmation_on ON access_groups;
DROP TRIGGER IF EXISTS member_auto_confirmation ON group_memberships;
DROP FUNCTION IF EXISTS notify_group_auto_confirmation_on();
DROP FUNCTION IF EXISTS notify_member_auto_confirmation();
DROP FUNCTION IF EXISTS notify_auto_confirmation(uuid, uuid);

UPDATE access_groups SET auto_confirm_enabled = false WHERE auto_confirm_enabled;
