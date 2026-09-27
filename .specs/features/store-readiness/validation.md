# Validação independente — preparação das lojas

Data: 2026-09-27. Verificador independente, distinto do autor.

**Veredito do diff `b484dac1192fb08aa707bca8848ccb283979b30c..42f2f3c0`: FAIL.**
Seis dos oito critérios têm evidência local satisfatória; SR4 falha e SR5 tem uma lacuna de integração. Há uma lacuna de precisão no SR4. Aprovação/publicação não foi realizada.

O checkout estava limpo em `42f2f3c0` quando os gates começaram. O autor iniciou correções após receber os achados; essas alterações posteriores **não estão aprovadas por este relatório inicial**. Código e testes do checkout real não foram alterados pelo verificador. A única escrita de fonte no checkout feita pelo verificador é este arquivo.

## Achados priorizados e tarefas de correção

### F1 — SR4: histórico de presença impede a exclusão (P1)

`backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/DeletedAccountPersonalData.kt:32`, no commit verificado, executa `UPDATE attendance_events SET reason = NULL`. A tabela tem o trigger `attendance_events_append_only` de `backend/features/groups/src/main/resources/db/migration/V6__add_game_attendance.sql:108`, que rejeita toda atualização. A restrição `ck_attendance_events_organizer_reason`, na linha 94, também exige motivo não nulo para eventos ORGANIZER.

Reprodução independente com PostgreSQL real: criar usuário, grupo, jogo e um evento ORGANIZER; executar a mesma transação de soft-delete/limpeza. Resultado: SQLSTATE `P0001`, `attendance events are append only`; a transação de exclusão é revertida. O teste novo de persistência não semeia eventos de presença, portanto não detecta a falha.

- **Corrigir:** permitir exclusivamente a redação controlada dos campos pessoais, preservando a imutabilidade do restante do histórico e as constraints.
- **Verificar:** excluir uma conta com eventos SELF e ORGANIZER pela composição real; afirmar sucesso, remoção do motivo pessoal e manutenção dos campos históricos. Atualizações arbitrárias e DELETE do histórico devem continuar recusados.
- **Evidência independente:** scratch `VerifierDeletionProbeTest.kt:43` e `/tmp/store-verifier-probes.log`.

### F2 — SR4: bootstrap concorrente recria dados da conta excluída (P1)

`backend/features/access/src/main/kotlin/br/com/saqz/access/adapter/output/jdbc/session/JdbcSessionRepository.kt:47` consulta a tombstone antes do UPSERT. O trigger de `V89__account_deletion_requests.sql:17` não serializa operações pelo subject.

Reprodução determinística: uma transação mantém `FOR UPDATE` sobre o usuário; outro thread começa `upsertAndLoad`, passa pela consulta de exclusão e aguarda o lock; a primeira transação grava a solicitação, faz soft-delete e confirma; o UPSERT já iniciado termina com uma nova linha ativa contendo o mesmo UID e dados pessoais. O trigger BEFORE INSERT tinha visto o snapshot anterior à confirmação.

O probe confirma que o UPSERT entrou em espera em `pg_stat_activity`; usa `pg_stat_clear_snapshot()` durante a sincronização. Depois do commit, `assertEquals(0, count(active users for subject))` recebe **1**. Isto não é uma hipótese de concorrência nem um timeout de teste.

- **Corrigir:** serializar bootstrap e exclusão pela identidade, mantendo a checagem de tombstone depois da aquisição da barreira. Cobrir todas as vias de criação/atualização relevantes.
- **Verificar:** reproduzir a ordem acima com duas conexões e confirmar zero linhas ativas, inclusive após concluir o job externo.
- **Evidência independente:** scratch `VerifierDeletionProbeTest.kt:61`, assertion na linha 84; `/tmp/store-verifier-concurrency-probe.log`.

### F3 — SR4: cópias de endereço e notas pessoais permanecem (P1)

`DeletedAccountPersonalData.kt:34` redige `group_venues`, mas não os snapshots de `games` e `game_series_slots`. Essas tabelas guardam título, nome/endereço do local, quadra e notas independentes da linha do local.

