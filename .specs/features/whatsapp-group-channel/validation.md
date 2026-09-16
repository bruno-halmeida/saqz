# Validação independente — Canal de grupo de WhatsApp (AC1–AC7)

**Range validado:** `ea8d33e1..ccfe2abf` (HEAD `ccfe2abf`)
**Verificador:** agente independente (≠ autor). Trabalho no main integrado.
**Método:** resultado ancorado na spec + sensor de discriminação (mutação) em cópia isolada.

---

## 1. Gates executados

### Backend FULL (re-run forçado, `--rerun-tasks`; `JAVA_HOME=openjdk@21`)

```sh
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :features:groups:test :features:groups:integrationTest :bootstrap:test :architecture-tests:test --rerun-tasks
```

`BUILD SUCCESSFUL in 4m 20s` (34 actionable tasks, 34 executed). Contagens dos XML de resultado:

| Suíte | Testes | Falhas | Erros | Skipped |
|---|---|---|---|---|
| `:features:groups:test` | 690 | 0 | 0 | 0 |
| `:features:groups:integrationTest` | 571 | 0 | 0 | 0 |
| `:bootstrap:test` | 491 | 0 | 0 | 0 |
| `:architecture-tests:test` | 20 | 0 | 0 | 0 |

### Mobile ALVOADO (`iosSimulatorArm64Test`)

```sh
cd mobile && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :features:groups:data:iosSimulatorArm64Test :features:groups:presentation:iosSimulatorArm64Test
```

| Suíte | Testes | Falhas | Erros |
|---|---|---|---|
| `:features:groups:data:iosSimulatorArm64Test` | 270 | 0 | 0 |
| `:features:groups:presentation:iosSimulatorArm64Test` | 591 | **2** | 0 |

**As 2 falhas são alheias ao range e pré-existentes** — arquivos não tocados em `ea8d33e1..ccfe2abf`:

- `HomeViewModelTest :: waitlisted mensalista formats the reserva subtitle` — espera `"Você está na reserva de Vôlei do CERET."`, real `"Você está na lista de espera de Vôlei do CERET."` (`strings_home.xml:11` diz "lista de espera" em HEAD e na base).
- `GroupDetailsScreenTest :: group response shows waitlist position and locks after deadline` — espera o nó `"Você é o 3º da reserva."`, que não existe (`strings_game_response.xml:10` usa "na lista de espera").

Nenhum dos dois arquivos (`mobile/features/groups/presentation/.../home/HomeViewModelTest.kt`, `.../ui/details/GroupDetailsScreenTest.kt`) nem `strings_home.xml`/`strings_game_response.xml` aparece no diff do range. É dívida de baseline, não regressão do canal de grupo. **Todas as suítes alvo da feature em presentation passam** (§AC7).

---

## 2. Veredito por AC

### AC1 — Vínculo (PASS)

Evidência de desfecho (spec: validar gestor → inviteInfo → anti-sequestro → instância conectada → join → confirmar Participants → persistir habilitado; erros 422/409/422/409/502/403):

- `LinkGroupWhatsAppTest` — **15 testes, 0 falhas**. Happy path com canal habilitado (`LinkGroupWhatsAppTest.kt:18`); puro código (`:40`); atleta 403 (`:49`); grupo inexistente (`:58`); link irreconhecível rejeitado antes do provider (`:66`); InvalidInvite/NotInGroup (`:75`); instância desconectada (`:86`); provider indisponível (`:98`); **anti-sequestro** sem admin casando com membro ativo → `UnresolvableAdmins` (`:109`); convite sem telefone de admin (`:120`); JID já vinculado a outro grupo → `JidInUse` (`:131`); re-vínculo do mesmo grupo é upsert (`:140`); join não confirmado → `JoinPending` (`:154`, `:163`); join rejeitado → `InvalidInvite` (`:172`).
- `GroupWhatsAppBindingEndpointIntegrationTest` — **10 testes, 0 falhas**. Fluxo manager link/read/toggle com `status` ACTIVE→DISABLED→ACTIVE (`:65`); 401 sem token; 403 não-gestor em toda rota (`:103`); 404 grupo desconhecido (`:112`); 422 payload inválido (`:119`); 422 convite inválido (`:127`); 422 admin não resolvível (`:134`); 409 JID em uso (`:143`); 409 join não confirmado (`:157`); 502 desconectado/indisponível (`:163`).
- Mapeamento de status confirmado em `GroupWhatsAppBindingController.kt:66-88` (InvalidInvite/UnresolvableAdmins→422; JidInUse/JoinPending→409; Disconnected/Unavailable→502; AccessForbidden→403).
- Persistência: `JdbcGroupWhatsAppBindingRepositoryIntegrationTest.kt:53` prova `enabled=true`, `broken_at=NULL`, `status()==ACTIVE` no nascimento; `LinkGroupWhatsApp.kt:111-121` persiste com `enabled=true`.

