-- Aviso ao dono e admins quando o prazo de confirmação vence com fila e vaga, no grupo em escolha
-- manual (rodada multi-aparelho de 09/10/2026). Migration própria: o valor novo do enum só pode ser
-- usado depois do commit desta (mesmo motivo da V78 e da V87).
ALTER TYPE group_message_channel ADD VALUE 'WAITLIST_DEADLINE';