Reprodução: excluir um dono com jogo contendo `venue_address='Private Street 123'` e telefone pessoal em `notes`. A exclusão termina, mas ambos os campos permanecem íntegros. A prova independente falha em `assertFalse(row.first.contains("Private Street"))`. O grupo fica inacessível; isso não equivale à remoção dos dados pessoais prometida por SR4 e pela página pública.

- **Corrigir:** redigir os snapshots pessoais dos grupos encerrados sem apagar valores e estados financeiros sujeitos a retenção.
- **Verificar:** semear jogo e série recorrente com dados pessoais e confirmar redação dos snapshots e preservação do histórico necessário.
- **Evidência independente:** scratch `VerifierDeletionProbeTest.kt:51`, assertion na linha 58; `/tmp/store-verifier-probes.log`.

### F4 — gate backend: nova composição quebra contextos sem assinatura (P1)

`AccountDeletionConfiguration.kt:29` exige `CancelSubscription` incondicionalmente. Contextos válidos sem configuração de assinaturas não têm esse bean. O gate completo de bootstrap falha em 63 testes por `NoSuchBeanDefinitionException`, além da falha F5.

- **Corrigir:** respeitar a disponibilidade da configuração sem permitir que uma assinatura ativa seja ignorada. Se houver cobrança pendente e o provedor estiver indisponível, manter o job pendente; não remover a identidade nem declarar conclusão.
- **Verificar:** contextos com e sem configuração de assinaturas sobem; cobrir usuário sem assinatura, assinatura já cancelada, assinatura ativa com provedor ausente e falha recuperável.
- **Evidência:** `backend/bootstrap/build/test-results/test/TEST-br.com.saqz.bootstrap.EmailVerificationEndpointIntegrationTest.xml:28`, cópia preservada em `/tmp/store-verifier-original-results/backend/bootstrap/test/`.

### F5 — gate backend: teste antigo contradiz a decisão de exclusão (P2)

`backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/DeleteAccountIntegrationTest.kt:71` ainda exige o nome público original; linhas 83–91 ainda exigem recriação com o mesmo UID. O helper do teste também reimplementa a composição antiga, sem `removeDeletedAccountPersonalData`.

- **Corrigir:** testar a composição real e atualizar as expectativas para anonimização e bloqueio do UID excluído. Manter a cobertura de histórico de terceiros e de novo cadastro com identidade nova.
- **Verificar:** o teste passa com resultados especificados em SR4, sem remover o cenário ou enfraquecer suas assertions.

### F6 — SR4: adapters reais não são discriminados pelos testes (P1 de cobertura)

O sensor removeu `FirebaseAuth.deleteUser(subject)` do adapter real e todos os testes selecionados continuaram passando. Também substituiu o purge de token/últimos dígitos/bandeira por autoatribuição, com o mesmo resultado. Os testes de `CompleteAccountDeletion` verificam um fake, mas não comprovam a ligação real Firebase/Asaas/SQL.

- **Corrigir:** adicionar testes do adapter real que observem o subject, `revokeRefreshTokens`, `deleteUser`, idempotência USER_NOT_FOUND e propagação das demais falhas. Exercitar o cancelamento real do caso de uso com fake do gateway e persistência PostgreSQL, verificando token, last4 e brand nulos.
- **Verificar:** matar M5 e M6, além de preservar os quatro mutantes já mortos.

### F7 — SR5: encerramento da sessão só é comprovado até o efeito (P2 de cobertura)

`AccountDeletionViewModelTest.kt:38` afirma `AccountDeletionEffect.DELETED`, mas nenhum teste do diff atravessa `AccountDeletionRoot` → `onDeletionComplete` → `AccessIntent.ConfirmLogout` e afirma sessão local encerrada/stack limpo. `SaqzNavHostTest` não exercita a rota DeleteAccount. A ligação de fonte parece correta; não foi observado defeito funcional nesse trecho.

- **Corrigir:** acrescentar um teste de integração da composição de exclusão e sessão.
- **Verificar:** após 204, ocorre sign-out e a navegação autenticada desaparece; em cancelamento/falha de reautenticação ou erro da API isso não ocorre.