### AC2 — Preferência do gestor (PASS)

- `ManageGroupWhatsAppBindingTest` — **8 testes, 0 falhas**: owner lê ACTIVE (`:19`); admin lê BROKEN preservando estado (`:29`); sem vínculo (`:38`); atleta 403 em leitura e escrita (`:43`); grupo inexistente (`:52`); desabilitar **preserva o vínculo** e reporta DISABLED (`:60`); reabilitar sem novo join (`:72`); alterar vínculo ausente = NotBound (`:83`).
- `JdbcGroupWhatsAppBindingRepositoryIntegrationTest` — **9 testes, 0 falhas**: PK+UNIQUE (`:30`); find null (`:48`); nasce habilitado (`:53`); **upsert substitui e libera o JID anterior** (`:72`); UNIQUE de JID entre grupos (`:90`); desabilitar preserva e reporta DISABLED (`:99`); reabilitar ACTIVE (`:113`); markBroken preserva vínculo (`:128`); BROKEN precede DISABLED (`:144`).
- `JdbcGroupWhatsAppBindingMembersIntegrationTest` — **3 testes, 0 falhas**: `memberPhones` normaliza para dígitos e exclui sem telefone (`:27`); exclui grupo soft-deleted (`:42`); `findByJid` (`:52`).
- `JdbcGroupWhatsAppBindingRepository.kt:99-107` (`SET_ENABLED`/`MARK_BROKEN`); reabilitar não chama `join` (nenhuma referência a directory em `ManageGroupWhatsAppBinding.kt`).
- Estados visíveis via GET com `status` (endpoint `:77-88`) restritos a owner/admin (403 em `:105`).

### AC3 — Enfileiramento (PASS)

- `V76__notification_whatsapp_group_queue.sql:12-24`: trigger `AFTER INSERT ON group_messages`, `INSERT ... SELECT` com `b.group_id = NEW.group_id AND b.enabled AND b.broken_at IS NULL AND NEW.channel IN ('NOTICE','REMINDER')`, `ON CONFLICT (message_id) DO NOTHING` → um job por message_id.
- `NotificationWhatsAppGroupIntegrationTest` — **13 testes, 0 falhas**: um job por aviso e replay do mesmo `requestId` não duplica (`:50`, count 1 → 2 com novo request); **CHAT/CHARGE nunca enfileiram** (`:61`); grupo sem vínculo / desabilitado / quebrado não enfileira (`:68`).
- Corpo composto em `JdbcNotificationWhatsAppGroup.kt:105-110` (prefixo `Saqz · {grupo}`; REMINDER acrescenta o link do `notification_attendance_links.code`).

### AC4 — Entrega e revalidação (PASS)

- `JdbcNotificationWhatsAppGroup.kt:33-70`: revalida vínculo ativo/habilitado/não-quebrado (`:45`), gating REMINDER (`:49`), `groupInfo` + `isMember` (`:53-58`), `NotInGroup`→`breakBinding` e transitório→`scheduleRetry` (`:59-62`), envio e classificação (`:64-68`).
- `NotificationWhatsAppGroupIntegrationTest`: NOTICE com prefixo e revalida membership (`:79`); REMINDER com link e sem dado privado (`:92`); `NotInGroup` quebra vínculo e cancela todos os pendentes (`:109`); instância ausente de Participants quebra (`:120`); falha transitória faz retry sem quebrar (`:129`); desabilitado cancela (`:139`); vínculo removido cancela (`:147`); backoff e esgotamento em 10 (`:155`); falha permanente → FAILED (`:173`); REMINDER expirado cancela sem perder notificação no app (`:181`).
- `UazapiGroupDirectoryTest` — **12 testes, 0 falhas**: `inviteInfo` desembrulha `{"group":...}` e normaliza PhoneNumber JID (`:20`); groupInfo sem wrapper (`:35`); **500 "that group does not exist" → NotInGroup e nunca retry** (`:48`); 404→NotInGroup (`:60`); 5xx transitório→Unavailable (`:71`); 4xx→InvalidInvite (`:82`); join vazio + reconfirma (`:93`); join 500→NotInGroup (`:109`); isMember false/true (`:121`,`:136`); instanceStatus dígitos e Disconnected (`:148`,`:159`).
- `UazapiGroupNotificationSenderTest` — **5 testes, 0 falhas**: `/send/text` ao JID com `track_id group-<id>`, sem regex de telefone, Retry-After, classificação permanente/transitório, falha de rede.

