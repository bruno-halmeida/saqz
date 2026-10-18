-- DEB-053: quem deu por resolvida a revisão de uma cobrança de jogo cancelado, e quando.
-- Tabela à parte: o histórico da cobrança (group_charge_events) exige troca de status e o enum não ganha valor novo.
CREATE TABLE group_charge_review_resolutions (
    id uuid PRIMARY KEY,
    charge_id uuid NOT NULL,
    group_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    resolved_at timestamptz NOT NULL,
    CONSTRAINT fk_group_charge_review_resolutions_charge FOREIGN KEY (charge_id) REFERENCES group_charges (id),
    CONSTRAINT fk_group_charge_review_resolutions_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
    CONSTRAINT fk_group_charge_review_resolutions_actor FOREIGN KEY (actor_user_id) REFERENCES access_users (id)
);
CREATE INDEX ix_group_charge_review_resolutions_charge ON group_charge_review_resolutions (group_id, charge_id, resolved_at);
CREATE TRIGGER group_charge_review_resolutions_append_only BEFORE UPDATE OR DELETE ON group_charge_review_resolutions FOR EACH ROW EXECUTE FUNCTION reject_finance_event_mutation();
