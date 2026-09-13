# Evidência do autor — gestão financeira final

Data: 2026-09-13. Escopo: F2/T04/T05/T15 (correção de cadastro comercial e delegação de acesso à conta financeira). O contrato executável foi publicado em `docs/receivables/final-management-contract.md`; os pressupostos, ACs, desenho e tarefas TLC estão em `.specs/features/final-financial-management/`.

## Fonte e contrato do provedor

A implementação foi limitada pela documentação oficial do Asaas para `GET` e `POST /v3/myAccount/commercialInfo/`:

- <https://docs.asaas.com/reference/recuperar-dados-comerciais>
- <https://docs.asaas.com/reference/atualizar-dados-comerciais>
- <https://docs.asaas.com/docs/confirma%C3%A7%C3%A3o-anual-de-dados-comerciais-para-subcontas>

O cliente só representa `email`, `phone`, `mobilePhone`, `site`, `incomeCents`, `postalCode`, `address`, `addressNumber`, `complement` e `province`. O adaptador lê primeiro o cadastro comercial completo e preserva `personType`, `cpfCnpj`, `birthDate`, `companyType`, `companyName` e `taxRegime`; CPF/CNPJ, nome legal e titularidade não entram no contrato público.

## Evidência por critério

- **MGT-01/MGT-04 — descoberta e revogação imediata:** os controllers de contas/delegações agora respondem `no-store`; `ManageFinancialRegistration` consulta repositório, delegação e administração atuais e repete a autorização entre o GET remoto e o POST (`FinancialManagement.kt:75-151`). O teste de aplicação prova que delegado revogado recebe `NOT_FOUND` sem IO e que remoção ocorrida depois da leitura ainda impede o POST (`ManageFinancialRegistrationTest.kt:19-39`).
- **MGT-02/MGT-03 — concessão/revogação:** o fluxo existente de delegações permanece a única autoridade de concessão/revogação e exige titular + administrador atual; o novo mobile permite estes intents apenas com papel `OWNER` (`FinancialManagementContract.kt:41`, `FinancialManagementViewModel.kt:117-134`, `FinancialManagementScreen.kt:82-96`). O teste de VM prova que `DELEGATE` corrige mas não concede nem revoga (`FinancialManagementViewModelTest.kt:16-28`).
- **MGT-05 — allowlist e identidade legal:** o domínio backend tem somente os dez campos corrigíveis (`FinancialManagement.kt:9-29`). O adaptador faz read/merge/write e serializa a identidade recuperada (`HttpAsaasFinancialAccounts.kt:19-75`); o teste HTTP local verifica método/caminho/token, `incomeValue` decimal exato, preservação de CPF/CNPJ/personType e ausência de titular/nome inventado (`HttpAsaasFinancialAccountsTest.kt:12-31`).
- **MGT-06/MGT-07 — requestId, concorrência e recovery:** a operação é iniciada antes do IO; apenas a transação que inseriu recebe `shouldExecute=true`, portanto concorrente/replay não faz segundo POST (`FinancialManagement.kt:93-151`, `JdbcFinancialManagement.kt:19-70`). Timeout vira `UNKNOWN/RESULT_PENDING` e recovery apenas lê; os testes provam um único POST, requestId estável e conflito para o mesmo request com dinheiro alterado (`ManageFinancialRegistrationTest.kt:41-69`). A V62 persiste metadados/digest e payload cifrado vinculado à operação; o teste JDBC prova ciphertext sem PII clara, replay não executável e atualização de estado (`JdbcFinancialManagementIntegrationTest.kt:19-44`).
- **MGT-08/MGT-09 — sessão, geração e persistência mobile:** o marker serializável contém somente ator, conta, requestId, tipo, alvo e versão de termos (`FinancialManagementContract.kt:8-10`); dados de correção ficam apenas em memória. A VM valida sessão/ator/generation, limpa no logout e recupera correção sem repetir escrita (`FinancialManagementViewModel.kt:22-165`); testes cobrem restauração, nenhum email/dinheiro no marker e descarte de callback tardio (`FinancialManagementViewModelTest.kt:30-61`).
- **MGT-10 — limites:** não há comando, rota ou controle de reembolso nem transferência de titularidade. A UI compartilhada exibe papel, conta, ator, requestId/resultado, BRL derivado de centavos e alerta fiscal de município; testes iOS exercitam a mesma composição usada por Android (`FinancialManagementScreen.kt:36-112`, `FinancialManagementScreenTest.kt:10-48`).

## Gates executados em cópia limpa

A cópia foi criada a partir de `git archive HEAD` e recebeu somente os arquivos deste escopo mais dependências coordenadas já presentes no checkout. Nenhum Gradle foi executado no workspace compartilhado.

