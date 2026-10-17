-- Instante da última TROCA explícita do código do convite (DEB-042). Os links dos lembretes anteriores a ele deixam de
-- valer como convite. A criação preguiçosa do primeiro convite não conta como troca e deixa a coluna nula.
ALTER TABLE group_invites ADD COLUMN rotated_at timestamptz;
