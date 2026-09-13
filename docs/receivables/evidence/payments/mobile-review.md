# Revisão independente — configuração mobile de Recebimentos

**Resultado: revisão funcional Recebimentos aprovada, sem defeitos remanescentes em `ce18d907`; gate Android geral permanece vermelho por duas falhas também reproduzidas na base `45d10d0d`.** Os dois achados de recuperação encontrados no primeiro passe foram corrigidos e suas regressões passaram na reexecução independente. Os gates host/iOS/visuais e o probe instrumentado de Recebimentos passaram; não atesto integração financeira de homologação nem aprovação integral do gate Android geral.

Revisor: `task_0e3a5228db83` / `ctx_9608ae52f015`. Checkout `/Users/bruno_almeida/Private/saqz`, branch `feat/receivables-payments`, base `origin/main` informada `45d10d0d`. Fontes rastreadas e todas as novas fontes mobile foram incluídas, com leitura inicial durante a implementação e rechecagem do snapshot congelado após liberação do coordenador. Não editei fontes nem executei mutações git. Scripts/probes e relatório foram criados somente em `/tmp`; Gradle escreveu seus outputs normais ignorados.

## Achados do passe e resolução

### M1 — P2, corrigido: operação incerta dependia de chave de sessão volátil

No primeiro passe, `ReceiptConfigurationViewModel` comparava `receipt.session` persistida com `session.currentKey()`. A implementação real em `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/VerifiedSessionCoordinator.kt:168` compõe essa chave com generation em memória e user.id. Após logout/login no mesmo processo e morte do processo durante pendência, a geração reinicia e o init antigo descartava requestId/fingerprint/comando mesmo para o mesmo usuário. O teste anterior recriava somente a VM com chave constante e não cobria essa condição.

O autor separou `ReceivablesRecoveryIdentity` da guarda de sessão em voo: persistência agora usa `receipt.user`, fornecida pelo usuário autenticado na composition root; o retorno assíncrono ainda exige a chave integral da sessão. `onCleared` limpa a pendência quando a sessão foi revogada. A regressão troca `7:user` por `1:user`, reenvia corpo idêntico e verifica estado final; outro teste rejeita recuperação por usuário diferente. Ambos passaram no host e iOS. Isso verifica o comportamento da VM com estado restaurado; não substitui teste de morte de processo Android com NavHost e autenticação reais.

Paths principais: `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceiptConfigurationViewModel.kt`, `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/GroupReceivables.kt`, `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/ReceivablesModule.kt`.

### M2 — P3, corrigido: replay restaurado deixava contas/permissões vazias

O init restaurava apenas accountId/pendingMutation. Após sucesso, a VM conservava accounts vazio e criava `ReceiptStatus(..., emptyMap())`: a Screen mostrava ausência de conta e não habilitava CANCEL, exigindo Atualizar + selecionar novamente. A captura de sucesso anterior usava contas/permissões previamente preenchidas e não representava o problema.

Após replay restaurado, `reloadContext` agora consulta contas e estado de manutenção; `hasNoAccount` e o bloqueio de mensagem durante pendência evitam a mensagem contraditória. A regressão verifica conta recarregada e CANCEL permitido. Probe independente adicional restaurou uma **desativação**, manteve MOBILE/BACKEND OFF, reenviou o comando original, confirmou estado desativado e permissões recarregadas. Sucesso nos dois casos. Nenhuma nova escrita é criada para recarregar o contexto.

Paths adicionais: `ReceiptConfigurationContract.kt`, `ReceiptConfigurationScreen.kt` e `ReceiptConfigurationViewModelTest.kt` nos diretórios de apresentação Receivables.

## Matriz de revisão

| Requisito | Evidência e conclusão |
|---|---|
| PIX, CARD, ambos, nenhum | Teste parametriza os três conjuntos não vazios; seleção inicial vazia bloqueia preview/ativação. PNGs meios-0/1/2 e nenhum-meio inspecionados. |
| Aceite e invalidação | Revisão exige termos completos por versão e ACTIVATE_GROUP autorizado. Troca de meios/conta e conflito invalidam revisão/aceite; ativação sem aceite não chama gateway. |
| API e BigDecimal numérico | Controllers reais conferidos; gateway consome `{value,requestId}`, preços Long em centavos, taxa JsonPrimitive obrigatoriamente numérica. Probe serializou BigDecimal real com Jackson e passou `0.02990000000000000000001`, `1E-8`, `0.0000` pelo gateway sem perder representação. Sem Double ou gross-up local. |
| Idempotência/resultado incerto | UUID nasce na VM; retry conserva corpo/fingerprint/accepted. Timeout/5xx/200 malformado mantêm comando pendente; edição, refresh e saída voluntária bloqueados. Conflito exige nova revisão. |
| Retry após OFF | Replay de ativação não passa pelo gate de nova operação; regressão existente passou. Probe independente também verificou desativação restaurada com OFF. |
| Logout antes de retorno | Preview, termos e mutação descartam resposta de sessão revogada; estado e SavedState limpos. Testes host e iOS passaram. |
| Manutenção sem rollout | Leitura/desativação não exigem termos/tarifas; availability em coroutine separada não impede CANCEL se estiver suspensa. Permissões vêm do servidor. |
| Entrada MOBILE OFF | `configurationEntryAvailable` esconde OFF + lista vazia; conta existente preserva entrada; falha de consulta mantém tentativa de manutenção desconhecida. Query de contas tem geração/sessão e é independente de availability. Testes OFF/conta/falhas/resposta de sessão anterior passaram. |
| Rota/DI/boundaries | Rota NavKey serializável com groupId escalar registrada em SavedState e NavHost. Grupos usa callback do composition root, sem dependência entre features. Gateway em data/interface em domain, VM/UI em commonMain, instâncias via Koin. Testes de grafo e restauração/logout passaram. |

