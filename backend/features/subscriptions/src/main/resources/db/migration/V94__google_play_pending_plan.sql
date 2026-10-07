-- Troca de plano adiada pelo Play (ReplacementMode.DEFERRED, usada para descer de plano): o
-- token novo mantém o item antigo até a renovação e traz em deferredItemReplacement o produto
-- que entra depois. Como o auto_renew_plan da App Store, guarda só o plano, para "Meu plano"
-- mostrar a troca agendada e os limites já respeitarem o plano menor.
ALTER TABLE google_play_subscriptions ADD COLUMN pending_plan subscription_plan;
