-- DEB-064 (NOT-001): a central de avisos sempre mostra tudo; as preferências controlam só push e WhatsApp. O gatilho
-- da V70 (recriado até a V119) gravava `in_app = false` quando "Avisos dos grupos", "Mensagens" ou "Lembretes de
-- presença" estavam desligados, e a central escondia essas linhas. Sem o gatilho, `in_app` fica no padrão (true) e a
-- central deixa de filtrar por ele. A coluna fica: a contagem de destinatários e os apps antigos não dependem de sair.
-- Linhas antigas com `in_app = false` aparecem na central a partir daqui.
DROP TRIGGER notification_in_app ON group_notifications;
DROP FUNCTION notification_in_app_preference();
