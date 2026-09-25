-- Token de push-to-start da Live Activity (iOS 17.2+): é por ele que o servidor abre a janela de
-- presença das 24 h. Nulo em Android, em iOS antigo e enquanto o app ainda não o recebeu.
ALTER TABLE notification_devices ADD COLUMN live_activity_start_token varchar(4096);
