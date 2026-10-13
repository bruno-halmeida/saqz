-- Aviso ao dono e admins quando alguém pede para entrar num grupo com aprovação (ENTRY_REQUEST).
-- Migration própria: o valor novo do enum só pode ser usado depois do commit desta (como V78, V87, V95, V97).
ALTER TYPE group_message_channel ADD VALUE 'ENTRY_REQUEST';
