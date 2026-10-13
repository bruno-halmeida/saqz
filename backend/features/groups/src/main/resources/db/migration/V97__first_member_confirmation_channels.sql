-- Avisos de "pico" do onboarding para dono e admins: o primeiro atleta que entrou sozinho no grupo
-- (FIRST_MEMBER) e a primeira confirmação de presença de um não-dono (FIRST_CONFIRMATION).
-- Migration própria: o valor novo do enum só pode ser usado depois do commit desta (como V78, V87, V95).
ALTER TYPE group_message_channel ADD VALUE 'FIRST_MEMBER';
ALTER TYPE group_message_channel ADD VALUE 'FIRST_CONFIRMATION';