## Critérios ancorados no spec

As referências abaixo usam as linhas do commit `42f2f3c0`, antes dos fixes. Assertions em fakes não são tratadas como homologação de provedores externos.

| AC | Resultado exigido | Evidência `arquivo:linha` + assertion | Resultado |
|---|---|---|---|
| SR1 — Crashlytics | Recurso real de build ID empacotado, não vazio | `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/CrashlyticsBuildIdTest.kt:20` — `assertNotNull(..., buildId)`; `:21` — `assertFalse(buildId.isNullOrBlank())`, lendo `CommonUtils.getMappingFileId(context)` | PASS local |
| SR1 — dev/prod | Dev sem credenciais usa emulador; prod sem Firebase não compila | `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/FirebaseAuthBootstrapTest.kt:26` — `assertEquals(listOf("create", "emulator:10.0.2.2:9099"), calls)`; `/tmp/store-verifier-production-gate.py:7` — `assert result.returncode!=0` e mensagem específica de configuração ausente | PASS local; credenciais reais externas |
| SR2 — Firebase iOS | Release rejeita arquivo ausente/inválido e identidade errada | `mobile/ios-app/tests/firebase-config.test.cjs:40`, `:46`, `:52`, `:59`, `:65` — `assert.equal(result.status, 1)` mais erro correspondente | PASS |
| SR2 — Debug/identidade/API | Só Debug tem fallback; Release usa identidade/API esperadas | Mesmo arquivo `:85` — status 0 e remoção do plist obsoleto; `:95`/`:96` — DEBUG presente/ausente; `:114` — status exato por URL; `:117` — `SaqzAPIBaseURL === 'https://api.saqz.app'`; `mobile/ios-app/SaqzIOSTests/SaqzIOSTests.swift:37` — sequência configure/emulator/root | PASS local |
| SR3 — Apple/nonce | Nonce aleatório com hash no pedido e raw nonce no Firebase | `mobile/ios-app/SaqzIOSTests/IOSAuthAdapterTests.swift:364`/`:365` — tokens/nonce exatos; `:394` — hash SHA256 exato, `:395`/`:396` — nonce distinto/raw distinto; `:397` — 64 caracteres | PASS local |
| SR3 — sessão/falhas | Mesmo fluxo autenticado; cancelar/falhar não autentica; Google/senha continuam | `mobile/features/access/src/commonTest/kotlin/br/com/saqz/access/presentation/AuthenticationCoordinatorTest.kt:120` — `AuthTransition.Authenticated(verifiedUser)`; `:129`/`:139` — transitions vazio; Swift `:352`/`:377` — ausência de efeito Firebase; `:123`/`:169`/`:170` — senha/Google e subject corretos | PASS local; consoles externos |
| SR4 — auth recente | Recusa auth antiga/ausente/futura e identidade trocada | `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/SessionEndpointIntegrationTest.kt:127`/`:128`/`:129` — 403, `RECENT_AUTHENTICATION_REQUIRED`, nenhuma exclusão; `AccountDeletionPersistenceTest.kt:102` — `assertThrows<AccountDeletionIdentityMismatch>` | PASS da implementação de 300 s; precisão abaixo |
| SR4 — cancelamento/identidade | Cancela cobrança antes de apagar Firebase, preserva retry em falha | `backend/features/access/src/test/kotlin/br/com/saqz/access/application/session/CompleteAccountDeletionTest.kt:19` — ordem e payload `subscription:$userId`, `identity:firebase-subject`; `:31`/`:32` — não concluído/retry +60 s; `HttpAsaasGatewayTest.kt:478` — status 503 preservado | GAP: M5/M6 mostram ausência de prova do adapter real |
| SR4 — dados/vínculos/retenção | Remove dados pessoais e vínculos, mantém valores financeiros anonimizados | `AccountDeletionPersistenceTest.kt:54`/`:55` — nome neutro, email/phone nulos; `:57`/`:59` — conteúdo/vínculos ausentes; `:61`/`:62`/`:63` — nome neutro, 4500 e PAID preservados | FAIL: F1/F3; teste não semeia todos os dados que o schema permite |
| SR4 — retomada/recriação | Job durável, lease e tentativa correta; UID excluído não reaparece | `AccountDeletionPersistenceTest.kt:82`/`:84`/`:86` — claim vazio durante lease/antes do retry, tentativa 2; `:89`/`:91` — completion antigo não conclui, atual conclui/redige; `:65` — `assertThrows<AccountDeleted>` | FAIL: caminho sequencial passa, corrida F2 falha |
| SR5 — confirmação/reauth/erro | Sem consentimento não exclui; identidade correta, cancelamento e falha recuperáveis | `mobile/features/profile/presentation/src/commonTest/kotlin/br/com/saqz/profile/presentation/deletion/AccountDeletionViewModelTest.kt:24`, `:35`, `:47`, `:50`, `:60`, `:63` — não excluído, id exato, erro exato, retry conclui; `AccountDeletionScreenTest.kt:13`/`:14` — botões desabilitados; binding `AccountDeletionAuthorizationBindingTest.kt:15` — fresh-token antes de revoke | PASS da tela/VM/binding |
| SR5 — API/logout/página | DELETE da conta exibida, sessão termina após sucesso, solicitação pública disponível | `mobile/features/profile/data/src/commonTest/kotlin/br/com/saqz/profile/data/KtorProfileGatewayTest.kt:149`–`:158` — DELETE, caminho/header/id/204/repetição; VM `:38` — efeito DELETED; `landing-page/tests/account-deletion.test.cjs:9`–`:15` — passos, mailto, identidade, retenção e canonical | PARCIAL: logout integrado sem assertion, F7 |
| SR6 — compra e assinatura web | Nenhum CTA/link de compra; assinatura existente autoriza | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/subscriptiongate/SubscriptionGateScreenTest.kt:38` — Request ausente; `SubscriptionGateViewModelTest.kt:50` — zero envios; `:55`/`:56` — Authorized/AuthorizationGranted; `MyPlanTrialScreenTest.kt:29` — Subscribe ausente | PASS |
| SR6 — chat/recebimentos | Entradas e rotas restauradas indisponíveis; avisos preservados | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/StoreLaunchNavigationTest.kt:19` — só Home sobrevive ao conjunto de rotas bloqueadas; `:24` — avisos/plano/exclusão permitidos; `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/ReceivablesLaunchConfigurationTest.kt:21` — `ReceivablesAvailability(false,false,maintenanceAvailable=true)` mesmo com rollout prévio ALL_USERS | PASS |
| SR7 — declarações e permissões | Dados/finalidades/exclusão/retenção declarados, sem publicidade desnecessária | `mobile/ios-app/tests/privacy-config.test.cjs:11`/`:12` — tracking false/domínios vazios; `:18`–`:21` — tipos, vínculo, tracking e finalidade presentes; `StorePrivacyPermissionsTest.kt:20`/`:21`/`:25` — permissões ausentes/metadados false; `landing-page/tests/privacy.test.cjs:41`–`:47` — meios de login, ausência de chat/API, exclusão | PASS de configuração/texto; efetividade da exclusão depende de corrigir SR4 |
| SR8 — associações/links | Identidade iOS correta, validação Android sem fingerprint inventado, fallbacks seguros | `links-page/tests/app-links.test.cjs:54`–`:58` — `8JG4JP8VMT.app.saqz` e paths; `:22`/`:24` — rejeita debug/ausente/inválido; `:45`–`:49` — CLI não sobrescreve inválido, grava/verifica válido; `fallback.test.cjs:23`/`:37`/`:44` — intent Android exato, URI iOS, payload inválido sem destino | PASS local; certificado Play pendente externo |

