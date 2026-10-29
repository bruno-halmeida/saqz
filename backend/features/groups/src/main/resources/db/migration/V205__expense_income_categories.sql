-- Caixa novo (DEB-148, FIN-006): Patrocínio e Venda, só de entrada. Migração própria: o valor novo do enum só pode
-- ser usado depois do commit desta (como V97); o CHECK que os prende à entrada vem na V206.
ALTER TYPE public.expense_category ADD VALUE IF NOT EXISTS 'SPONSORSHIP';
ALTER TYPE public.expense_category ADD VALUE IF NOT EXISTS 'SALE';
