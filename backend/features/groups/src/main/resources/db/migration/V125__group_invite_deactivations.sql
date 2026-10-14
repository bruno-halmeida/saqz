-- Lembra que um admin desativou o convite do grupo, para a leitura não criar outro sozinha.
-- Só "Gerar código" (rotate) volta a criar convite e apaga esta marca.
CREATE TABLE group_invite_deactivations (
    group_id uuid PRIMARY KEY,
    deactivated_at timestamptz NOT NULL,
    CONSTRAINT fk_group_invite_deactivations_group FOREIGN KEY (group_id) REFERENCES access_groups (id) ON DELETE CASCADE
);
