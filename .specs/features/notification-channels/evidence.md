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

## T2 — AC2–4
Gates JDBC (7 casos novos e regressões de comunicação/cobranças), HTTP (6 casos) e arquitetura (20): PASS. Compilação bootstrap PASS.

- `NotificationChannelsIntegrationTest.kt:37`: `assertEquals(0L, count("notification_whatsapp_queue"))` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:38`: `assertEquals(1, inbox().size)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:44`: `assertEquals(1, sent.size)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:45`: `assertEquals("+5511999999999", sent.single().phone)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:46`: `assertEquals("Saqz · Futebol\nTreino amanhã", sent.single().body)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:47`: `assertEquals(inbox().first().sequence, sent.single().notificationId)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:48`: `assertEquals("ACCEPTED", status())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:49`: `assertTrue(service.inbox(owner, null).success().items.isEmpty())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:55`: `assertEquals(preferences, service.savePreferences(member, preferences))` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:56`: `assertEquals(preferences, service.preferences(member))` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:59`: `assertEquals(message, publish(MessageChannel.NOTICE, request = request))` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:60`: `assertTrue(inbox().isEmpty())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:61`: `assertEquals(0L, count("notification_push_queue"))` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:62`: `assertEquals(1L, count("notification_whatsapp_queue"))` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:66`: `assertEquals(1, sent.size)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:73`: `assertEquals("PENDING", status())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:74`: `assertTrue(jdbc.sql("SELECT next_attempt_at >= now() + interval '7190 seconds' FROM notification_whatsapp_queue")` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:76`: `assertEquals(1, inbox().size)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:79`: `assertEquals("member-device", sent.single().first)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:80`: `assertEquals(group, sent.single().second.groupId)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:81`: `assertEquals("Você recebeu um aviso do grupo. Abra o app para conferir.", sent.single().second.body)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:84`: `assertEquals("ACCEPTED", status())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:85`: `assertEquals(2, jdbc.sql("SELECT attempts FROM notification_whatsapp_queue").query(Int::class.java).single())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:101`: `assertEquals("CANCELLED", status(), change)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:109`: `assertEquals(0L, count("notification_whatsapp_queue"))` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:113`: `assertEquals("FAILED", status())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:116`: `assertEquals("FAILED", status())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:117`: `assertEquals(10, jdbc.sql("SELECT attempts FROM notification_whatsapp_queue").query(Int::class.java).single())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:131`: `assertEquals(1L, count("notification_whatsapp_queue"))` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:135`: `assertEquals("CANCELLED", status())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:136`: `assertEquals(1, inbox().size)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:149`: `assertEquals(1L, count("notification_whatsapp_queue"))` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:150`: `assertEquals(MessageChannel.CHARGE, inbox().single().message.channel)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:151`: `assertTrue(service.inbox(owner, null).success().items.isEmpty())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:154`: `assertEquals("CANCELLED", status())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `NotificationChannelsIntegrationTest.kt:168`: `private fun <T> CommunicationResult<T>.success(): T = assertIs<CommunicationResult.Success<T>>(this).value` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:85`: `assertEquals(401, request("PUT", path, payload, actor = null).statusCode())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:86`: `assertEquals(200, request("PUT", path, payload, member).statusCode())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:88`: `assertEquals(json.readTree(payload), saved)` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:90`: `assertEquals(false, other["whatsapp"]["notices"].booleanValue())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:91`: `assertEquals(200, request("PUT", path, """{"notices":true,"messages":true,"reminders":false}""", member).statusCode())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:93`: `assertEquals(saved["whatsapp"], legacy["whatsapp"])` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:94`: `assertEquals(false, legacy["push"]["charges"].booleanValue())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:95`: `assertEquals(422, request("PUT", path, "{}", member).statusCode())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:100`: `assertEquals(401, request("GET", path, actor = null).statusCode())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:101`: `assertEquals(404, request("GET", path, actor = stranger).statusCode())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:105`: `assertEquals(200, posted.statusCode())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:107`: `assertEquals(member.toString(), message["authorId"].stringValue())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:108`: `assertEquals(group.toString(), message["groupId"].stringValue())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).
- `GroupCommunicationEndpointIntegrationTest.kt:109`: `assertEquals("Olá grupo", message["body"].stringValue())` — AC2–4 (preferências, privacidade, filas, cancelamento e retentativa).

Mapeamento reverso: todos os casos novos exercitam AC2–4; nenhuma alteração de testes anteriores. Teste HTTP detectou desserialização incorreta de DTOs aninhados: corrigida com DTOs de entrada explícitos e retestado.

## T3 — AC5
Gate focado iOS (gateway e comunicação), detekt dos três módulos e compilação Android: PASS. Capturas Roborazzi dos três canais, WhatsApp ligado/desligado/salvando e estados já existentes geradas; WhatsApp inspecionado visualmente.
- KtorCommunicationGatewayTest.deliveryChannelsRoundTripWithoutChangingOtherPreferences: assert de JSON completo e igualdade de NotificationPreferences tanto em PUT como GET (AC5).
- CommunicationScreenTest.whatsappOptInChangesOnlyTheSelectedCategory: assert de payload completo preservando push e ausência de switch de conversas (AC5).
- CommunicationScreenTest.pushSettingsAreDisabledWhileSaving: assertIsNotEnabled em cobrança (AC5).
- Testes anteriores de salvar/erro e payload legado preservados. Mapeamento reverso: testes novos somente AC5.

Execução ampla iOS de apresentação encontrou dois testes fora do escopo com expectativa “reserva” e recurso “lista de espera”: HomeViewModelTest e GroupDetailsScreenTest. A diferença já está em HEAD nos recursos originais; arquivos envolvidos não foram modificados. Não houve execução isolada da base. Gate focado de notificações verde; gate amplo continua vermelho.
