# Mobile — configuração de recebimentos

Implementada a jornada real de configuração do grupo, com seleção explícita Pix/cartão/ambos, leitura autenticada de contas e estado, revisão de preços e termos, ativação por fingerprint e desativação independente de rollout/plano. Código de produto em commonMain, gateway em domain/data, MVI na apresentação, Koin e rota Nav3 escalar registrada para restauração. Entrada contextual no caixa por callback montado no composition root, sem dependência entre features.

## Escopo e contratos

- Lidos integralmente mobile/AGENTS.md, docs/receivables/payment-wave.md e rollout-contract.md; controllers FinancialAccountDirectoryController, FinancialConditionsController e GroupReceivablesController e serviços reais consultados.
- Entrada contextual exige discovery ou conta existente: OFF sem conta oculta callback; OFF com conta preserva; erro de consulta de contas mantém acesso de tentativa para não descartar manutenção desconhecida. Estado do diretório tem guarda de geração/sessão e não depende da conclusão de availability.
- GET accounts não recebe identidade do cliente; bearer vem do AuthenticatedNetworkClient existente.
- Gap de leitura independente informado via Orca msg_70e7583b6a9a. Coordenador confirmou GET groups/{groupId}?accountId com value.state e permissions READ/CANCEL, implementado pelo worker backend e conferido na fonte; não foi inventado endpoint unilateralmente.
- Nenhum meio e nenhum aceite selecionados por padrão. Preview e ativação exigem pelo menos um meio; trocar meios remove a revisão e o aceite.
- Termos integrais carregados por cada versão antes de habilitar aceite. Valores e totais vêm do servidor; composição opcional mostra percentuais formatados por deslocamento decimal exato, sem Double nem cálculo local de preço.
- Mutação conserva requestId, métodos, fingerprint e accepted originais. NETWORK/UNAVAILABLE/UNCERTAIN bloqueiam edição, refresh, outra mutação e saída voluntária da rota; só reenvio idêntico fica disponível, sem novo gate rollout para replay.
- Comando pendente persiste em SavedStateHandle antes do envio e pode ser recuperado pelo mesmo usuário após morte do processo mesmo quando a geração de sessão reinicia. Identidade estável de recuperação vem de uma porta separada; a chave opaca de geração continua protegendo respostas em voo. Após replay restaurado, accounts e status/permissions são recarregados. Conflito 409 elimina revisão/aceite; logout/troca de sessão invalida respostas e limpa a pendência local.
- Leitura de manutenção não espera a consulta de rollout: teste com availability suspensa comprova desativação disponível. Permissions CANCEL/ACTIVATE_GROUP vêm do backend.
- Não há gatilho de criação de subconta, cadastro financeiro fictício, emissão, pagamento, saque ou reembolso nesta frente.

## Gates executados

Ambiente: JDK21 `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`, ANDROID_HOME `/Users/bruno_almeida/Library/Android/sdk`.

