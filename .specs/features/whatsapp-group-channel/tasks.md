# Canal de grupo de WhatsApp — Tasks

**Spec**: `spec.md` · **Design**: `design.md` (contratos congelados — não redesenhar)
**Status**: Approved
**Protocolo**: executar com a skill `tlc-spec-driven` ativa. Um commit atômico por task.

## Test Coverage Matrix

> Guidelines: `mobile/AGENTS.md` (KMP), padrões existentes em `backend/features/groups/src/{test,integrationTest}` e `backend/bootstrap/src/test`.

| Camada | Tipo | Expectativa | Local | Comando |
|---|---|---|---|---|
| Use case / domínio backend | unit | todos os ramos; 1:1 com AC1–2 | `backend/features/groups/src/test` | `cd backend && ./gradlew :features:groups:test` |
| Fila/repository backend | integration (Postgres) | enfileiramento, dedup, estados, revalidação, quebra | `backend/features/groups/src/integrationTest` | `cd backend && ./gradlew :features:groups:integrationTest` |
| Controller backend | HTTP integration | rotas: happy + cada erro do contrato | `backend/bootstrap/src/test` | `cd backend && ./gradlew :bootstrap:test --tests '*WhatsApp*'` |
| Adapter SDK | unit (HttpServer local) | paths, payload, classificação de erro, parse | `backend/features/groups/src/test` | `cd backend && ./gradlew :features:groups:test` |
| Gateway mobile | unit (MockEngine) | roundtrip DTO + mapeamento de erro | `mobile/features/groups/data/src/commonTest` | `cd mobile && ./gradlew :features:groups:data:iosSimulatorArm64Test` |
| ViewModel mobile | unit (fake) | estados + effects | `mobile/features/groups/presentation/src/commonTest` | `cd mobile && ./gradlew :features:groups:presentation:iosSimulatorArm64Test` |
| Screen mobile | UI (testTag) | cada estado da tela | `mobile/features/groups/presentation/src/commonTest` | idem acima + `detektAll` |
| Migração SQL | via integrationTest | schema aplica no Postgres descartável | migração em `db/migration` | integrationTest correspondente |

## Gate Check Commands

| Nível | Quando | Comando |
|---|---|---|
| Quick (backend) | toda task backend | `cd backend && ./gradlew :features:groups:test :features:groups:integrationTest` |
| Full (backend) | T2, T3 | quick + `./gradlew :bootstrap:test --tests '*WhatsApp*' :architecture-tests:test` |
| Quick (mobile) | task de domain/data | `cd mobile && ./gradlew :features:groups:data:iosSimulatorArm64Test :features:groups:domain:iosSimulatorArm64Test` |
| Full (mobile) | T6, T7 | quick mobile + `./gradlew detektAll :android-app:testDevDebugUnitTest` |

JAVA_HOME em todos os gates backend: `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

## Lanes paralelas (grafo)

```
T0 (humano, grupo real) ──┐
                          ▼
