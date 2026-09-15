# Evidências

## T1 — AC1
Gate `:features:groups:test --tests '*UazapiNotificationSenderTest'`: PASS, 4 testes, 0 falhas.

| Critério | Evidência (UazapiNotificationSenderTest.kt) | Resultado esperado |
|---|---|---|
| Envio autenticado e payload | :25 `assertEquals(WhatsAppDelivery.Accepted, ...)`; :27–34 assertions de path, token, número, texto, track_id e flags | aceite, `/send/text`, telefone sem +, rastreio estável |
| Limite do provedor | :43 `assertEquals(WhatsAppDelivery.Retry(7200), ...)` | esperar pelo menos 7200 segundos |
| HTTP transitório/permanente | :52–53 `assertEquals(expected, ...)` | 408/5xx retentar; 400/401/403/404 falhar |
| Telefone inválido e rede | :59 `assertEquals(WhatsAppDelivery.Failed, ...)`; :61 `assertEquals(WhatsAppDelivery.Retry(), ...)` | rejeitar grupo, retentar rede |

Mapeamento reverso: as quatro funções de teste cobrem exclusivamente AC1. Nenhum teste removido/ignorado; testes usam o SDK real com HTTP local. Track ID é rastreio, não garantia de idempotência do provedor; timeout após aceite pode duplicar uma entrega remota.
