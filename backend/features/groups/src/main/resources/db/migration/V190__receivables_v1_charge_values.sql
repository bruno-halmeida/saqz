-- Recebimento pelo app v1 (DEB-140, FIN-010 e FIN-014). Só colunas novas e opcionais: os apps das lojas não leem nenhuma.
-- payable_cents: o que o atleta paga, fixado quando a cobrança nasce num grupo com recebimentos ativos (nulo = amount_cents).
-- fee_policy e fee_schedule_id: a escolha do gestor e a tabela de tarifas usadas no cálculo, para a cobrança guardar a
-- origem do valor. settled_by = 'APP' marca a baixa automática (o "Desfazer" recusa) e app_method diz o meio; paid_method
-- continua PIX no Pix e OTHER no cartão, como os apps antigos esperam.
ALTER TABLE group_charges
    ADD COLUMN payable_cents bigint,
    ADD COLUMN fee_policy varchar(8),
    ADD COLUMN fee_schedule_id uuid,
    ADD COLUMN settled_by varchar(8),
    ADD COLUMN app_method varchar(8),
    ADD CONSTRAINT ck_group_charges_payable CHECK (payable_cents IS NULL OR payable_cents >= amount_cents),
    ADD CONSTRAINT ck_group_charges_fee_policy CHECK (fee_policy IS NULL OR fee_policy IN ('ABSORB', 'PASS')),
    ADD CONSTRAINT ck_group_charges_settled_by CHECK (settled_by IS NULL OR settled_by = 'APP'),
    ADD CONSTRAINT ck_group_charges_app_method CHECK ((settled_by IS NULL) = (app_method IS NULL)
        AND (app_method IS NULL OR app_method IN ('PIX', 'CARD')));

-- Lançamentos automáticos do caixa: as taxas de um pagamento pelo app e o Pix da revisão que não era cobrança.
-- O app marca "automático" e não deixa editar; nulo é o lançamento do gestor, como hoje.
ALTER TABLE group_expenses
    ADD COLUMN origin varchar(32),
    ADD CONSTRAINT ck_group_expenses_origin CHECK (origin IS NULL OR origin IN ('RECEIVABLES_FEES', 'RECEIVABLES_REVIEW'));
