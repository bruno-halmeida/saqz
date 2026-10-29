-- Vaga do avulso só com pagamento antecipado (DEB-150, PROM-010): opção do grupo, desligada de fábrica e só com
-- Escolha manual. O CHECK garante que Ordem de chegada nunca fica com a opção ligada.
ALTER TABLE access_groups ADD COLUMN advance_payment_required boolean NOT NULL DEFAULT false;
ALTER TABLE access_groups ADD CONSTRAINT ck_access_groups_advance_payment_manual
    CHECK (NOT advance_payment_required OR promotion_mode = 'MANUAL');