Prefixo de todos os comandos Gradle:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/bruno_almeida/Library/Android/sdk mobile/gradlew -p mobile
```

Rodada final integrada (funcional, lint e visuais), exit 0 (log `/tmp/receipt-final-gates.log`):

```text
:features:receivables:data:iosSimulatorArm64Test
:features:receivables:presentation:iosSimulatorArm64Test
:compose-app:iosSimulatorArm64Test
detektAll
:features:receivables:data:testAndroidHostTest
:features:receivables:presentation:testAndroidHostTest
:compose-app:testAndroidHostTest
```

| Módulo | Task | Testes | Falhas/erros |
|---|---|---:|---:|
| `features/receivables/data` | `testAndroidHostTest` | 9 | 0 |
| `features/receivables/data` | `iosSimulatorArm64Test` | 9 | 0 |
| `features/receivables/presentation` | `testAndroidHostTest` | 23 | 0 |
| `features/receivables/presentation` | `iosSimulatorArm64Test` | 24 | 0 |
| `compose-app` | `testAndroidHostTest` | 12 | 0 |
| `compose-app` | `iosSimulatorArm64Test` | 140 | 0 |

Os testes de iOS executaram no simulador, incluindo a UI Compose (sem meios selecionados, preview desabilitado, clique em Pix emitindo Intent) e restauração/logout da nova rota. Android host cobre gateways/ViewModels e DI; o host de compose-app exclui testes de navegação por convenção existente, portanto a prova da nova navegação é a suíte iOS de 140 testes, com ReceiptConfigurationNavigationTest executado.

Gate visual Android, renderização Compose/Roborazzi real:

```text
:android-app:recordRoborazziDevDebug --tests '*ReceiptConfigurationScreenshotTest'
:features:groups:presentation:recordRoborazziAndroidHostTest --tests '*GroupCashboxScreenshotTest.receiptConfigurationEntry*'
```

Dois testes do app e dois testes contextuais do caixa (callback presente e ausente). Log integrado `/tmp/receipt-final-android.log` (exit 0), regeneração final integrada também em `/tmp/receipt-final-gates.log`. Compilação Android do app e fontes comuns foi dependência desses comandos; compilação/link iOS foi dependência dos testes de simulador. `git diff --check -- mobile` passou.

Falhas intermediárias foram corrigidas: nome de task Android incorreto na primeira invocação, token de espaçamento, parâmetro inserido em chamada vizinha, parsing numérico de BigDecimal, inferência de lista no teste Native, opt-in de BackHandler e complexidade/comprimento de linhas do detekt. Baselines não foram alterados. Não houve falha remanescente nos gates acima.

## Evidência visual

Capturas em `mobile/build/reports/receivables-configuration/`, com fixtures de teste explícitas; são telas reais renderizadas, não imagens desenhadas ou evidência de transação financeira em produção. Revistos seleção, estados de cadastro, ausência de conta, erro, pendência, tarifas, termos, aceite e manutenção, além da entrada no caixa.

- [aceite-explicito.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/aceite-explicito.png)
- [cadastro-approved.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/cadastro-approved.png)
- [cadastro-correction_required.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/cadastro-correction-required.png)
- [cadastro-incomplete.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/cadastro-incomplete.png)
- [cadastro-rejected.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/cadastro-rejected.png)
- [cadastro-under_review.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/cadastro-under-review.png)
- [carregando.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/carregando.png)
- [configuracao-salva.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/configuracao-salva.png)
- [desativacao-sem-rollout.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/desativacao-sem-rollout.png)
- [entrada-caixa.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/entrada-caixa.png)
- [entrada-oculta-sem-conta-off.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/entrada-oculta-sem-conta-off.png)
- [erro-denied.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/erro-denied.png)
- [erro-invalid.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/erro-invalid.png)
- [erro-network.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/erro-network.png)
- [erro-signed_out.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/erro-signed-out.png)
- [erro-stale.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/erro-stale.png)
- [erro-unavailable.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/erro-unavailable.png)
- [erro-uncertain.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/erro-uncertain.png)
- [meios-0.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/meios-0.png)
- [meios-1.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/meios-1.png)
- [meios-2.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/meios-2.png)
- [nenhum-meio.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/nenhum-meio.png)
- [operacao-em-andamento.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/operacao-em-andamento.png)
- [operacao-pendente.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/operacao-pendente.png)
- [restauracao-pendente.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/restauracao-pendente.png)
- [revisao-resumida.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/revisao-resumida.png)
- [rollout-off.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/rollout-off.png)
- [sem-conta.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/sem-conta.png)
- [tarifas-e-precos.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/tarifas-e-precos.png)
- [termos-indisponiveis.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/termos-indisponiveis.png)
- [termos-sem-aceite.png](https://raw.githubusercontent.com/bruno-halmeida/saqz/8577de1e628c7eeb3c8a422c216a6f16f2891b95/receivables-payment-methods/termos-sem-aceite.png)

## Arquivos e limite

28 arquivos mobile neste lote: 141 linhas alteradas em arquivos rastreados + 1294 linhas novas = **1435 linhas**, abaixo do teto de 2000. Contagem relativa ao estado HEAD atual apenas desta frente; arquivos de outras frentes não foram contabilizados nem editados. Nenhum staging/branch/commit/push executado.

- `mobile/android-app/build.gradle.kts`
- `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/ReceiptConfigurationScreenshotTest.kt`
- `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/ReceivablesModule.kt`
- `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzLocalNavConfiguration.kt`
- `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt`
- `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/di/SaqzKoinModulesTest.kt`
- `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/ReceiptConfigurationNavigationTest.kt`
- `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/receivables/ReceivablesLogoutTest.kt`
- `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/receivables/ReceivablesSessionBindingTest.kt`
- `mobile/features/groups/presentation/src/androidHostTest/kotlin/br/com/saqz/groups/presentation/ui/finance/groupcash/GroupCashboxScreenshotTest.kt`
- `mobile/features/groups/presentation/src/commonMain/composeResources/values/strings_receivables.xml`
- `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/finance/groupcash/GroupCashboxRoot.kt`
- `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/finance/groupcash/GroupCashboxScreen.kt`
- `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorGroupReceivablesGateway.kt`
- `mobile/features/receivables/data/src/commonTest/kotlin/br/com/saqz/receivables/data/KtorGroupReceivablesGatewayTest.kt`
- `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/GroupReceivables.kt`
- `mobile/features/receivables/presentation/build.gradle.kts`
- `mobile/features/receivables/presentation/src/commonMain/composeResources/values/strings.xml`
- `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceiptConfigurationContract.kt`
- `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceiptConfigurationScreen.kt`
- `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceiptConfigurationViewModel.kt`
- `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceiptFormatting.kt`
- `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceivablesContract.kt`
- `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceivablesCoordinator.kt`
- `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ReceiptConfigurationViewModelTest.kt`
- `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ReceiptEntryDiscoveryTest.kt`
- `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ReceivablesCoordinatorTest.kt`
- `mobile/features/receivables/presentation/src/iosTest/kotlin/br/com/saqz/receivables/presentation/ReceiptConfigurationScreenTest.kt`

## Limites explícitos

- Sem credenciais ou conta financeira de homologação fornecidas: integração HTTP testada com Ktor MockEngine e contratos reais, UI com fixtures, não uma ativação Asaas real.
- Não foi executado connectedDevDebugAndroidTest em emulador físico/AVD; Android foi compilado e testado por host/Robolectric, iOS por simulador nativo.
- GET accounts atual não oferece nome/rótulo de conta. UI usa referência curta estável do ID; gap de rótulo seguro para múltiplas delegações informado via Orca msg_f6174ef1dc15.
- A recuperação SavedStateHandle é local à rota e ao usuário autor autenticado; troca de usuário não recupera comando alheio, e contexto revogado em logout limpa pendência ao responder ou ao limpar a ViewModel. Teste reproduz geração 7:user antes da morte do processo e 1:user depois, preservando o corpo original. Não há novo endpoint de consulta de operação financeira.
- Cadastro, documentos nativos e pagamentos são frentes posteriores; ausência de conta não cria subconta automaticamente.

Nota do coordenador: diff completo staged conferido em ce18d907 (1435 linhas); três espaços finais foram removidos antes do commit. Evidências publicadas na branch órfã screenshots, commit 8577de1e628c7eeb3c8a422c216a6f16f2891b95. A revisão independente final ainda está em execução.