**Precisão do spec:** SR4 não define o intervalo numérico de “autenticação recente” nem uma matriz de classes/prazos de retenção. Os testes demonstram a decisão implementada (300 segundos), não uma janela definida pelo spec. A política usa “prazo aplicável”; este relatório não a transforma em prazo quantificado ou prova jurídica de retenção. Falta igualmente uma assertion de rota demonstrando a restrição de acesso a todo o histórico retido; o teste de persistência demonstra valores e anonimização de uma cobrança.

## Gates executados independentemente

Ambiente: macOS, JDK 21 em `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`, Android SDK local, PostgreSQL Zonky embutido; sem Docker. O bloco Gate Check Commands de tasks.md descreve tipos de gate sem comandos literais; foram concretizados assim:

| Gate | Comando/seleção | Resultado |
|---|---|---|
| Backend amplo | `backend/gradlew -p backend :features:access:test :features:access:integrationTest :features:subscriptions:test :bootstrap:test` | **968/1032 passaram; 64 falharam; 0 pulados**. Exit 1. Access 147/147; integração Access 117/117; subscriptions 257/257; bootstrap 447/511 |
| Backend focado, scratch original | `:features:access:test --tests '*CompleteAccountDeletionTest' --tests '*DeleteAccountTest' :features:subscriptions:test --tests '*HttpAsaasGatewayTest' :bootstrap:test --tests '*AccountDeletionPersistenceTest' --tests '*ReceivablesLaunchConfigurationTest' --tests '*SessionEndpointIntegrationTest'` | **84/84**, zero falhas/skips antes do sensor. Exercita camadas compiladas, PostgreSQL e HTTP local |
| Android e qualidade | `mobile/gradlew -p mobile :android-app:testDevDebugUnitTest detektAll` | **231/231**, zero falhas/skips; detekt PASS |
| KMP iOS | `iosSimulatorArm64Test` nos módulos access, profile:data, profile:presentation, groups:presentation, subscriptions:presentation e compose-app, filtros listados abaixo | **221/221**, zero falhas/skips. UI commonTest não foi rodada na JVM |
| Swift Apple | `xcodebuild ... -scheme SaqzDev -destination 'platform=iOS Simulator,id=A23D655E-A90A-4B35-85D0-567EED77C7FA' -only-testing:SaqzIOSTests/IOSAuthAdapterTests CODE_SIGNING_ALLOWED=NO test` | **40/40**, zero falhas; build Swift/Kotlin PASS |
| Swift bootstrap | Mesmo destino, `-only-testing:SaqzIOSTests/SaqzIOSTests test-without-building` | **5/5**, zero falhas |
| Web/configuração nativa | `node --test landing-page/tests/*.test.cjs links-page/tests/*.test.cjs mobile/ios-app/tests/*.test.cjs` | **50/50**, zero falhas/skips |
| Build negativo Android prod | Scratch sem Google Services: `:android-app:assembleProdRelease -Psaqz.api.prodBaseUrl=https://api.saqz.app` | PASS do contrato: exit 1 e `Missing Android Firebase config:` |

