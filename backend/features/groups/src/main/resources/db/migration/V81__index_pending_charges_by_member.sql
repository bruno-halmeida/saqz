-- A Home busca as cobranças pendentes de UMA pessoa em todos os grupos dela. Os índices de
-- group_charges começam por group_id, então essa busca varria a tabela inteira a cada abertura.
CREATE INDEX ix_group_charges_member_pending ON group_charges (member_user_id) WHERE status = 'PENDING';
