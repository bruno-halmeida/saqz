-- Mensalistas entram confirmados por padrão: a feature do grupo e o opt-in do membro nascem true.
ALTER TABLE access_groups ALTER COLUMN auto_confirm_enabled SET DEFAULT true;
ALTER TABLE group_memberships ALTER COLUMN auto_confirm_enabled SET DEFAULT true;
UPDATE access_groups SET auto_confirm_enabled = true WHERE NOT auto_confirm_enabled;
UPDATE group_memberships SET auto_confirm_enabled = true WHERE membership_type = 'MENSALISTA' AND NOT auto_confirm_enabled;