Filtros KMP: `AuthenticationStateMachineTest` (11; o arquivo se chama AuthenticationCoordinatorTest), `SerializedNativeAuthPortTest` (13), `KtorProfileGatewayTest` (13), `AccountDeletion*` (7), `CommunicationViewModelTest`/`GroupDetailsViewModelTest`/`GroupShellBlocksTest` (117), `MyPlanTrialScreenTest` (5), `SaqzNavHostTest`/`StoreLaunchNavigationTest`/`AccountDeletionAuthorizationBindingTest`/`SubscriptionGate*Test` (55). A primeira invocação usou o nome de arquivo do coordenador; a conferência do XML identificou a omissão e uma execução adicional pelo nome real da classe comprovou os 11 testes.

Falhas do bootstrap amplo: AdminCouponAnalytics 7, OrganizerTrialEndpoint 18, TrialCampaignEndpoint 5, PurchaseInformationEndpoint 8, PasswordResetEndpoint 17, EmailVerificationEndpoint 8 (bean ausente), DeleteAccountIntegration 1 (expectativa antiga). Não foram classificadas como problema ambiental.

Logs: `/tmp/store-verifier-backend-gate.log`, `/tmp/store-verifier-mobile-gate.log`, `/tmp/store-verifier-kmp-gate.log`, `/tmp/store-verifier-kmp-auth-gate.log`, `/tmp/store-verifier-swift-gate.log`, `/tmp/store-verifier-swift-bootstrap-gate.log`, `/tmp/store-verifier-node-gate.log`, `/tmp/store-verifier-production-gate.log`. XMLs originais de backend/Android preservados em `/tmp/store-verifier-original-results` antes das correções do autor.

