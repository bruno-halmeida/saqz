-- SAI-007 / DEB-027: quem saiu ou foi removido só volta com aprovação do dono ou admin. A marca só vale para
-- saídas novas (as antigas ficam com o padrão falso e seguem entrando direto); a aprovação do pedido a limpa.
ALTER TABLE group_membership_removals
    ADD COLUMN requires_approval boolean NOT NULL DEFAULT false;