O estado do coordinator é observado no NavHost para disponibilizar callback; a captura de caixa recebe callback diretamente. A combinação de teste de regra e captura comprova as partes, mas não equivale a percurso integrado da autenticação ao caixa no dispositivo.

## Gates independentes executados

Após o coordenador liberar `ce18d907`, usei JDK21 `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` e ANDROID_HOME `/Users/bruno_almeida/Library/Android/sdk`. Comandos executados serialmente entre invocações, sem builds backend:

```sh
# Prefixo: JAVA_HOME=... ANDROID_HOME=... mobile/gradlew -p mobile
-I /tmp/saqz-mobile-review-rerun.gradle \
 :features:receivables:data:testAndroidHostTest \
 :features:receivables:presentation:testAndroidHostTest \
 :compose-app:testAndroidHostTest \
 :features:receivables:data:iosSimulatorArm64Test \
 :features:receivables:presentation:iosSimulatorArm64Test \
 :compose-app:iosSimulatorArm64Test detektAll --no-parallel

-I /tmp/saqz-mobile-review-probes/init.gradle \
 :features:receivables:data:testAndroidHostTest \
 :features:receivables:presentation:testAndroidHostTest \
 --no-parallel --no-configuration-cache

-I /tmp/saqz-mobile-review-rerun.gradle :android-app:testDevDebugUnitTest --no-parallel

-I /tmp/saqz-mobile-review-rerun.gradle \
 :android-app:recordRoborazziDevDebug --tests '*ReceiptConfigurationScreenshotTest' \
 :features:groups:presentation:recordRoborazziAndroidHostTest \
 --tests '*GroupCashboxScreenshotTest.receiptConfigurationEntry*' --no-parallel
```

O init de reexecução define `outputs.upToDateWhen { false }` nas tasks de teste: testes realmente executaram, não foram aprovados apenas como UP-TO-DATE. Compilações/dependências sem mudança puderam reutilizar outputs; detektAll concluiu com sucesso.

| Gate | Testes | Falhas/erros/skips |
|---|---:|---:|
| Receivables data host | 9 | 0 |
| Receivables presentation host | 23 | 0 |
| Compose-app host | 12 | 0 |
| Receivables data iOS sim | 9 | 0 |
| Receivables presentation iOS sim | 24 | 0 |
| Compose-app iOS sim | 140 | 0 |
| Android app integrado host | 188 | 0 |
| Visuais app + entrada Grupos | 4 | 0 |
| Probes independentes adicionais | 2 | 0 |

Totais principais: **44 host + 173 iOS**, além de **188 integrados Android host**, **4 visuais** e **2 probes extras**. As categorias têm sobreposição funcional e não representam quantidade de jornadas distintas. Os probes rodaram junto das suítes host de data/presentation (10 e 24 testes naquela invocação).

Logs: `/tmp/saqz-payment-mobile-review-gates.log`, `/tmp/saqz-payment-mobile-review-probes.log`, `/tmp/saqz-payment-mobile-review-android.log`, `/tmp/saqz-payment-mobile-review-visual.log`. Contagem da suíte integrada antes da task visual filtrar os XMLs: `/tmp/saqz-payment-mobile-review-android-count.txt`. Código, fixture numérica e XMLs dos probes: `/tmp/saqz-mobile-review-probes/`.

Duas falhas de preparação do probe foram corrigidas apenas em `/tmp`: init inicialmente tentou localizar módulos no included build `build-logic`; depois uma assert do probe consultava hasNoAccount durante pendência, embora a Screen já suprima a mensagem por pendingMutation. Assert foi movida para o estado pós-replay que ela pretendia validar. Nenhuma dessas falhas exigiu correção de produto. Reexecução final verde.

`git diff --check ce18d907 -- mobile` e `git status --short mobile` terminaram sem saída: fontes mobile permaneceram iguais ao snapshot revisado.

## Evidência visual e limites

Inspecionei **31 PNGs** em `mobile/build/reports/receivables-configuration`, abrangendo entrada presente/oculta, cinco registros cadastrais, carregamento, sem conta, sete erros, meios individuais/ambos/vazio, operação pendente/em andamento/restaurada, revisão, composição, termos, aceite e desativação sem rollout. Nenhuma sobreposição ou truncamento impeditivo observado nessas capturas; cortes correspondem à viewport rolável. A captura restauracao-pendente agora não afirma ausência de conta.