Os testes marcados `emulator` são excluídos pelo alvo bootstrap:test já existente e não foram executados neste gate. Binários assinados e provedores reais não foram homologados. Não há teste silenciosamente pulado nos conjuntos efetivamente executados.

## Sensor de discriminação

Profundidade crítica: **6 mutações comportamentais independentes, 4 mortas e 2 sobreviventes**. Executadas após a baseline focada verde, restaurando o arquivo a cada mutação; nenhuma alteração no checkout real.

Scratch imutável de origem: `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-store-verifier-q77fpo31`, criado com `git archive 42f2f3c0`. Script `/tmp/store-verifier-mutations.py`, resultados `/tmp/store-verifier-mutants.json`.

| Mutante | Local original | Mudança de comportamento | Resultado discriminante |
|---|---|---|---|
| M1 | `CompleteAccountDeletion.kt:27` | Omitir cancelamento de cobrança | **Morto**: sequência esperada subscription/identity passa a conter só identity; teste de falha de billing também reprova |
| M2 | `CompleteAccountDeletion.kt:28` | Omitir exclusão da identidade no caso de uso | **Morto**: sequência sem identity e job erroneamente concluído durante falha do provedor |
| M3 | `CompleteAccountDeletion.kt:31` | Marcar completo ao capturar falha externa | **Morto**: `assertNull(completedAt)` recebe timestamp |
| M4 | `AccessSessionController.kt:172` | Ignorar condição de autenticação recente | **Morto**: teste HTTP esperava 403, recebeu 204 |
| M5 | `AccountDeletionConfiguration.kt:41` | Omitir `auth.deleteUser(subject)` do adapter real | **Sobreviveu**: baseline de CompleteAccountDeletion/AccountDeletionPersistence/SessionEndpoint totalmente verde |
| M6 | `AccountDeletionConfiguration.kt:31` | Preservar token/last4/brand via autoatribuição SQL | **Sobreviveu**: mesma baseline verde |

M1–M3 usam CompleteAccountDeletionTest; M4 usa SessionEndpointIntegrationTest; M5–M6 usam o conjunto indicado. Todos compilam; mortes são assertions de comportamento, não erros sintáticos. O coletor de M4 também encontrou XML antigo de M3, mas a morte atribuída a M4 é exclusivamente o HTTP 403→204 no XML recém-executado.

Os probes adicionais de PostgreSQL não são contados como mutantes: apenas semearam estados/corridas permitidos pelo schema contra código original. F1/F3 foram reproduzidos no log `store-verifier-probes.log`; a primeira tentativa de sincronizar F2 não atingiu o estado pretendido, foi corrigida, e somente a segunda execução determinística fundamenta F2.

## Integridade e rastreabilidade dos testes

Contagem estática independente nos 37 arquivos de teste alterados: **398 → 463 definições (+65), nenhum arquivo com redução**. Métodos Swift, `@Test` Kotlin e `test()` Node foram contados no git, não inferidos de logs filtrados. Inventário completo em `/tmp/store-verifier-test-inventory.json`. Não há novo skip/disable usado para encobrir falha.

As mudanças de assertions de chat/CTAs, anonimização e proibição de recriação correspondem às decisões explícitas SR4/SR6. O teste de exclusão antigo que não acompanhou essa decisão está registrado em F5. Não se recomenda enfraquecer sua cobertura.

| Grupo de testes alterados | Requisito/critério que os justifica |
|---|---|
| CrashlyticsBuildIdTest | SR1, recurso real de build |
| firebase-config.test.cjs, SaqzIOSTests.swift | SR2, configuração/ordem de inicialização |
| IOSAuthAdapterTests, AuthenticationCoordinatorTest, SerializedNativeAuthPortTest | SR3; reautenticação/revogação também SR5 |
| AccountDeletionPersistence, CompleteAccountDeletion, DeleteAccount, SessionEndpoint, JdbcSessionRepositoryIntegration, AccessSchema | SR4, exclusão durável e schema |
| AdminUsersEndpoint, DisposableAccessFlow, EmulatorSession | SR4, fixtures/expectativas de auth recente e conta excluída |
| HttpAsaasGatewayTest | SR4, cancelamento idempotente e propagação de falha |
| Profile gateway, AccountDeletion VM/Screen/Screenshot/Binding, account-deletion.test.cjs | SR5 |
| SaqzNavHost, StoreLaunchNavigation, SubscriptionGate, MyPlanTrial, GroupDetails/GroupShellBlocks/Communication, ReceivablesLaunchConfiguration | SR6 |
| StorePrivacyPermissions, privacy-config, privacy.test.cjs | SR7 |
| app-links/fallback, sitemap | SR8; sitemap de exclusão também SR5 |

