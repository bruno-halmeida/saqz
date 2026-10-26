-- FIN-012: a assinatura cobra no dia de vencimento do atleta. Quando a adesão cobra na hora (mês em aberto) o ciclo do
-- Asaas passa a contar da data da adesão; aligned_due_day guarda o dia para o qual a assinatura já foi realinhada
-- (nulo = realinhar). Mudar o dia do atleta depois também dispara o realinhamento.
ALTER TABLE card_subscriptions ADD COLUMN aligned_due_day integer CHECK (aligned_due_day IS NULL OR aligned_due_day BETWEEN 1 AND 31);
