# Resultado atual — round 2 (2026-09-15)

Escopo final inclui `d788b4c2` (T4).

**Implementação reavaliada: nenhum defeito funcional bloqueante remanescente identificado. Cobertura de aceitação ainda parcial; não é PASS pleno de todos os ACs.** Gates automatizados aprovados; entrega/permissão/toque em push real não validados. O relatório do round 1 abaixo é histórico, não uma lista de bugs ainda abertos.

## Correções conferidas

| Achado round 1 | Evidência atual | Resultado |
|---|---|---|
| Revogação perdida no logout/troca | `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/notifications/NotificationSessionBinding.kt:23` inicia needsClear; `:38–44` mantém falha e só libera após sucesso; `:58–62` timeout retorna false. AndroidNotificationPort.kt:49 usa `done(it.isSuccessful)` e IOSNotificationPort.swift:55 usa `completion.finish(error == nil)` | Corrigido; sem dependência de DELETE com credencial de outra conta |
| Falha/reinício sem retry | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/notifications/NotificationSessionBindingTest.kt:57` devices continua 1 na falha; `:60` tokens corretos após retry; `:66` restartedGateway vazio até revogar; `:69` token-3 após sucesso. SaqzApp.kt:70–72 LifecycleResumeEffect chama refresh | Corrigido, 3 testes de sessão aprovados |
| Limites e grupo diferente sem assertivas | `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/communication/ChargeReminderIntegrationTest.kt:100–104` INVALID/zero para 201, count 200 para 200; `:114–116` CONFLICT/zero para seleção cross-group | Corrigido |
| Transporte HTTP não exercitado | `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/ChargeNotificationHttpTest.kt:57–68` 403,400,422,200/notificationCount=1,replay/count=1,409,404; `:73–83` token inválido422,body ausente400,registro204/user_id=member, DELETE conta diferente mantém1 e dono remove0 | Corrigido binding/autorizações de domínio/status/SQL; teste standalone injeta actor e não exercita Spring Security/token real |
| HTTP malformado500 e handler duplicado durante correção | `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/http/SafeExceptionHandler.kt:481–491` único handler,400 VALIDATION_FAILED restrito aos novos endpoints, mantém caminho app-link existente | Corrigido; teste HTTP passou |
| Destino CHARGE diferente da promessa | Spec AC5 agora explicita abrir grupo que mostra pendências próprias; SaqzNavHost.kt:795–796 abre Details. GroupDetailsScreen.kt:143 renderiza ownCharges. `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/ui/details/GroupDetailsScreenTest.kt:219–221` assertExists em OwnCharges/Pending/History | Comportamento coerente com spec clarificada e cobranças de jogos/mensais; falta assertiva específica CHARGE→Details |
| Backoff30s e título com grupo | JdbcNotificationPush.kt:70 passa a `60 * power(2, attempts)`; ChargeReminderIntegrationTest.kt:134 `assertEquals("Saqz", message.title)`, :135 corpo sem valor | Corrigidos mínimo inicial e título; sem teste da classificação do adaptador/Retry-After |

## Gates atuais e integridade

- Gate backend de tasks.md chamado independentemente: exit0, BUILD SUCCESSFUL, tarefas UP-TO-DATE. XMLs atuais: ChargeReminderIntegrationTest **8/8**, GroupCommunicationIntegrationTest **7/7**, zero skipped/falhas. Antes:7; agora15; delta+8; nenhum teste anterior removido.
- Gate HTTP de tasks.md: log `/tmp/saqz-charge-http.log` BUILD SUCCESSFUL; XML ChargeNotificationHttpTest **1/1**, zero skipped/falhas. Nova chamada independente confirmada no fechamento.
- Logs do autor inspecionados: `/tmp/saqz-push-session-final.log` BUILD SUCCESSFUL; XML NotificationSessionBindingTest **3/3**, zero skipped/falhas. Android compile e detekt passaram no mesmo gate. `/tmp/saqz-push-ios-final.log`: BUILD SUCCEEDED. Não reexecutados pelo verificador neste round.
- Sensor anterior permanece válido para lógica não alterada de autorização, preferências e receipt de entrega: **3/3 mortos**, sem repetição desnecessária e sem mutação na árvore real.
- tasks.md agora contém comandos reproduzíveis; T1–T4 marcadas completas. T5 pode registrar verificação concluída **com as ressalvas de cobertura abaixo**, sem afirmar todos ACs integralmente provados.

## Resultado por AC e limites remanescentes

- **AC1–AC3:** resultados precisos assertados em serviço/SQL e transporte HTTP. Autenticação usa a cadeia existente do produto; o novo teste HTTP injeta actor, não testa Firebase/Spring Security de ponta a ponta.
- **AC4:** fila, destinatários, preferência, receipt por dispositivo, token inválido e payload privado assertados. Adaptador Firebase/Retry-After não têm teste específico; sem garantia exactly-once externa. Backoff esgota após10 tentativas e operação/diagnóstico estão documentados em `docs/notifications/charge-reminders.md:26`.
- **AC5:** seleção/envio/erro/retry/duplicação cobertos. Destino revisado por inspeção e conteúdo do grupo tem testes existentes; **nenhuma assertiva nova específica do caminho CHARGE→Details**. Evidence-or-zero: essa subparte não conta como cobertura automatizada completa.
- **AC6:** sessão, retry, troca, logout e reinício cobertos por três testes; ports compilados para Android/iOS. **Permissão e abertura pelo push em aparelho não executadas/assertadas**. A spec e documentação deixam esse limite explícito. Revogação offline é eventual; token inválido é removido do backend quando provedor informar UNREGISTERED.

**Fechamento:** bugs funcionais encontrados no round 1 resolvidos no recorte revisado; gates verdes e sensor discriminante. Permanecem limites explícitos de testes/validação externa, portanto não afirmar 6/6 ACs integralmente cobertos. Não foram enviadas cobranças nem push reais. Lições L-017–L-020 preservadas; nenhuma nova lição repetida para os mesmos sinais.

---

# Histórico — round 1

# Cobrança por notificação — validação independente, round 1

Data: 2026-09-15. Verifier independente (autor ≠ verificador).
Escopo: d0d098ce, 9888dd81, 01cd4850 e implementação nativa não commitada observada no início do round. Correções concorrentes do autor **ainda não revalidadas**. Nenhuma implementação/teste real foi alterado pelo verificador.

## Veredito: FAIL — correções e cobertura pendentes

AC2 e AC3 cobertos no nível integração de serviço; AC1, AC4, AC5 e AC6 parcialmente cobertos. Sem aprovação implícita de entrega real FCM/APNs.

## Tarefas

T1–T3 marcadas completas; T4 e T5 ainda abertas na leitura inicial. tasks.md descreve gates, mas não contém seção com comandos reproduzíveis; comando backend exato veio do coordenador.

## Evidência por critério

Paths abreviados nesta tabela:
- B = backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/communication/ChargeReminderIntegrationTest.kt
- G = mobile/features/groups/data/src/commonTest/kotlin/br/com/saqz/groups/data/communication/KtorChargeReminderGatewayTest.kt
- V = mobile/features/groups/presentation/src/androidHostTest/kotlin/br/com/saqz/groups/presentation/ui/finance/sheets/ChargeReminderViewModelTest.kt
- S = mobile/features/groups/presentation/src/androidHostTest/kotlin/br/com/saqz/groups/presentation/ui/finance/sheets/ChargeSelectionTest.kt
- D = mobile/features/groups/data/src/commonTest/kotlin/br/com/saqz/groups/data/communication/KtorNotificationDeviceGatewayTest.kt
- N = mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/notifications/NotificationSessionBindingTest.kt (versão inicial)

| AC | Resultado exigido | file:line + assertiva | Veredito |
|---|---|---|---|
| AC1 | Organizador/admin; seleção 1..200 distinta, pendente, do grupo, atômica | B:63 `assertEquals(2, receipt.notificationCount)`; B:85–92 `Failure(FORBIDDEN/NOT_FOUND/INVALID/CONFLICT)` e `assertEquals(0L, count("group_notifications"))` | Parcial: faltam bordas 200/201, cobrança de outro grupo explicitamente e transporte HTTP real |
| AC2 | Uma inbox privada por cobrança ativa, dados do servidor, CHARGE proibido no mural | B:65–73 `inbox.size == 1`, `body.contains("70,00")`, `channel == CHARGE`, inbox do owner vazia e messages/publish CHARGE retornam INVALID; B:97–98 inativo retorna CONFLICT e zero notificações | PASS no serviço/SQL |
| AC3 | Replay idempotente, conflito por seleção diferente, pagamentos intactos | B:78–81 recibos iguais, uma notificação/fila, `Failure(CONFLICT)`; B:71 duas cobranças continuam PENDING | PASS no serviço/SQL; concorrência específica do novo endpoint não testada |
| AC4 | Inbox sem dispositivo, preferência, retry durável, token inválido removido, payload sem valor | B:65/70 inbox e fila sem registro; B:109–126 grupo correto, corpo não contém 70,00, tokens exatos, recibos de entrega e conclusão; B:137–143 sender proibido após transferência/mute e inbox preservada | Parcial: adaptador FCM real sem teste; backoff inicial 30s incompatível com quota FCM |
| AC5 | IDs selecionados, seleção múltipla/todos, duplicação em voo, erro/sucesso, requestId estável; abrir cobrança própria | G:36–45 POST/path/Bearer/body com requestId e IDs exatos; V:32–41 erro, replay e sent; V:52–61 vazio/in-flight; S:33–42 seleção; S:65/73 IDs exatos | Parcial: navegação inicial abre grupo, não destino de cobranças explicitado na spec; sem assertiva de navegação |
| AC6 | Registro autenticado, permissão/token nativos, desvinculação em logout/troca e abrir app | D:34–43 PUT/path/Bearer/payload sem user e sucesso; D:47–52 DELETE/Bearer e conflito; N:23–38 tokens, clear e chamadas removed; N:45–54 retry/rotação | FAIL inicial: fakes não modelam sessão/erro de revogação; permissão e abertura nativas sem assertivas de comportamento |

## Achados e fix tasks (todos reportados no mesmo round)

1. **Major — AC6, revogação pode ser perdida.** No binding inicial, unregister ocorre após sessão já mudar; KtorNotificationDeviceGateway usa credencial atual, mas JdbcNotificationPush.kt:25–27 só remove pelo actor dono. O próprio B:132–133 prova que conta diferente não remove. AndroidNotificationPort.clear ignorava task.isSuccessful e IOSNotificationPort.clear ignorava error; registered era descartado mesmo em falha. Logout offline deixava token anterior válido sem revogação pendente. Fix: resultado explícito de clear, preservar/repetir pendência inclusive após reinício, bloquear novo vínculo até revogação concluída, testar falha/timeouts/troca durante callbacks. Autor iniciou correção durante o round; exige rerun.
2. **Major — AC5, destino divergente.** SaqzNavHost.kt:795–796 abre GroupsRoute.Details, não cobranças próprias. Fix: alinhar destino com experiência aceita e testar rota; se manter grupo por suportar mensalidade e jogos, registrar essa decisão na spec sem alegar que navega diretamente à cobrança.
3. **Major — cobertura HTTP/limites.** Nenhum teste do novo POST notify ou PUT/DELETE notification-devices atravessa controllers/segurança reais. AC1 não testa 200 aceitos/201 rejeitados, foreign-group, DTO malformado/sem requestId. Fix: testes de endpoint para sucesso, autenticação, autorização, validação, conflito e payload; ampliar bordas do serviço. Controller foi alterado sem commit durante o round, reforçando que teste MockEngine do cliente não prova binding Jackson real.
4. **Minor — AC4, FCM retry.** JdbcNotificationPush.kt:69 iniciava 30s e FirebaseNotificationPushSender classificava todos erros exceto UNREGISTERED como RETRY. FCM exige mínimo 60s em QUOTA_EXCEEDED e respeitar Retry-After em indisponibilidade. Fix: classificação/backoff apropriado com teste do adaptador, documentar esgotamento após 10 tentativas e diagnóstico. Autor anunciou ajuste para 60s; ainda não revalidado.

## Gate executado independentemente

```sh
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home backend/gradlew -p backend :features:groups:integrationTest --tests '*ChargeReminderIntegrationTest' --tests '*GroupCommunicationIntegrationTest' :bootstrap:compileKotlin
```

Exit 0, BUILD SUCCESSFUL. XMLs: ChargeReminderIntegrationTest 6, GroupCommunicationIntegrationTest 7; **13 passados, 0 falhas, 0 skipped**. Antes da feature: 7 testes de comunicação, arquivo não alterado; depois: 13, delta +6. Nenhum teste removido/enfraquecido nesse recorte. Tasks Gradle NO-SOURCE/SKIPPED de configuração não são testes pulados.

Gates mobile: coordenador informou dados iOS, 8 testes host seleção/VM/screenshot, binding iOS e lint/Android compile aprovados; são evidências do autor, não execução independente deste round. iOS nativo em nova compilação após ajuste da bridge. Não executar nem afirmar push real sem credenciais/aparelho.

## Sensor de discriminação

Cópia exclusiva `/tmp/saqz-charge-sensor-20260915` feita de backend (176 MB, caches locais reaproveitados). Mutação anterior revertida na cópia antes de cada próxima. Comando: gradlew dessa cópia, `:features:groups:integrationTest --tests '*ChargeReminderIntegrationTest'`. Árvore real intacta.

| Mutação | Local | Resultado |
|---|---|---|
| Retirar bloqueio ATHLETE (`if (false)`) | ChargeReminderService.kt:29 | KILLED: B:85 falha, 6 testes/1 falha, exit 1 |
| Ignorar preferência reminders (`AND true`) | JdbcNotificationPush.kt:44 | KILLED: B:142 sender lança `muted`, 6 testes/1 falha, exit 1 |
| Ignorar recibo por dispositivo (`AND false` no NOT EXISTS) | JdbcNotificationPush.kt:57 | KILLED: B:124 recebe dispositivo já entregue, 6 testes/1 falha, exit 1 |

Profundidade lightweight: 3 injetadas, **3 mortas, 0 sobreviventes**. Feature envia lembretes; não muta pagamento/auth base. Sensor comprova somente os comportamentos escolhidos, não preenche as lacunas de cobertura.

## Qualidade e limites

- Separação domain/data/native e DI acompanham mobile/AGENTS.md; SQL mantém transação, trava grupo e seleção; outbox e receipt evitam reenvio normal de dispositivos já concluídos.
- Alterações externas a cobrança presentes na árvore não foram incluídas na avaliação nem modificadas.
- Regra evidence-or-zero/per-layer: FAIL nas rotas novas e comportamentos nativos não assertados.
- FCM é entrega externa: crash após envio e antes de commit pode duplicar push; receipt local não garante exactly-once externo. Inbox é persistente/idempotente; nenhuma promessa de exactly-once FCM.
- Após 10 falhas a fila permanece não concluída e não é mais drenada; esgotamento/alerta e retry após recuperação não foram testados. Inbox continua disponível.
- Payload inicial ocultava valor mas incluía nome de grupo no título; autor está tornando genérico. Revogação offline não pode prometer impedir mensagem já aceita pelo provedor.
- UAT humano, permissão/APNs/FCM em aparelho e toque no push não realizados; limite externo explícito da spec.

Fontes oficiais consultadas para limites reais: [FCM error codes](https://firebase.google.com/docs/cloud-messaging/error-codes), [FCM token management](https://firebase.google.com/docs/cloud-messaging/manage-tokens). UNREGISTERED é token inválido; INVALID_ARGUMENT só deve invalidar token quando payload válido está estabelecido. Não apagar indiscriminadamente por erro de payload.

## Traceabilidade e próximo round

AC2/AC3 verificados no recorte de serviço. AC1/AC4/AC5/AC6 precisam fix/coverage. Manter T4/T5 abertas até revalidação. Não atualizar spec como totalmente verificada com este resultado. Reexecutar gates afetados após fixes e anexar round 2; preservar limites de validação externa.
