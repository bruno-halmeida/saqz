-- O "link de compartilhar a lista" (por jogo) foi removido (LINK-006, DEB-044): o app nunca teve tela para ele
-- e em produção a tabela tem 0 linhas. O link do lembrete segue em notification_attendance_links.
DROP TABLE game_attendance_links;