### AC5 — Cobrança DM preservada (PASS)

- `NotificationChannelsIntegrationTest` — **9 testes, 0 falhas** (T4 reescrito): NOTICE nunca usa DM mesmo com opt-in e não alcança autor (`:39`); **CHARGE enfileira DM, independente do push, replay cria 1 job** (`:56`); falha de CHARGE não perde inbox nem bloqueia push e respeita Retry-After (`:76`); pendente cancelada em opt-out/saída/troca de telefone (`:95`); telefone ausente e falhas permanentes/esgotadas não entram em loop (`:112`); REMINDER não enfileira DM (`:128`); CHARGE privada e cancelada cancela pendente (`:139`); CHAT push sem WhatsApp (`:151`); presença só push + link (`:166`).
- `V75__whatsapp_dm_charge_only.sql:10` restringe `whatsapp_enabled` a CHARGE; push inalterado (`:6-9`).
- `UazapiNotificationSenderTest` (DM) — 4 testes, 0 falhas. **Nota baixa:** AC5 diz "adaptado", mas o arquivo não mudou no range (comportamento DM de cobrança inalterado, então não exigiu adaptação). Ver Gaps.

### AC6 — Privacidade (PASS)

- Corpo do grupo montado só a partir de `{group_name}` + corpo da mensagem (`JdbcNotificationWhatsAppGroup.kt:105-110`); trigger restringe a NOTICE/REMINDER (`V76:18`), então CHARGE (único com valor) nunca vai ao grupo.
- `NotificationWhatsAppGroupIntegrationTest.kt:104-106` afirma que o corpo REMINDER não contém `INSTANCE_JID`, nem `@s.whatsapp.net`, nem `R$`; `:100-102` afirma prefixo exato + link da mensagem.
- Central e push individuais inalterados: `NotificationChannelsIntegrationTest.kt:39-53` (avisos só inbox+push), `:51-52` corpos de push inalterados.

### AC7 — Mobile (PASS)

- `CommunicationScreenTest` — **5 testes, 0 falhas**: `whatsappPreferencesKeepOnlyTheChargeSwitchAndPreserveServerValues` (`:45`) afirma que `preferences-whatsapp-notices` e `preferences-whatsapp-reminders` **não existem**, que o switch de cobrança existe e que o payload salvo **preserva os valores de notices/reminders** recebidos; `preferences-whatsapp-messages` ausente.
- `NotificationCenterRoot.kt` diff removeu os dois switches de notices/reminders mantendo o de charges.
- `WhatsAppBindingViewModelTest` — **10 testes, 0 falhas**: load ACTIVE/DISABLED/BROKEN/NONE, erro de carga, link em branco ignorado, confirmação do nome do grupo retornado, falha de link preservando o invite, desabilitar/reabilitar, toggle ignorado sem vínculo, BROKEN retomável.
- `WhatsAppBindingScreenTest` — **8 testes, 0 falhas**: loading, erro com retry, form vazio desabilitado, link preenchido emite intent, ACTIVE/DISABLED/BROKEN toggle, confirmação por nome (`:69`).
- `KtorGroupWhatsAppGatewayTest` — **7 testes, 0 falhas**: GET/PUT/PATCH autenticados e rota correta, NONE sem identidade, mapeamento 403/404/409/502/422, fieldErrors preservados, payload malformado = InvalidResponse.

---

## 3. Sensor de discriminação (mutação)

Cópia isolada: `git archive ccfe2abf backend mobile > /tmp/verificacao-wgc.tar`, extraído em `/tmp/verificacao-wgc`. Todas as mutações aplicadas **somente na cópia**; árvore do repo intacta (`git status --porcelain` vazio). Cada teste alvo rodado via Gradle na cópia; mutação descartada após o teste.

