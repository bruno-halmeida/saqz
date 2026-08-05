-- Token de cartao do checkout direto (VUL-194). Nunca o PAN/CVV, que nunca chegam a persistir:
-- so o token que a Asaas devolve na criacao (para trocar cartao sem redigitar) e os 4 ultimos
-- digitos + bandeira, so para "final 1234" na tela. Nullable e sem backfill: linhas PIX nunca
-- preenchem, linhas CREDIT_CARD antigas a este ticket nao tem o dado para inventar.
ALTER TABLE subscriptions
    ADD COLUMN asaas_credit_card_token varchar(64),
    ADD COLUMN credit_card_last4 varchar(4),
    ADD COLUMN credit_card_brand varchar(32);