Para SR4, a cobertura por camada ainda não atende ao contrato completo: o caso de uso é bem discriminado, mas adapter real, composição com todos os dados pessoais e concorrência não eram. Para SR5, o teste de efeito não substitui a conclusão da jornada autenticada.

## Qualidade e tarefas

Diretriz consultada: `mobile/AGENTS.md` (ports por callback, Koin, commonTest iOS, estado/efeitos, nenhum teste apagado). Spec, tasks, design, diff, testes e migrations foram lidos independentemente.

T1/T2/T3/T6/T7/T8 têm evidência local; T4 está reprovada; T5 tem cobertura parcial de integração. Todos os itens constavam como concluídos em tasks.md no início.

A aprovação final de qualidade permanece **suspensa porque o gate amplo de build/testes falhou**, conforme validate.md. Detekt verde não substitui esse gate. A superfície inclui 133 arquivos, 3.729 adições e 156 remoções; a regra de 2.000 linhas por PR deve ser respeitada quando houver PR, mas nenhum PR foi solicitado/aberto por este verificador.

Não houve UAT humano nem homologação de jornada em dispositivo assinado; os testes Compose e Swift mencionados são automáticos. A aprovação visual de produto não é inferida dessas assertions.

## Limites externos separados dos defeitos

- Certificado público **Play App Signing ainda não fornecido**. O script valida formato, pacote e rejeita o debug conhecido; não pode atestar a procedência de um SHA-256 arbitrário. O assetlinks de produção não está pronto e nenhum fingerprint de teste foi publicado.
- Configurações Apple/Firebase nos consoles, credenciais reais, provedor Apple em produção, Asaas de produção, fichas das lojas, deploy das páginas/associações e teste em binários assinados continuam pendentes.
- A manutenção de fotos/textos/avisos exige análise de UGC pelas lojas, mesmo com chat fechado.
- Esses itens são limites explícitos do spec; **não causam os FAILs F1–F7 nem são tratados como concluídos por fakes**.

## Lições fundamentadas para registrar pelo coordenador

A instrução desta verificação autoriza escrita somente em validation.md; portanto não alterei `.specs/lessons.json`/LESSONS.md via lessons.py. Há sinal relevante e zero lições gravadas pelo script; segue a matéria-prima para o coordenador registrar:

- F1/ac_gap: Testes de exclusão devem semear histórico sujeito a triggers e constraints de imutabilidade.
- F2/ac_gap: Testes de tombstone devem sincronizar operações concorrentes antes do commit de exclusão.
- F3/ac_gap: Inventarie snapshots e cópias desnormalizadas ao provar remoção de dados pessoais.
- F4/gate_fail: Beans de limpeza devem funcionar com dependências opcionais sem transformar indisponibilidade de cobrança em sucesso.
- M5/surviving_mutant: Prove chamadas e payloads no adapter real de exclusão de identidade, além do fake do caso de uso.
- M6/surviving_mutant: Prove em PostgreSQL a remoção de credenciais financeiras após cancelamento.
- F7/ac_gap: Prove encerramento de sessão na composição da jornada, além da emissão de um efeito.
- SR4/spec_precision_gap: Explicite a janela de autenticação recente e as categorias de retenção no contrato aceito.

**Próximo passo:** corrigir os achados e submeter o diff dos fixes a nova verificação, com gate amplo verde, probes de concorrência/dados e M5/M6 mortos. Não atualizar SR4/SR5 para “verificado” antes disso.