- **Roborazzi/Robolectric:** Screen real renderizada com fixtures em Pixel7, incluindo clique/intent e callback contextual; 4 testes visuais reexecutados. Não é Android instrumentado.
- **Compose iOS sim:** execução nativa do teste de seleção/intent da Screen e demais suítes de VM/DI/navegação. Não é jornada financeira autenticada contra backend.
- **Android real do AVD:** `connectedDevDebugAndroidTest` foi executado (44 testes, 2 falhas preexistentes confirmadas na base). Um probe independente de Recebimentos passou com Screen/VM reais e gateways fake. Não há evidência de morte/restauração do processo completo, percurso pelo NavHost ou ativação contra backend real.
- **HTTP real:** o probe de BigDecimal usa serialização Java/Jackson 2.19 local e Ktor MockEngine; prova parsing numérico, sem alegar resposta capturada do servidor. Controllers e contratos foram comparados por leitura. Não houve chamada ao backend de homologação ou Asaas.

Gaps de evidência que permanecem: revisão CARD/ambos com vários preços/versões de termos não tem captura específica; viewport pequeno/fonte ampliada e cutoff preenchido não foram capturados; process death integrado precisa de teste próprio. Esses limites não foram encobertos por aprovação de leitura, capturas ou testes com fakes.

## Complemento instrumentado e comparação de base — concluído

Por solicitação posterior do coordenador, executei `:android-app:connectedDevDebugAndroidTest` no AVD Saqz_API_30, Android 11: 44 testes, 2 falhas, 0 erros/skips (exit 1). Falhas em AndroidAuthenticatedLifecycleTest: registrationSubmitRemainsSingleFlightAcrossRecreation não encontrou Criar conta; restoredUnverifiedSessionSkipsLoginAndSurvivesRecreation não exibiu identity-verify. Log `/tmp/saqz-payment-mobile-review-connected.log`; XMLs originais preservados em `/tmp/saqz-mobile-review-connected-results`. A comparação dos mesmos dois testes em 45d10d0d isolada reproduziu **as mesmas duas falhas**, 2 testes/2 falhas/0 erros/0 skips, exit 1. Não são regressões introduzidas por este lote.

Probe instrumentado extra de Recebimentos **passou**: 1 teste em Android real do AVD, com Screen e ViewModel de produto, gateways fake, lista sem meios, seleção de ambos, revisão, bloqueio de ativação sem aceite, aceite/ativação e desativação após OFF. Código/init em `/tmp/saqz-mobile-review-probes/android/` e `android-init.gradle`, log `/tmp/saqz-payment-mobile-review-android-probe-final.log`, XML em `/tmp/saqz-mobile-review-probes/android-results`. Não navega pelo NavHost nem usa backend real. Preparação do probe exigiu ajustar sourceSet Kotlin e dependência core:common apenas no init temporário.

Comando da comparação de base (prefixo JDK21/ANDROID_HOME igual aos gates):

```sh
/tmp/saqz-mobile-review-base/mobile/gradlew -p /tmp/saqz-mobile-review-base/mobile \
 :android-app:connectedDevDebugAndroidTest \
 '-Pandroid.testInstrumentationRunnerArguments.class=br.com.saqz.androidapp.AndroidAuthenticatedLifecycleTest#registrationSubmitRemainsSingleFlightAcrossRecreation,br.com.saqz.androidapp.AndroidAuthenticatedLifecycleTest#restoredUnverifiedSessionSkipsLoginAndSurvivesRecreation' \
 -Pandroid.testInstrumentationRunnerArguments.timeout_msec=60000 --no-parallel --no-configuration-cache
```

Base extraída com `git archive 45d10d0d mobile`, sem alterar git; arquivos locais necessários copiados sem expor seu conteúdo. Log `/tmp/saqz-payment-mobile-review-base-connected.log`; XMLs em `/tmp/saqz-mobile-review-base/mobile/android-app/build/outputs/androidTest-results/connected/`. A invocação do probe instrumentado usa `-I /tmp/saqz-mobile-review-probes/android-init.gradle` e filtro `br.com.saqz.androidapp.IndependentReceiptAndroidProbe`; não inclui fontes temporárias no produto.

Uma tentativa intermediária de reexecutar a classe inteira junto do probe ainda não empacotado encontrou initializationError no probe e não concluiu o primeiro teste de cadastro; interrompi o app de teste para recuperar o AVD. Essa tentativa não foi usada para classificar regressão: a prova comparável é a suíte completa do snapshot atual e a execução dos dois casos na base, com mensagens idênticas.

**Pendência externa ao lote:** corrigir os dois testes/fluxos de cadastro e sessão que já falham na base antes de declarar o gate instrumentado geral integralmente aprovado. Não alterei cadastro fora do escopo. O dispositivo ficou com o APK da base instalado após a comparação; fontes do checkout continuam em ce18d907, sem alteração do revisor.