```text
backend:
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon \
  :features:receivables:test --tests '*ManageFinancialRegistrationTest' \
  --tests '*HttpAsaasFinancialAccountsTest' \
  :features:receivables:integrationTest --tests '*JdbcFinancialManagementIntegrationTest' \
  :bootstrap:compileKotlin :architecture-tests:test
RESULTADO: PASS

mobile:
./gradlew --no-daemon --quiet \
  :features:receivables:data:iosSimulatorArm64Test \
  :features:receivables:presentation:iosSimulatorArm64Test
RESULTADO: PASS

./gradlew --no-daemon --quiet \
  :features:receivables:data:compileAndroidMain \
  :features:receivables:presentation:compileAndroidMain
RESULTADO: PASS

gate final após adicionar a entrada de gestão ao onboarding, preservando Carteira:
./gradlew --no-daemon --quiet \
  :features:receivables:presentation:compileAndroidMain \
  :features:receivables:presentation:iosSimulatorArm64Test
RESULTADO: PASS (exit 0)
```

Uma tentativa de executar a suíte de integração backend inteira na base limpa encontrou duas falhas de `ReceivablesSchemaIntegrationTest` porque `git archive HEAD` não contém as migrações ainda não rastreadas V56–V61 de outros autores. Isto não indica falha da V62: o teste JDBC focal de V62 e o compile/bootstrap/architecture passaram quando as dependências coordenadas foram copiadas.

## Wiring entregue ao coordenador

Arquivos centrais não foram editados por este autor. O coordenador deve aplicar:

```diff
--- mobile/compose-app/.../di/ReceivablesModule.kt
+++ mobile/compose-app/.../di/ReceivablesModule.kt
@@
+import br.com.saqz.receivables.data.KtorFinancialManagementGateway
+import br.com.saqz.receivables.domain.FinancialManagementGateway
+import br.com.saqz.receivables.presentation.FinancialManagementViewModel
@@ receivablesModule
+    singleOf(::KtorFinancialManagementGateway) bind FinancialManagementGateway::class
+    viewModelOf(::FinancialManagementViewModel)

--- mobile/compose-app/.../navigation/SaqzNavHost.kt
+++ mobile/compose-app/.../navigation/SaqzNavHost.kt
@@
+import br.com.saqz.receivables.presentation.FinancialManagementRoute
+import br.com.saqz.receivables.presentation.FinancialManagementRoot
@@ entry<FinancialOnboardingRoute>
-                    onOpenWallet = { backStack.add(ReceiptWalletRoute) })
+                    onOpenWallet = { backStack.add(ReceiptWalletRoute) },
+                    onOpenManagement = { backStack.add(FinancialManagementRoute) })
+            entry<FinancialManagementRoute> {
+                FinancialManagementRoot(onBack = pop, onChanged = {
+                    receiptsCoordinator.refresh(); profileRefreshVersion++
+                })
+            }

--- mobile/compose-app/.../navigation/SaqzLocalNavConfiguration.kt
+++ mobile/compose-app/.../navigation/SaqzLocalNavConfiguration.kt
@@
+import br.com.saqz.receivables.presentation.FinancialManagementRoute
@@ polymorphic(NavKey::class)
+            subclass(FinancialManagementRoute::class, FinancialManagementRoute.serializer())
```

O backend é conectado isoladamente por `ReceivablesManagementConfiguration`, condicionado à chave de criptografia já usada por recebíveis. Não é preciso alterar `ReceivablesModule` backend nem bootstrap global.

## Limites reais e verificação independente

- Não houve chamada real ao Asaas, homologação, prova de análise cadastral, captura visual ou UAT humano.
- Não houve pedido, aprovação ou execução de reembolso.
- A autorização é refeita imediatamente antes do POST. Uma remoção confirmada antes deste ponto bloqueia a mutação; uma remoção que ocorra depois do POST não pode desfazer atomicamente uma atualização já aceita pelo provedor externo.
- O POST de substituição pode reiniciar análise e mudança de município pode afetar configuração fiscal/NFS-e, por isso a UI exige aviso explícito.
- O autor executou revisão e gates discriminantes, mas não há verificador independente: a tarefa proibiu novos workers. O coordenador deve fazer a verificação autor != verifier e integrar o wiring central acima.

## Integração posterior pelo coordenador

O coordenador completou DI, navegação e restauração. A concessão agora oferece candidatos por nome/grupos
em endpoint exclusivo do titular; a UI não pede UUID e não exibe IDs técnicos. Snapshot dos seis campos legais
é cifrado antes do POST e conferido também na recuperação. Resposta imediata exige ainda a correção exata.
Acesso revogado limpa campos/candidatos/role; operação pendente não migra para outra conta por fallback.
Testes adicionais cobrem candidatos atuais e titular, identidade cifrada preservada, divergência legal após timeout,
revogação na VM e seleção por nome na UI Android/iOS. MGT-11..13 estão na especificação atualizada.
O callback central foi renomeado `onChange` para cumprir o lint Compose sem supressões.
