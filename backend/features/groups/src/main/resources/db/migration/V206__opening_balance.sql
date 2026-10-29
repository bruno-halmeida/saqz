-- Caixa novo (DEB-148, FIN-006).
-- Patrocínio e Venda só existem na entrada; as categorias antigas seguem valendo nos dois sentidos (app antigo).
ALTER TABLE group_expenses ADD CONSTRAINT ck_group_expenses_income_category
    CHECK (category NOT IN ('SPONSORSHIP', 'SALE') OR direction = 'IN');

-- Saldo inicial: quanto o grupo já tinha antes do Saqz. Nulo = ninguém respondeu (a pergunta do caixa zerado);
-- 0 é o "Começar do zero". Versão própria para o If-Match, sem disputar a versão do perfil do grupo.
ALTER TABLE access_groups ADD COLUMN opening_balance_cents bigint
    CONSTRAINT ck_access_groups_opening_balance CHECK (opening_balance_cents IS NULL OR opening_balance_cents BETWEEN 0 AND 99999999);
ALTER TABLE access_groups ADD COLUMN opening_balance_version bigint NOT NULL DEFAULT 1;

-- Histórico de cada mudança: quem, quando, de quanto para quanto.
CREATE TABLE group_opening_balance_events (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL REFERENCES access_groups(id) ON DELETE CASCADE,
    actor_user_id uuid NOT NULL,
    old_amount_cents bigint,
    new_amount_cents bigint NOT NULL,
    occurred_at timestamptz NOT NULL
);
CREATE INDEX ix_group_opening_balance_events_group ON group_opening_balance_events(group_id, occurred_at);
