-- Diária retroativa de quem jogou sem cobrança (DEB-147, FIN-016).
-- "Não cobrar": marca interna por jogo e pessoa (quem decidiu e quando), sem cobrança; Cobrar ou Já pagou apaga a marca.
CREATE TABLE game_uncharged_skips (
    game_id uuid NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    member_user_id uuid NOT NULL,
    guest_seq integer NOT NULL DEFAULT 0,
    group_id uuid NOT NULL REFERENCES access_groups(id) ON DELETE CASCADE,
    decided_by_user_id uuid NOT NULL,
    decided_at timestamptz NOT NULL,
    PRIMARY KEY (game_id, member_user_id, guest_seq)
);

-- A confirmação em lote é idempotente por `request_id`: o mesmo pedido devolve o resultado gravado, sem segundo efeito.
CREATE TABLE game_uncharged_reviews (
    group_id uuid NOT NULL REFERENCES access_groups(id) ON DELETE CASCADE,
    request_id uuid NOT NULL,
    game_id uuid NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    actor_user_id uuid NOT NULL,
    charged integer NOT NULL,
    paid integer NOT NULL,
    skipped integer NOT NULL,
    ignored integer NOT NULL,
    charge_ids uuid[] NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (group_id, request_id)
);
