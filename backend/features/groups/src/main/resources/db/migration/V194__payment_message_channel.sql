-- Avisos do recebimento pelo app (FIN-013/FIN-014): pagamento, revisão e "Pagamento confirmado". Migration própria: o valor
-- novo do enum só pode ser usado depois do commit desta (como V115). Os apps das lojas descartam canal que não conhecem.
ALTER TYPE group_message_channel ADD VALUE 'PAYMENT';