A: T1 ──────────────────► T2 ──► T3 ─┐
B: T4 ───────────────────────────────┤
C: T5 ──────────────────► T6 ────────┤──► T8 (integração + docs + verificação)
D: T7 ───────────────────────────────┘
```

- **Lanes B, C e D começam imediatamente** (não dependem de T0 nem de T1).
- **T2/T3 só começam com T0 aprovado** (parse real validado) — lane A pode executar T1 antes.
- Lanes A–D são ownership exclusivo de arquivos (ver coluna `Where`): nenhuma lane edita arquivo de outra.

---

## T0: Homologação U1–U5 (humano + assistente, token real)

**O que**: criar grupo de teste no WhatsApp, convidar a instância; executar `join`,
`/group/list`, `/group/info` (dentro e fora do grupo), `sendText` no grupo (com e sem
`IsAnnounce`), join com `IsJoinApprovalRequired=true`. Registrar respostas brutas.
**Where**: `.specs/features/whatsapp-group-channel/evidence.md`
**Depends on**: nada (externo)
**Done when**: U1–U5 respondidos com payload real; formato dos `Participants[].PhoneNumber` e
`Owner*` documentado. **Gate**: revisão do usuário.

---

## T1: Schema + repository de vínculo

**What**: V73 + modelo `GroupWhatsAppBinding` + port + `JdbcGroupWhatsAppBindingRepository`
(find, upsert substituindo + cancelando jobs pendentes do vínculo anterior, setEnabled,
markBroken).
**Where**: `backend/features/groups/src/main/resources/db/migration/V73__group_whatsapp_bindings.sql`,
`application/whatsapp/`, `adapter/output/jdbc/whatsapp/`
**Depends on**: nada · **Requirement**: AC1–2 · **Tests**: integration (schema + repository)
**Gate**: quick backend

## T2: Directory SDK + vínculo + endpoints

**What**: `WhatsAppGroupDirectory` + `UazapiGroupDirectory` (parse JsonNode conforme T0),
`LinkGroupWhatsApp`, `ManageGroupWhatsAppBinding`, `GroupWhatsAppBindingController`, wiring em
`NotificationWhatsAppConfiguration`.
**Where**: `adapter/output/whatsapp/UazapiGroupDirectory.kt`, `application/whatsapp/`,
`adapter/input/http/GroupWhatsAppBindingController.kt`, `bootstrap/.../NotificationWhatsAppConfiguration.kt`
**Depends on**: T0, T1 · **Requirement**: AC1, AC2, AC8
**Tests**: unit (use case, fakes de directory), unit (directory contra HttpServer local com JSON
do T0), HTTP integration (bootstrap, todas as rotas e erros do contrato)
**Gate**: full backend

## T3: Fila de grupo + worker

**What**: V76 (tabela + trigger), `NotificationWhatsAppGroupSender` + `UazapiGroupNotificationSender`,
`JdbcNotificationWhatsAppGroup` com o algoritmo do design, beans do worker.
**Where**: `db/migration/V76__notification_whatsapp_group_queue.sql` (V74 fica vago — V75 já aplicada no dev, Flyway `outOfOrder=false`),
`application/communication/NotificationWhatsAppGroup.kt` (novo), `adapter/output/whatsapp/UazapiGroupNotificationSender.kt`,
`adapter/output/jdbc/communication/JdbcNotificationWhatsAppGroup.kt`, `NotificationWhatsAppConfiguration.kt`
**Depends on**: T0, T1, T2 · **Requirement**: AC3, AC4, AC6
**Tests**: **arquivo novo** `NotificationWhatsAppGroupIntegrationTest.kt` (trigger 1 job/mensagem,
replay, estados, revalidação, quebra, cancelamento ao desabilitar) + unit do sender de grupo
**Gate**: full backend

## T4: DM vira CHARGE-only

**What**: V75 (view `whatsapp_enabled` só CHARGE); adaptar testes existentes do canal DM de
avisos/presença para afirmar que **não** enfileiram mais; testes de cobrança intactos.
**Where**: `db/migration/V75__whatsapp_dm_charge_only.sql`,
`NotificationChannelsIntegrationTest.kt` (edição — **só esta lane toca este arquivo**)
**Depends on**: nada · **Requirement**: AC3, AC5
**Done when**: `NotificationChannelsIntegrationTest` verde com notices/reminders sem fila DM e
charge com fila DM; push inalterado.
**Gate**: quick backend

## T5: Gateway mobile do vínculo

**What**: `GroupWhatsAppGateway` (domain) + `KtorGroupWhatsAppGateway` (data) com os 3 métodos do
contrato + DTOs + módulo Koin data.
**Where**: `mobile/features/groups/domain/.../communication/GroupWhatsAppGateway.kt`,
`mobile/features/groups/data/.../communication/KtorGroupWhatsAppGateway.kt`, módulo data
**Depends on**: nada (contrato do design.md) · **Requirement**: AC2, AC7
**Tests**: unit MockEngine (bound true/false, status, erros 403/404/409/422)
**Gate**: quick mobile

## T6: Tela de configurações avançadas do gestor

**What**: rota `GroupsRoute.WhatsApp(groupId)` + entrada na tela Edit, `WhatsAppBinding{Root,Screen,ViewModel,Contract}` + módulo DI + registro em `SaqzNavHost`/`saqzLocalNavConfiguration`. Fluxo: colar link → mostrar nome do grupo retornado → confirmar → estado + desabilitar/reabilitar.
**Where**: `mobile/features/groups/presentation/.../whatsappbinding/`, `navigation/GroupsRoute.kt`,
`strings_whatsapp_binding.xml` (novo), `compose-app` (nav/DI)
**Depends on**: T5 · **Requirement**: AC2, AC7
**Tests**: ViewModel (estados), Screen (testTag: loading/erro/salvo/desabilitado/quebrado)
**Gate**: full mobile

## T7: Remover switches de avisos/presença do usuário

**What**: `NotificationCenterRoot` remove switches whatsapp-notices/whatsapp-reminders, mantém
whatsapp-charges; savePreferences envia payload completo preservando valores recebidos; ajustar
`CommunicationScreenTest`.
**Where**: `mobile/features/groups/presentation/.../communication/NotificationCenterRoot.kt` +
testes de tela (**só esta lane toca esses arquivos**)
**Depends on**: nada · **Requirement**: AC7
**Gate**: full mobile

## T8: Integração, docs e validação independente

**What**: subir stack local com whatsapp habilitado, smoke do fluxo ponta a ponta com fakes,
`operation.md`, verificação independente (verificador ≠ autor) de AC1–AC7 com sensor de mutação.
**Where**: `docs/notifications/whatsapp-group.md` (novo — docs/ é gitignored, duplicar em `.specs`), `validation.md`
**Depends on**: T1–T7 · **Requirement**: todos
**Gate**: full backend + full mobile

---

## Diagram-Definition Cross-Check

| Task | Depends on | Diagrama | Status |
|---|---|---|---|
| T1 | — | A inicia | ✅ |
| T2 | T0, T1 | T0→T2, T1→T2 | ✅ |
| T3 | T0, T1, T2 | T2→T3 | ✅ |
| T4 | — | B | ✅ |
| T5 | — | C inicia | ✅ |
| T6 | T5 | T5→T6 | ✅ |
| T7 | — | D | ✅ |
| T8 | T1–T7 | todas→T8 | ✅ |

## Test Co-location

| Task | Camada | Matrix exige | Status |
|---|---|---|---|
| T1 | repository/migração | integration | ✅ |
| T2 | use case + adapter + controller | unit + HTTP | ✅ |
| T3 | fila/worker + adapter | integration + unit | ✅ |
| T4 | view/testes | integration | ✅ |
| T5 | gateway data | unit | ✅ |
| T6 | ViewModel + Screen | unit + UI | ✅ |
| T7 | Screen | UI | ✅ |
| T8 | docs/validação | n/a (gates completos) | ✅ |

## Regras de ownership (anti-colisão entre lanes)

  - **Lane A** (T1–T3): backend `whatsapp/`, V73/V76, `NotificationWhatsAppConfiguration.kt`,
  `NotificationWhatsAppGroupIntegrationTest.kt` (novo).
- **Lane B** (T4): V75 + `NotificationChannelsIntegrationTest.kt` (único lane que edita).
- **Lane C** (T5–T6): `communication/GroupWhatsAppGateway*`, `whatsappbinding/`, rotas/nav/strings novos.
- **Lane D** (T7): `NotificationCenterRoot.kt` + seus testes.
- Qualquer necessidade de tocar arquivo de outra lane → parar e alinhar, nunca editar.
