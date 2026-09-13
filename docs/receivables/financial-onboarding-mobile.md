# Cadastro financeiro no mobile

Base 24fd5bc4; continuação autorizada de T04/T14/T15/T17. Jornada própria de titular:
Perfil → Recebimentos → cadastro voluntário, documentos, situação e recuperação. Contas delegadas
continuam no seletor de configuração do grupo; cadastro não transfere identidade nem ativa grupos.

## Tarefas atômicas

1. Backend: termos vigentes e recuperação da conta própria. FinancialConditions/Jdbc/Controller,
   FinancialOnboarding/AccountsController; testes integração HTTP/JDBC e segurança/arquitetura.
2. Domain/data mobile: contrato de conta própria, formulário PF/PJ, documentos, gateway autenticado,
   upload com resultado tipado no network compartilhado. Gate gateways/network Android+iOS/detekt.
3. Ports de seleção de documentos Android/iOS, registro explícito no composition root e DI.
   Gate compilação nativa e testes de limites/cancelamento.
4. Presentation: estado/VM/telas/strings, Perfil/rotas/DI, recuperação sem PII persistida e consulta
   após navegador/envio. Gate VM/UI/rotas/DI Android+iOS, capturas inspecionadas.
5. Verificação independente após commits, mutações em cópia temporária e relatório por critério.

## Critérios e decisões

FO1 — GET /api/receivables/terms retorna a versão publicada e já vigente mais recente (effectiveAt,
publishedAt, version decrescentes como desempate determinístico); sem termos 404. Versões históricas
continuam acessíveis. Sem sessão 401. Nenhum termo, tarifa ou aceite é inventado.
FO2 — POST /accounts/me/recover {requestId} somente para titular da sessão, 404 sem conta,
200 com a mesma conta e requestId. Executa somente operação CREATE_ACCOUNT já registrada;
UNKNOWN sem credencial nunca repete criação. Conta existente permanece consultável após corte.
Respostas de cadastro/documentos/recuperação não são cacheáveis nem contêm chave/dados pessoais.
FO3 — Gateway valida conta/ator, versões/documentos/envelopes e requestId de writes. PF/PJ respeitam
campos backend; renda em centavos exatos sem Double. Cadastro exige termos não vazios e aceite.
Resposta inválida/perda de transporte preserva incerteza. Upload nunca tem retry de transporte
cego; sucesso de envio não significa documento aprovado. Dados sensíveis somente em memória.
FO4 — Consulta conta própria antes de oferecer formulário; entrada permanente em Perfil. Cadastro
é voluntário, independente da associação ao grupo; aprovação do cadastro não ativa recebimentos.
INCOMPLETE, UNDER_REVIEW, CORRECTION_REQUIRED, APPROVED e REJECTED têm rótulos/ações explícitos.
Falha na consulta não significa ausência de conta. Cadastro novo depende de disponibilidade backend/mobile.
FO5 — Persistir apenas ator/requestId/tipo/id/status do documento antes de mutações; bloquear novo
cadastro/edição/saída quando resultado incerto. Consulta vazia não autoriza outro cadastro. Em memória,
replay do cadastro mantém comando exato, sempre após consulta; restauração consulta conta sem PII.
Conta encontrada encerra tentativa de criação e permite recuperar provisionamento existente.
Logout/generation descartam respostas/callbacks/efeitos antigos e limpam PII/arquivo/marker.
FO6 — Documento com onboardingUrl usa navegador HTTPS de Asaas validado, sem interpretar retorno
como aprovação. Documento com envio API permite seleção explícita PDF/JPEG/PNG até5MiB,
confirmação antes do envio, bytes só em memória; cancelamento do picker não envia. Depois do envio
consulta situação remota. Resultado incerto preserva marker; PENDING antigo não prova novo resultado.
FO7 — UI shared commonMain com DS/strings/tags/previews; rotas escalares/serializáveis e Koin.
Exibir empty/loading/erro/termos/formulário/revisão/documentos/incerto/estados da conta. Voltar nativo
respeita pendência; retorno atualiza descoberta da conta. Testes isolam cada condição do aceite.

## Limites externos

Homologação real Asaas e termos/condições reais continuam em T20. Credencial perdida no provedor
não será recriada automaticamente; recuperação operacional desse caso requer identificação segura
no provedor e permanece na fila T18. Correção cadastral remota/delegações/carteira são tarefas
seguintes; não apresentar telas não implementadas como ações disponíveis.

## Gate tarefa 1

/ tmp sem espaço: `/tmp/saqz-onboarding-backend.log`, exit0. 10 onboarding +4 conditions +8 security
+20 architecture =42 execuções, sem falhas/erros/skips.

