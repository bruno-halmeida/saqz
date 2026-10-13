-- Aviso ao mensalista de que a confirmação automática vale para ele. Migration própria: o valor novo do
-- enum só pode ser usado depois do commit desta (como V78, V87, V95, V97).
ALTER TYPE group_message_channel ADD VALUE 'AUTO_CONFIRMATION';
