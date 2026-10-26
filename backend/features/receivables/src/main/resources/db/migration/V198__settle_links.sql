-- Link da hora do acerto (FIN-013): https://links.saqz.app/acerto/{code}, um código por (grupo, jogo) ou (grupo, mês).
-- O app resolve com GET /api/receivables/settle-links/{code} e abre o pagamento do atleta logado. Também guarda que a
-- hora do acerto daquele jogo/mês já saiu (uma vez só).
CREATE TABLE settle_links (
    code varchar(32) PRIMARY KEY,
    group_id uuid NOT NULL,
    game_id uuid,
    billing_month date,
    created_at timestamptz NOT NULL,
    announced_at timestamptz,
    CHECK ((game_id IS NULL) <> (billing_month IS NULL))
);
CREATE UNIQUE INDEX settle_links_game ON settle_links(group_id, game_id) WHERE game_id IS NOT NULL;
CREATE UNIQUE INDEX settle_links_month ON settle_links(group_id, billing_month) WHERE billing_month IS NOT NULL;