| AC | Asserções literais por teste (paths sob backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables) | Resultado |
|---|---|---|
| FO1 | FinancialConditionsIntegrationTest:103–105 404/no-store/null; :110 versão v2; :116–118 versão v4/conteúdo/hash exatos; :120 histórico v1; :122 count0 | ausente/futuro/não publicado/desempate/histórico/somente leitura |
| FO2 | FinancialOnboardingIntegrationTest:173 NOT_FOUND; :179/181 Success(account,request); :182 operação id igual; :183 1 POST/sem credencial; :185 foreign NOT_FOUND | UNKNOWN recupera sem nova subconta e sem trocar ator |
| FO2 HTTP | mesmo arquivo:205 404; :211–214 status200/no-store/requestId/conta/ator/UNDER_REVIEW/operationsfalse; :216 conjunto exato de campos; :217–218 retry1POST,400/404 | envelope e isolamento |
| FO6 descrição | mesmo arquivo:225 Success(documento CUSTOM com descrição literal) | instrução preservada |
| FO1/2 sessão | BearerSecurityIntegrationTest novo método `financial onboarding discovery and recovery require authentication`, helper assertUnauthorized401 | segurança real |

Todos os testes adicionados mapeiam FO1/FO2/FO6; nenhum existente foi removido, ignorado ou enfraquecido.

## Gate tarefa 2

Gateway autenticado e multipart com envelope tipado: dados/requestId exatos, erros e proteção contra
retry de upload. Extensão `uploadMediaDecoded` reutiliza o transporte limitado existente; o método
antigo de mídia conserva seu decoder Unit. Gate `/tmp/saqz-onboarding-data.log`, exit0: data39 Android
+39 iOS; network allTests e detekt domain/data/network aprovados.

FO3/6 evidências em KtorFinancialOnboardingGatewayTest (commonTest): :21 conta inteira; :22 ator
inválido; :23 ausência404 vs :24 rede; :31 termo completo; :33 vazio inválido; :45–50 corpo exato/retry;
:58–62 recuperação somente request/isolamento; :69 instruções; :72–81 links/duplicatas; :90–93 bytes,
MIME e parâmetros exatos; :102–107 pending/malformed/perda => UNCERTAIN e chamada única;
:115–123 comandos e arquivos inválidos não enviam; :130–133 erros400/401/403/404/409 tipados.
Cada um dos nove testes deriva FO3/FO6, sem casos removidos/ignorados nem asserções enfraquecidas.

## Gate tarefa 3

Android OpenDocument e iOS UIDocumentPicker registrados explicitamente no composition root;
port exportado no framework Swift. Bytes limitados a 5 MiB, sem gravação de rascunho de documento.

