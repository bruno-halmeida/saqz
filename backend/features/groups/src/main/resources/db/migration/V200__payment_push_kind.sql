-- Tipo do aviso do recebimento pelo app no push (canal PAYMENT): o app abre o destino certo do toque (F2, rodada 3).
-- Aditivo: só os avisos novos do recebimento preenchem; nulo nos demais e nos antigos.
ALTER TABLE group_messages ADD COLUMN payment_kind varchar(32)
    CHECK (payment_kind IS NULL OR payment_kind IN
        ('PAID', 'REVIEW', 'CONFIRMED', 'SETTLE', 'REFUSED', 'PRICE_CHANGE', 'AUTOPAY_CANCELLED'));