| Mutação | Injeção | Teste alvo | Resultado |
|---|---|---|---|
| **M1** | `V76`: removida a condição `AND NEW.channel IN ('NOTICE','REMINDER')` do trigger | `NotificationWhatsAppGroupIntegrationTest.chat and charge never enqueue a group job` | **MORTA** — `AssertionFailedError` em `NotificationWhatsAppGroupIntegrationTest.kt:65` |
| **M2** | `JdbcNotificationWhatsAppGroup`: `if (!directory.isMember(...))` → `if (false)` (membership sempre satisfeita) | `...instance missing from participants breaks the binding` | **MORTA** — `IllegalStateException` em `NotificationWhatsAppGroupIntegrationTest.kt:124` |
| **M3** | `UazapiGroupDirectory`: `missingGroup(...)` → `false` (500 "does not exist" vira retry/Unavailable) | `UazapiGroupDirectoryTest.a 500 that group does not exist becomes NotInGroup and never retries` | **MORTA** — 2 falhas: `UazapiGroupDirectoryTest.kt:53` e `:114` (join) |
| **M4** | `LinkGroupWhatsApp`: `if (invite.admins.none { ... })` → `if (false)` (pula anti-sequestro) | `LinkGroupWhatsAppTest.no whatsapp admin matches an active saqz member` | **MORTA** — 2 falhas: `LinkGroupWhatsAppTest.kt:112` e `:124` (convite sem admin) |

**4/4 mutações mortas. Nenhum gap de sensor (nenhuma mutação sobrevivente).**

---

## 4. Gaps ranqueados

1. **[Média] Re-vínculo não cancela jobs pendentes do vínculo anterior.** A spec ("Decisões confirmadas", `spec.md:17-18`) exige: colar novo link substitui o vínculo **e cancela jobs de grupo pendentes do vínculo anterior**. `JdbcGroupWhatsAppBindingRepository.upsert` (`JdbcGroupWhatsAppBindingRepository.kt:28-39`) apenas faz `ON CONFLICT (group_id) DO UPDATE`, sem tocar a fila. Como o `group_id` é o mesmo, os jobs pendentes continuam `PENDING` e, na próxima rodada do worker, são entregues ao **novo** JID (`JdbcNotificationWhatsAppGroup.kt:44-68`) — em vez de `CANCELLED`. Não há teste cobrindo cancelamento no re-vínculo. (A task T1 descrevia "upsert substituindo + cancelando jobs pendentes do vínculo anterior".)
2. **[Baixa] AC5 cita `UazapiNotificationSenderTest` "adaptado", mas o arquivo não mudou no range.** Está verde (4 testes) e o fluxo DM de cobrança é idêntico, então a adaptação não foi necessária; é lacuna de rastreabilidade entre a redação do AC e o diff, sem impacto funcional.
3. **[Baixa / baseline, não do range] Gate mobile de `:features:groups:presentation` não está totalmente verde:** 2 falhas pré-existentes em `HomeViewModelTest` e `GroupDetailsScreenTest` por divergência de string "reserva" × "lista de espera" (`strings_home.xml`, `strings_game_response.xml`), em arquivos fora do range. Não afeta AC7 (todas as suítes alvo passam), mas mantém o gate do módulo vermelho para quem rodar a suíte completa.

---

## 5. Resumo

| AC | Veredito | Evidência principal |
|---|---|---|
| AC1 | **PASS** | LinkGroupWhatsAppTest 15 / Endpoint 10 / repo 9 — 0 falhas |
| AC2 | **PASS** | ManageBinding 8 / repo 9 / members 3 — 0 falhas |
| AC3 | **PASS** | V76 trigger + NotificationWhatsAppGroupIntegrationTest 13 (replay/CHAT/CHARGE) |
| AC4 | **PASS** | NotificationWhatsAppGroupIntegrationTest 13 + UazapiGroupDirectoryTest 12 + sender 5 |
| AC5 | **PASS** | NotificationChannelsIntegrationTest 9 + UazapiNotificationSenderTest 4 |
| AC6 | **PASS** | JdbcNotificationWhatsAppGroup.kt:105-110 + asserts de corpo sem telefone/JID/R$ |
| AC7 | **PASS** | CommunicationScreenTest 5 + WhatsAppBindingViewModel 10 + Screen 8 + KtorGateway 7 |

Sensor de discriminação: **4/4 mortas**. Veredito global: **PASS** (1 gap de médio — cancelamento de pendentes no re-vínculo — e 2 notas baixas).