| Critério | Evidência exata | Resultado esperado |
|---|---|---|
| FO6 limites e MIME | `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/ReceiptDocumentPickerTest.kt:28` — `assertEquals(mime, file.contentType); assertArrayEquals(bytes, file.bytes)`; :32 `assertThrows(IllegalArgumentException::class.java)` | preserva bytes/MIME no limite; rejeita vazio, excesso e tipo inválido |
| FO6 cancelamento | mesmo arquivo :43 `assertEquals("android.intent.action.OPEN_DOCUMENT", launched.intent.action)`; :49 `assertEquals(listOf(ReceiptFileSelection.Invalid), results)`; :53 `assertEquals(listOf(ReceiptFileSelection.Invalid, ReceiptFileSelection.Cancelled), results)` | seleção explícita, callback cancelado não recebe resultado antigo |
| FO7 DI real | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/SaqzKoinBootstrapTest.kt` — `assertSame(dependencies.financialDocuments, koin.get<ReceiptDocumentPicker>())` (tipo qualificado no código) | porta da plataforma é a instância resolvida |

Cada teste deriva FO6/FO7. Gates: `/tmp/saqz-onboarding-native-final.log` (2 picker +7 DI/rotas
solicitados no Android, 6 DI efetivamente executados; rota é iOS), `/tmp/saqz-onboarding-full-ui.log`
(2 bootstrap +6 DI Android), `/tmp/saqz-onboarding-visual.log` (9 DI/rota iOS), todos exit0.
Swift6 typecheck do adapter exit0 em `/tmp/saqz-onboarding-swift.log`; app SaqzDev compilado
no Xcode para arm64 simulator, `BUILD SUCCEEDED` em `/tmp/saqz-onboarding-xcode.log`.
Compilação não equivale a walkthrough do seletor nativo iOS; esse limite não é homologação Asaas.

## Gate tarefa 4

Critérios FO3–FO7: estado/VM, Root, tela shared, Perfil, navegação e DI.

| Critério | Evidência literal no teste |
|---|---|
| FO4 | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:25` — `assertFalse(failed.state.value.discovered); assertFalse(failed.state.value.canEdit); assertEquals(ReceiptError.NETWORK, failed.state.value.error)` |
| FO3 | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:34` — `assertEquals(250001L, registration.incomeCents); assertEquals("1990-01-01", registration.birthDate); assertNull(registration.companyType)` |
| FO3/5 | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:71` — `assertEquals(OnboardingAttempt("owner", f.commands.single().requestId, "CREATE"), Json.decodeFromString<OnboardingAttempt>(marker))` |
| FO5 replay | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:78` — `assertTrue(f.mineReads > reads); assertSame(f.commands[0], f.commands[1])` |
| FO5 restauração | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:81` — `assertEquals(2, f.commands.size); assertTrue(restored.state.value.pending)` |
| FO5 recuperação | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:92` — `vm.onIntent(FinancialOnboardingIntent.Recover); assertEquals(f.recoveries[0], f.recoveries[1]); assertTrue(f.commands.isEmpty())` |
| FO6 envio | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:103` — `assertEquals(onboardingDocument, f.uploads.single().first); assertContentEquals(onboardingFile.bytes, f.uploads.single().third.bytes)` |
| FO6 incerteza | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:114` — `assertEquals(OnboardingAttempt("owner", f.uploads.single().second, "UPLOAD", "doc", "PENDING"), Json.decodeFromString<OnboardingAttempt>(marker))` |
| FO5 sessão | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:150` — `assertNull(vm3.state.value.selectedFile); assertEquals(ReceiptError.SIGNED_OUT, vm3.state.value.error); assertTrue(f3.pickCancelled)` |
| FO6 link | `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt:128` — `assertEquals(FinancialOnboardingEffect.Open("doc", "https://asaas.com/onboarding/test", effect.generation), effect)` |
| FO7 valor/aceite | `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/FinancialOnboardingScreenshotTest.kt:31` — `compose.onNodeWithText("Renda ou faturamento informado: R$\u00a02.500,01").performScrollTo().assertIsDisplayed()` |
| FO7 confirmação | `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/FinancialOnboardingScreenshotTest.kt:74` — `assertEquals(FinancialOnboardingIntent.Upload, intents.last()); capture("confirmar-documento")` |
| FO7 voltar nativo | `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/FinancialOnboardingBackTest.kt:50` — `assertEquals(0, returned); assertEquals(marker, saved.get<String>("onboarding.attempt"))` |
| FO7 diretório | `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/FinancialOnboardingBackTest.kt:57` — `assertEquals(1, changed)` |
| FO7 Perfil | `mobile/features/profile/presentation/src/commonTest/kotlin/br/com/saqz/profile/presentation/own/ui/OwnProfileRootTest.kt:38` — `assertEquals(1, opens)` |
| FO7 rota | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/MemberPaymentNavigationTest.kt:22` — `assertEquals(stack.toList(), restored.toList())` |

Mapa reverso: os 11 testes FinancialOnboardingViewModelTest cobrem descoberta (FO4), PF/PJ e
aceite (FO3), invalidação do aceite (FO3/5), create incerto, recover, sessão e rejeição (FO5),
seleção/envio/link/upload incerto (FO6). Os três testes ScreenshotTest cobrem formulário/termos,
estados/voltar e documentos/situação (FO7); BackTest cobre Root/diretório (FO5/7). O teste iOS
FinancialOnboardingScreenTest cobre os mesmos CTAs no runtime Compose iOS (FO7). Perfil Root/VM,
serialização/logout e DI cobrem a entrada permanente e montagem real (FO4/7). Nenhum teste sem
critério, removido, ignorado ou enfraquecido; fixtures de plataforma ganharam a porta exigida.

Gates finais do autor:
- `/tmp/saqz-onboarding-full-ui.log` exit0: presentation receivables 66 Android +70 iOS; profile
  12 Android +36 iOS; DI/bootstrap Android8; picker2 e UI Android4. Detekt presentation/profile/compose.
- `/tmp/saqz-onboarding-visual.log` exit0: DI/bootstrap/rota iOS9; Roborazzi onboarding3,
  14 capturas em `mobile/build/reports/financial-onboarding/` (locais). Inspecionadas formulário,
  termos, confirmação, incerteza, enviado, preparação, link e erro; os demais estados usam a mesma composição.
- `/tmp/saqz-onboarding-android-final.log` exit0: detekt Android app completo +18 testes de
  onboarding/picker e regressões dos testes antigos, que receberam somente quebras de linha.
- `/tmp/saqz-onboarding-ui-build.log` exit0: compilação Android e framework iOS, testes VM e detekt.

Adequação do autor: resultados e payloads exatos, limites, consentimento, recuperação e sessão
cobertos nos contratos desta onda. Revisão independente ainda deve confirmar discriminação.
As duas falhas gerais Android instrumentadas anteriores não fazem parte destes resultados.

## Fechamento da onda

Revisão independente `verify_financial_onboarding` aprovada no delta24fd5bc4..19fba8f4:
FO1–FO7 com evidências, dez falhas comportamentais distintas injetadas em scratch e detectadas,
nenhuma sobrevivente/inconclusiva. Relatório `docs/receivables/evidence/financial-onboarding-mobile-review.md`.
Sem defeito novo que exija registrar lição; nenhuma regra candidata promovida automaticamente.
Compilação final Android/framework e detekt em `/tmp/saqz-onboarding-final-compile.log`; Xcode
com framework final em `/tmp/saqz-onboarding-xcode-final.log`, ambos aprovados no HEAD03e6f2e3.
As duas falhas instrumentadas antigas foram corrigidas separadamente em03e6f2e3; o gate geral
Android passou44/44, com inspeção independente no apêndice do relatório. Os limites externos
e as demais tarefas da iniciativa continuam como descritos acima; esta onda está concluída.
