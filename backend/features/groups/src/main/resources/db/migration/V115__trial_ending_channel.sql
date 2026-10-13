-- Aviso de fim do teste do Organizador (3 dias, 1 dia e no dia do fim), só para o dono.
-- Migration própria: o valor novo do enum só pode ser usado depois do commit desta (como V78, V87, V95, V97).
ALTER TYPE group_message_channel ADD VALUE 'TRIAL_ENDING';
