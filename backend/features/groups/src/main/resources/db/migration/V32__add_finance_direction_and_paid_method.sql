-- A despesa vira lançamento: OUT reproduz exatamente o que as linhas existentes já são.
ALTER TABLE group_expenses ADD COLUMN direction varchar(3) NOT NULL DEFAULT 'OUT';
ALTER TABLE group_expenses ADD CONSTRAINT ck_group_expenses_direction CHECK (direction IN ('IN', 'OUT'));

ALTER TABLE group_expenses DROP CONSTRAINT ck_group_expenses_category;
ALTER TABLE group_expenses ADD CONSTRAINT ck_group_expenses_category
    CHECK (category IN ('VENUE', 'EQUIPMENT', 'REFEREE', 'RACHA', 'OTHER'));

-- O histórico é append-only e guarda o snapshot inteiro, então acompanha a direção.
ALTER TABLE group_expense_events ADD COLUMN direction varchar(3) NOT NULL DEFAULT 'OUT';
ALTER TABLE group_expense_events ADD CONSTRAINT ck_group_expense_events_direction CHECK (direction IN ('IN', 'OUT'));

-- Só a transição para PAID preenche o método (PIX quando o contrato antigo omite o campo),
-- então a coluna nasce nula em vez de DEFAULT 'PIX': cobrança pendente não foi recebida por Pix.
ALTER TABLE group_charges ADD COLUMN paid_method varchar(8);
ALTER TABLE group_charges ADD CONSTRAINT ck_group_charges_paid_method
    CHECK (paid_method IS NULL OR paid_method IN ('PIX', 'CASH', 'OTHER'));

-- Cobrança já recebida antes desta migração nunca vai transitar de novo, então o default de
-- produto (PIX) precisa alcançar o histórico aqui. Só PAID: pendente, isenta e cancelada
-- nunca foram recebidas e continuam nulas.
UPDATE group_charges SET paid_method = 'PIX' WHERE status = 'PAID' AND paid_method IS NULL;
