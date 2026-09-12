# Evidências incrementais

## T01 — domínio e fronteiras

Gate: JDK 21, `backend/gradlew -p backend :features:subscriptions:test :features:receivables:test :architecture-tests:test`, exit 0.
Cinco testes novos; vinte testes de arquitetura. Testes existentes preservados.

Arquivo das asserções: `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/domain/FinancialAccessTest.kt`.

| Critério do plano | Linha e asserção | Resultado esperado / mapeamento reverso |
|---|---|---|
| B1/B6 manutenção após corte | 22 `assertEquals(ActionPermission(true), ...)` | acesso a dinheiro e instrumentos existentes; FIN-ACCESS-01 |
| B5 bloqueio de novas operações | 26 `assertEquals(ActionPermission(false, INELIGIBLE_PLAN), ...)` | corte comercial sem bloqueio global |
| B2 aprovação e ativação | 33–40 `assertTrue` / `assertEquals(...reason)` | aprovação, liberação e grupo ativado obrigatórios |
| B2 delegação sem redelegação | 47–49 `assertTrue` / `assertEquals(UNAUTHORIZED, ...)` | operação permitida, identidade e concessão exclusivas do titular |
| B2 revogação | 51–54 `assertEquals(UNAUTHORIZED, ...)` | revogado ou administrador removido perde autorização |
| B2 isolamento | 60–62 `assertEquals(UNAUTHORIZED, ...)` | novo dono de grupo e delegado de outra conta sem acesso |
| B2 autenticação recente | 68–71 `assertEquals(RECENT_AUTHENTICATION_REQUIRED, ...)` | saque e mudança bancária exigem autenticação recente; leitura preservada |

Todas as asserções novas correspondem a critérios acima; sem testes especulativos.
A política recebe fatos atualizados: verificação HTTP/JDBC de autenticação e revogação pertence às tarefas seguintes.
A matriz trial/planos depende da composição T06; T01 fornece apenas a porta e não afirma implementar o trial.

## T02 — schema financeiro aditivo

Gate: JDK 21, `:features:receivables:check :architecture-tests:test :bootstrap:test --tests '*SubscriptionsMigrationOnBootstrapClasspathIntegrationTest'`, exit 0.
6 testes PostgreSQL reais, 5 de domínio, 20 de arquitetura e migração integrada do bootstrap passam.

Asserções em `ReceivablesSchemaIntegrationTest.kt`; cada linha abaixo também é o mapeamento reverso para B1/B2/B4/B5/B6:

| Critério | Linha / asserção | Resultado |
|---|---|---|
| B1 migração aditiva | 25–29 `assertEquals(2/0/2300/17, ...)` (V49 + V50 após a entrega de onboarding) | aplica a base e preserva cobrança manual; cria 17 tabelas financeiras |
| B2 identidade única | 37–39 `assertEquals("23505", ...sqlState)` / contagem 1 | titular e CPF/CNPJ não duplicam conta |
| B4 deduplicação por conta | 51–53 SQLSTATE 23505 / contagem 2 | evento duplicado rejeitado só na mesma conta |
| B5 competência única | 69–71 SQLSTATE 23505 / contagem 1 | mesma competência não reaparece após troca de conta |
| B4/B6 ledger imutável | 82–85 SQLSTATE 23505/P0001 / soma 10000 | recebimento e histórico não duplicam nem são apagados |
| B2 isolamento de saque | 101–107 SQLSTATE 23503 / contagem 0 | destino de outra conta rejeitado pelo banco |

Sem alterações em testes existentes. Valores comerciais não populados. Testes de concorrência de operações pertencem a T03.

## T03 — segredos e operações

Gate `:features:receivables:check :architecture-tests:test`, JDK 21, exit 0.
8 testes unitários (3 novos), 10 PostgreSQL (4 novos), 20 de arquitetura.

Mapeamento critério ↔ asserções (todas as novas asserções pertencem a B2 ou B4):

### FinancialSecretsTest.kt

- `FinancialSecretsTest.kt:20` — `assertFalse(first.contains("secret-api-key"))`
- `FinancialSecretsTest.kt:21` — `assertNotEquals(first, second)`
- `FinancialSecretsTest.kt:22` — `assertEquals("secret-api-key", secrets.decrypt(account, "provider-key", first))`
- `FinancialSecretsTest.kt:23` — `assertFailsWith<IllegalStateException> { secrets.decrypt(UUID.randomUUID(), "provider-key", first) }`
- `FinancialSecretsTest.kt:24` — `assertFailsWith<IllegalStateException> { secrets.decrypt(account, "legal-data", first) }`
- `FinancialSecretsTest.kt:29` — `assertEquals("Financial secret could not be authenticated", assertFailsWith<IllegalStateException> {`
- `FinancialSecretsTest.kt:38` — `assertEquals("secret-api-key", rotated.decrypt(account, "provider-key", old))`
- `FinancialSecretsTest.kt:41` — `assertFalse(failure.toString().contains("secret-api-key"))`
- `FinancialSecretsTest.kt:42` — `assertEquals(null, failure.cause)`
- `FinancialSecretsTest.kt:48` — `assertEquals(secrets.legalIdentityDigest(cpf), secrets.legalIdentityDigest(cpf))`
- `FinancialSecretsTest.kt:49` — `assertFalse(secrets.legalIdentityDigest(cpf).contains(cpf))`
- `FinancialSecretsTest.kt:50` — `assertNotEquals(secrets.legalIdentityDigest(cpf), secrets.legalIdentityDigest("12345678902"))`
- `FinancialSecretsTest.kt:51` — `assertNotEquals(secrets.legalIdentityDigest(cpf),`

### FinancialOperationsIntegrationTest.kt

- `FinancialOperationsIntegrationTest.kt:21` — `assertTrue(store.finish(claim, OperationStatus.SUCCEEDED, "pay_1", now, now))`
- `FinancialOperationsIntegrationTest.kt:23` — `assertEquals(operation.id, resumed.id)`
- `FinancialOperationsIntegrationTest.kt:24` — `assertEquals(OperationStatus.SUCCEEDED, resumed.status)`
- `FinancialOperationsIntegrationTest.kt:25` — `assertEquals("pay_1", resumed.providerReference)`
- `FinancialOperationsIntegrationTest.kt:26` — `assertFailsWith<FinancialRequestConflict> { store.register(operation.copy(requestDigest = "b".repeat(64)), now) }`
- `FinancialOperationsIntegrationTest.kt:27` — `assertFailsWith<FinancialRequestConflict> { store.register(operation.copy(actorUserId = UUID.randomUUID()), now) }`
- `FinancialOperationsIntegrationTest.kt:33` — `assertNull(store.claim(UUID.randomUUID(), operation.id, now, now.plusSeconds(90)))`
- `FinancialOperationsIntegrationTest.kt:38` — `assertEquals(1, results.size)`
- `FinancialOperationsIntegrationTest.kt:39` — `assertFalse(results.single().recoveryOnly)`
- `FinancialOperationsIntegrationTest.kt:40` — `assertEquals(operation.id, results.single().operation.id)`
- `FinancialOperationsIntegrationTest.kt:48` — `assertNull(store.claim(operation.accountId, operation.id, now.plusSeconds(89), now.plusSeconds(180)))`
- `FinancialOperationsIntegrationTest.kt:50` — `assertTrue(recovery.recoveryOnly)`
- `FinancialOperationsIntegrationTest.kt:51` — `assertTrue(store.finish(recovery, OperationStatus.SUCCEEDED, "pay_recovered", now.plusSeconds(92), now))`
- `FinancialOperationsIntegrationTest.kt:52` — `assertFalse(store.finish(first, OperationStatus.REJECTED, null, now.plusSeconds(93), now))`
- `FinancialOperationsIntegrationTest.kt:53` — `assertEquals("pay_recovered", store.register(operation, now).providerReference)`
- `FinancialOperationsIntegrationTest.kt:70` — `assertTrue(runner.run(operation.accountId, operation.id, now))`
- `FinancialOperationsIntegrationTest.kt:71` — `assertEquals(OperationStatus.UNKNOWN, store.register(operation, now).status)`
- `FinancialOperationsIntegrationTest.kt:72` — `assertFalse(runner.run(operation.accountId, operation.id, now.plusSeconds(30)))`
- `FinancialOperationsIntegrationTest.kt:73` — `assertTrue(runner.run(operation.accountId, operation.id, now.plusSeconds(60)))`
- `FinancialOperationsIntegrationTest.kt:74` — `assertEquals(OperationStatus.UNKNOWN, store.register(operation, now).status)`
- `FinancialOperationsIntegrationTest.kt:76` — `assertTrue(runner.run(operation.accountId, operation.id, now.plusSeconds(120)))`
- `FinancialOperationsIntegrationTest.kt:77` — `assertEquals(listOf("pay_1"), remotePayments)`
- `FinancialOperationsIntegrationTest.kt:78` — `assertEquals(OperationStatus.SUCCEEDED, store.register(operation, now).status)`
- `FinancialOperationsIntegrationTest.kt:79` — `assertEquals("pay_1", store.register(operation, now).providerReference)`
- `FinancialOperationsIntegrationTest.kt:80` — `assertFalse(runner.run(operation.accountId, operation.id, now.plusSeconds(180)))`

B2: ciphertext não contém chave, nonce aleatório, leitura exige conta/finalidade/chave corretas,
modificação é detectada, rotação mantém leitura, falha não expõe causa ou segredo e digest é keyed.
B4: resultado persistido retorna mesmo ID, mudança de conteúdo/ator conflita, uma execução concorrente,
conta alheia rejeitada, lease abandonado só recupera, worker obsoleto não sobrescreve,
timeout/consulta inconclusiva mantêm UNKNOWN; recuperação encontra exatamente um pagamento remoto.
Nenhuma asserção de comportamento existente enfraquecida; sem teste de biblioteca isolado ou asserção somente de número de chamadas.

## T04 — entrega parcial de onboarding

Gate `:features:receivables:check :architecture-tests:test`, JDK 21, exit 0:
8 testes unitários, 17 integração (7 novos), 20 arquitetura.
Migração agregada do bootstrap também passou com a alteração V47 local.

Critérios cobertos ↔ testes:
- B2 ativação voluntária: `voluntary consent...` garante ausência de conta e chamada externa sem aceite/termo publicado.
- B2 retomada: `registration retry...` mantém ID, persiste chave cifrada e um POST remoto; conteúdo diferente com mesmo requestId conflita.
- B2/B4 resposta perdida: `lost account creation...` preserva UNKNOWN, sem nova conta remota.
- B2 documentação: `documents wait...` exige 15 segundos, consulta com chave da subconta, aprovação apenas pelo general autenticado e não libera piloto.
- B2 upload: `onboarding link forbids...` impede upload de grupo com link; envia multipart do documento permitido e mantém análise separada de aprovação.
- B2 liberação: `BaaS switch...` bloqueia criação nova e preserva leitura de conta existente sem envenenar operação READY.
- B1 HTTP: `accounts HTTP flow...` prova contratos 400/404/409/202/200, requestId, isolamento de ator, ausência de chave/CPF em respostas.

Correções motivadas por teste: DTO passou a declarar JsonCreator/JsonProperty como os DTOs existentes; teste de renda passou a comparar o valor decimal exato, pois JSON numérico 2500.00 e 2500.0 representam a mesma quantia (nenhuma mudança no valor esperado).

Asserções localizadas (mapeamento reverso nos critérios acima):
- `FinancialOnboardingIntegrationTest.kt:29` — `assertEquals(FinancialError.INVALID_INPUT, (service.begin(request, false, "v1", registration(), now) as FinancialResult.Failure).error)`
- `FinancialOnboardingIntegrationTest.kt:30` — `assertEquals(FinancialError.INVALID_INPUT, (service.begin(request, true, "missing", registration(), now) as FinancialResult.Failure).error)`
- `FinancialOnboardingIntegrationTest.kt:31` — `assertNull(store.findOwned(owner))`
- `FinancialOnboardingIntegrationTest.kt:32` — `assertEquals(0, server.requestCount)`
- `FinancialOnboardingIntegrationTest.kt:33` — `assertEquals(FinancialError.NOT_FOUND, (service.refresh(request, now) as FinancialResult.Failure).error)`
- `FinancialOnboardingIntegrationTest.kt:40` — `assertEquals(RegistrationStatus.INCOMPLETE, first.value.registration)`
- `FinancialOnboardingIntegrationTest.kt:41` — `assertFalse(first.value.newOperationsEnabled)`
- `FinancialOnboardingIntegrationTest.kt:42` — `assertTrue(service.provision(first.value.id, now))`
- `FinancialOnboardingIntegrationTest.kt:44` — `assertEquals("POST", post.method)`
- `FinancialOnboardingIntegrationTest.kt:45` — `assertEquals("/v3/accounts", post.path)`
- `FinancialOnboardingIntegrationTest.kt:46` — `assertEquals("platform-secret", post.getHeader("access_token"))`
- `FinancialOnboardingIntegrationTest.kt:48` — `assertEquals("12345678901", body["cpfCnpj"].asText())`
- `FinancialOnboardingIntegrationTest.kt:49` — `assertEquals(0, java.math.BigDecimal("2500.00").compareTo(body["incomeValue"].decimalValue()))`
- `FinancialOnboardingIntegrationTest.kt:50` — `assertEquals("1990-01-01", body["birthDate"].asText())`
- `FinancialOnboardingIntegrationTest.kt:51` — `assertFalse(body.has("companyType"))`
- `FinancialOnboardingIntegrationTest.kt:53` — `assertEquals(first.value.id, second.value.id)`
- `FinancialOnboardingIntegrationTest.kt:54` — `assertEquals(RegistrationStatus.UNDER_REVIEW, second.value.registration)`
- `FinancialOnboardingIntegrationTest.kt:55` — `assertFalse(service.provision(first.value.id, now.plusSeconds(60)))`
- `FinancialOnboardingIntegrationTest.kt:56` — `assertEquals("subaccount-secret", store.credentials(first.value.id)!!.apiKey)`
- `FinancialOnboardingIntegrationTest.kt:57` — `assertEquals("acc_1", store.creationOperation(first.value.id).providerReference)`
- `FinancialOnboardingIntegrationTest.kt:58` — `assertEquals(1, server.requestCount)`
- `FinancialOnboardingIntegrationTest.kt:59` — `assertEquals(FinancialError.CONFLICT, (service.begin(request, true, "v1", registration("Changed"), now) as FinancialResult.Failure).error)`
- `FinancialOnboardingIntegrationTest.kt:60` — `assertEquals(FinancialError.CONFLICT, (service.begin(request.copy(actorUserId = UUID.randomUUID()), true,`
- `FinancialOnboardingIntegrationTest.kt:68` — `assertTrue(service.provision(account.id, now))`
- `FinancialOnboardingIntegrationTest.kt:69` — `assertEquals(OperationStatus.UNKNOWN, store.creationOperation(account.id).status)`
- `FinancialOnboardingIntegrationTest.kt:70` — `assertTrue(service.provision(account.id, now.plusSeconds(60)))`
- `FinancialOnboardingIntegrationTest.kt:71` — `assertEquals(OperationStatus.UNKNOWN, store.creationOperation(account.id).status)`
- `FinancialOnboardingIntegrationTest.kt:72` — `assertNull(store.credentials(account.id))`
- `FinancialOnboardingIntegrationTest.kt:73` — `assertEquals(1, server.requestCount)`
- `FinancialOnboardingIntegrationTest.kt:80` — `assertEquals(FinancialError.RESULT_PENDING, (service.refresh(request, now.plusSeconds(14)) as FinancialResult.Failure).error)`
- `FinancialOnboardingIntegrationTest.kt:81` — `assertEquals(0, server.requestCount)`
- `FinancialOnboardingIntegrationTest.kt:85` — `assertEquals(listOf(FinancialDocument("doc_1", "IDENTIFICATION", "PENDING", "https://asaas.com/onboarding/test")), documents.value)`
- `FinancialOnboardingIntegrationTest.kt:86` — `assertEquals(RegistrationStatus.APPROVED, store.findOwned(owner)!!.registration)`
- `FinancialOnboardingIntegrationTest.kt:87` — `assertFalse(store.findOwned(owner)!!.newOperationsEnabled)`
- `FinancialOnboardingIntegrationTest.kt:88` — `assertEquals("subaccount-secret", server.takeRequest().getHeader("access_token"))`
- `FinancialOnboardingIntegrationTest.kt:89` — `assertEquals("/v3/myAccount/status", server.takeRequest().path)`
- `FinancialOnboardingIntegrationTest.kt:92` — `assertEquals(FinancialError.PROVIDER_UNAVAILABLE, (service.refresh(request, now.plusSeconds(30)) as FinancialResult.Failure).error)`
- `FinancialOnboardingIntegrationTest.kt:102` — `assertEquals(FinancialError.INVALID_INPUT, (rejected as FinancialResult.Failure).error)`
- `FinancialOnboardingIntegrationTest.kt:103` — `assertEquals(RegistrationStatus.CORRECTION_REQUIRED, store.findOwned(owner)!!.registration)`
- `FinancialOnboardingIntegrationTest.kt:108` — `assertIs<FinancialResult.Success<Unit>>(service.upload(request, "doc_2", "IDENTIFICATION", "application/pdf",`
- `FinancialOnboardingIntegrationTest.kt:112` — `assertEquals("/v3/myAccount/documents/doc_2", upload.path)`
- `FinancialOnboardingIntegrationTest.kt:113` — `assertEquals("subaccount-secret", upload.getHeader("access_token"))`
- `FinancialOnboardingIntegrationTest.kt:114` — `assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))`
- `FinancialOnboardingIntegrationTest.kt:116` — `assertTrue(body.contains("name=\"documentFile\""))`
- `FinancialOnboardingIntegrationTest.kt:117` — `assertTrue(body.contains("pdf-content"))`
- `FinancialOnboardingIntegrationTest.kt:118` — `assertEquals(RegistrationStatus.UNDER_REVIEW, store.findOwned(owner)!!.registration)`
- `FinancialOnboardingIntegrationTest.kt:123` — `assertEquals(FinancialError.PROVIDER_UNAVAILABLE, (service.begin(request, true, "v1", registration(), now) as FinancialResult.Failure).error)`
- `FinancialOnboardingIntegrationTest.kt:124` — `assertNull(store.findOwned(owner))`
- `FinancialOnboardingIntegrationTest.kt:126` — `assertEquals(existing.id, (service.begin(request, true, "v1", registration(), now) as FinancialResult.Success).value.id)`
- `FinancialOnboardingIntegrationTest.kt:127` — `assertFalse(service.provision(existing.id, now))`
- `FinancialOnboardingIntegrationTest.kt:128` — `assertEquals(OperationStatus.READY, store.creationOperation(existing.id).status)`
- `FinancialOnboardingIntegrationTest.kt:129` — `assertEquals(0, server.requestCount)`
- `FinancialOnboardingIntegrationTest.kt:150` — `assertFalse(rs.getString(1).contains("12345678901"))`
- `FinancialOnboardingIntegrationTest.kt:151` — `assertFalse(rs.getString(2).orEmpty().contains("subaccount-secret"))`
- `FinancialAccountsHttpIntegrationTest.kt:63` — `assertEquals(404, missing.status)`
- `FinancialAccountsHttpIntegrationTest.kt:64` — `assertEquals("NOT_FOUND", mapper.readTree(missing.contentAsString)["error"].asText())`
- `FinancialAccountsHttpIntegrationTest.kt:68` — `assertEquals(400, rejected.status)`
- `FinancialAccountsHttpIntegrationTest.kt:69` — `assertEquals(requestId.toString(), mapper.readTree(rejected.contentAsString)["requestId"].asText())`
- `FinancialAccountsHttpIntegrationTest.kt:70` — `assertNull(store.findOwned(owner))`
- `FinancialAccountsHttpIntegrationTest.kt:75` — `assertEquals(200, created.status)`
- `FinancialAccountsHttpIntegrationTest.kt:76` — `assertEquals("UNDER_REVIEW", mapper.readTree(created.contentAsString)["value"]["registration"].asText())`
- `FinancialAccountsHttpIntegrationTest.kt:77` — `assertFalse(created.contentAsString.contains("subaccount-secret"))`
- `FinancialAccountsHttpIntegrationTest.kt:78` — `assertFalse(created.contentAsString.contains("12345678901"))`
- `FinancialAccountsHttpIntegrationTest.kt:79` — `assertEquals(200, create().status)`
- `FinancialAccountsHttpIntegrationTest.kt:80` — `assertEquals(1, server.requestCount)`
- `FinancialAccountsHttpIntegrationTest.kt:82` — `assertEquals(409, create().status)`
- `FinancialAccountsHttpIntegrationTest.kt:84` — `assertEquals(202, pending.status)`
- `FinancialAccountsHttpIntegrationTest.kt:85` — `assertEquals("RESULT_PENDING", mapper.readTree(pending.contentAsString)["error"].asText())`
- `FinancialAccountsHttpIntegrationTest.kt:89` — `assertEquals(400, invalidUpload.status)`
- `FinancialAccountsHttpIntegrationTest.kt:91` — `assertEquals(404, mvc.perform(get("/api/receivables/accounts/me")).andReturn().response.status)`
- `FinancialAccountsHttpIntegrationTest.kt:92` — `assertEquals(404, mvc.perform(get("/api/receivables/accounts/me/documents")).andReturn().response.status)`
- `FinancialAccountsHttpIntegrationTest.kt:93` — `assertNotNull(store.findOwned(owner))`

T04 NÃO concluída integralmente; lacunas estão em tasks.md e STATE.md. Não houve homologação externa.

## T07a — núcleo independente de tarifas

Adiantado por ser domínio puro independente da ativação T06. Não há nova cobrança nem exposição HTTP.
Gate `:features:receivables:test :architecture-tests:test`, JDK21, exit 0: 12 unitários (4 novos) e 20 arquitetura.

`FeeCalculatorTest.kt` mapeia exclusivamente B3:
- 15–24: base=10000, comissão=300, provedor=358, taxas=658, total=10658, líquido=10000; ID, termos e meio preservados; split fixo=3.00.
- 30–38: comissão arredondada=60, processamento=125, total mínimo=2184 e microvalor sem centavo desnecessário.
- 44–47: custos fixos sem percentuais e tabela explicitamente zerada sem tarifa inventada.
- 53–60: meio centavo, configuração inválida, valor inválido e overflow não geram cobrança inválida.

Todos os números são fixtures de teste. HALF_UP é a política explícita do núcleo; a tabela operacional
precisará corresponder às condições reais do provedor, com divergências conciliadas em T09.
Publicação versionada, seleção da tabela vigente, simulação HTTP e snapshot da emissão seguem pendentes em T07.

## T05a — delegações e revogação transacional

Gate JDK 21: `:features:receivables:check :features:groups:test :architecture-tests:test :bootstrap:test --tests '*FinancialDelegationIntegrationTest'`, exit 0.
12 unitários e 17 integrações de recebimentos, 639 testes de grupos, 20 arquitetura, 6 integrações novas de delegação.

B2 ↔ `FinancialDelegationIntegrationTest.kt`:
- Concessão exige titular, administrador atual, termos publicados e aviso de alcance integral.
- Delegado pode listar conta/autorizações, nunca conceder ou revogar autoridade.
- Replay de concessão anterior à revogação não a restaura; conteúdo divergente conflita.
- Rebaixamento de papel e saída persistem revoked_at na transação de grupos.
- Rollback desfaz tanto papel quanto revogação; promoção posterior não restaura autorização.
- Diretório continua para titular após exclusão do grupo, e exclui delegado revogado na sessão já aberta.
- Rotas MVC reais de lista/grant/revoke/diretório cobrem consentimento, termos inexistentes, conflito, sucesso e ausência de autorização.

Asserções localizadas, todas vinculadas aos critérios acima:
- `FinancialDelegationIntegrationTest.kt:51` — `assertEquals(FinancialError.INVALID_INPUT, (f.service.grant(f.account, request, f.admin, "v1", false, now) as FinancialResult.Failure).error)`
- `FinancialDelegationIntegrationTest.kt:52` — `assertNull(f.accounts.findDelegation(f.account, f.admin))`
- `FinancialDelegationIntegrationTest.kt:53` — `assertEquals(FinancialError.INVALID_INPUT, (f.service.grant(f.account, request, UUID.randomUUID(), "v1", true, now) as FinancialResult.Failure).error)`
- `FinancialDelegationIntegrationTest.kt:55` — `assertEquals(f.admin, granted.value.userId)`
- `FinancialDelegationIntegrationTest.kt:56` — `assertEquals(f.account, granted.value.accountId)`
- `FinancialDelegationIntegrationTest.kt:57` — `assertNull(granted.value.revokedAt)`
- `FinancialDelegationIntegrationTest.kt:59` — `assertEquals(1, (f.service.list(f.account, delegatedRequest) as FinancialResult.Success).value.size)`
- `FinancialDelegationIntegrationTest.kt:60` — `assertEquals(listOf(f.account), (f.service.accounts(delegatedRequest) as FinancialResult.Success).value.map { it.id })`
- `FinancialDelegationIntegrationTest.kt:61` — `assertEquals(FinancialError.NOT_FOUND, (f.service.grant(f.account, delegatedRequest, f.owner, "v1", true, now) as FinancialResult.Failure).error)`
- `FinancialDelegationIntegrationTest.kt:62` — `assertEquals(FinancialError.NOT_FOUND, (f.service.revoke(f.account, delegatedRequest, f.admin, now) as FinancialResult.Failure).error)`
- `FinancialDelegationIntegrationTest.kt:63` — `assertIs<FinancialResult.Success<Unit>>(f.service.revoke(f.account, request.copy(requestId = UUID.randomUUID()), f.admin, now.plusSeconds(5)))`
- `FinancialDelegationIntegrationTest.kt:65` — `assertEquals(now.plusSeconds(5), replay.value.revokedAt)`
- `FinancialDelegationIntegrationTest.kt:66` — `assertEquals(FinancialError.NOT_FOUND, (f.service.list(f.account, delegatedRequest) as FinancialResult.Failure).error)`
- `FinancialDelegationIntegrationTest.kt:67` — `assertEquals(emptyList(), (f.service.accounts(delegatedRequest) as FinancialResult.Success).value)`
- `FinancialDelegationIntegrationTest.kt:68` — `assertEquals(1, (f.service.list(f.account, request) as FinancialResult.Success).value.size)`
- `FinancialDelegationIntegrationTest.kt:76` — `assertEquals("ATHLETE", f.role())`
- `FinancialDelegationIntegrationTest.kt:77` — `assertNotNull(f.accounts.findDelegation(f.account, f.admin)!!.revokedAt)`
- `FinancialDelegationIntegrationTest.kt:79` — `assertEquals("ADMIN", f.role())`
- `FinancialDelegationIntegrationTest.kt:80` — `assertEquals(FinancialError.NOT_FOUND, (f.service.list(f.account,`
- `FinancialDelegationIntegrationTest.kt:88` — `assertIs<FinancialResult.Success<*>>(f.service.list(f.account, existingSession))`
- `FinancialDelegationIntegrationTest.kt:91` — `assertNotNull(f.accounts.findDelegation(f.account, f.admin)!!.revokedAt)`
- `FinancialDelegationIntegrationTest.kt:92` — `assertEquals(FinancialError.NOT_FOUND, (f.service.list(f.account, existingSession) as FinancialResult.Failure).error)`
- `FinancialDelegationIntegrationTest.kt:94` — `assertIs<FinancialResult.Success<*>>(f.service.list(f.account, FinancialRequest(UUID.randomUUID(), f.owner)))`
- `FinancialDelegationIntegrationTest.kt:95` — `assertEquals(f.owner, f.accounts.findById(f.account)!!.ownerUserId)`
- `FinancialDelegationIntegrationTest.kt:96` — `assertEquals(listOf(f.account), (f.service.accounts(FinancialRequest(UUID.randomUUID(), f.owner)) as FinancialResult.Success).value.map { it.id })`
- `FinancialDelegationIntegrationTest.kt:106` — `assertFailsWith<IllegalStateException> { f.change(failure).execute(f.owner, f.group, f.admin, PersistedMembershipRole.ATHLETE) }`
- `FinancialDelegationIntegrationTest.kt:107` — `assertEquals("ADMIN", f.role())`
- `FinancialDelegationIntegrationTest.kt:108` — `assertNull(f.accounts.findDelegation(f.account, f.admin)!!.revokedAt)`
- `FinancialDelegationIntegrationTest.kt:109` — `assertIs<FinancialResult.Success<*>>(f.service.list(f.account, FinancialRequest(UUID.randomUUID(), f.admin)))`
- `FinancialDelegationIntegrationTest.kt:120` — `assertEquals(404, controller.grant(stranger, f.account, body).statusCode.value())`
- `FinancialDelegationIntegrationTest.kt:122` — `assertEquals(200, grant.statusCode.value())`
- `FinancialDelegationIntegrationTest.kt:123` — `assertEquals(requestId, (grant.body as FinancialResult.Success<*>).requestId)`
- `FinancialDelegationIntegrationTest.kt:124` — `assertEquals(200, controller.list(RequestIdentity(f.admin.toString()), f.account).statusCode.value())`
- `FinancialDelegationIntegrationTest.kt:125` — `assertEquals(404, controller.revoke(stranger, f.account, f.admin, UUID.randomUUID()).statusCode.value())`
- `FinancialDelegationIntegrationTest.kt:126` — `assertEquals(200, controller.revoke(owner, f.account, f.admin, UUID.randomUUID()).statusCode.value())`
- `FinancialDelegationIntegrationTest.kt:127` — `assertEquals(404, controller.list(RequestIdentity(f.admin.toString()), f.account).statusCode.value())`
- `FinancialDelegationIntegrationTest.kt:152` — `assertEquals(400, grant(false).status)`
- `FinancialDelegationIntegrationTest.kt:153` — `assertEquals(400, grant(true, "unknown").status)`
- `FinancialDelegationIntegrationTest.kt:155` — `assertEquals(200, granted.status)`
- `FinancialDelegationIntegrationTest.kt:156` — `assertTrue(granted.contentAsString.contains(requestId.toString()))`
- `FinancialDelegationIntegrationTest.kt:157` — `assertEquals(409, grant(true, "changed-terms").status)`
- `FinancialDelegationIntegrationTest.kt:159` — `assertEquals(200, mvc.perform(get(url)).andReturn().response.status)`
- `FinancialDelegationIntegrationTest.kt:160` — `assertEquals(404, grant(true).status)`
- `FinancialDelegationIntegrationTest.kt:161` — `assertEquals(404, mvc.perform(delete("$url/${f.admin}").param("requestId", UUID.randomUUID().toString())).andReturn().response.status)`
- `FinancialDelegationIntegrationTest.kt:163` — `assertEquals(200, directory.status)`
- `FinancialDelegationIntegrationTest.kt:164` — `assertTrue(directory.contentAsString.contains(f.account.toString()))`
- `FinancialDelegationIntegrationTest.kt:166` — `assertEquals(200, mvc.perform(delete("$url/${f.admin}").param("requestId", UUID.randomUUID().toString())).andReturn().response.status)`
- `FinancialDelegationIntegrationTest.kt:168` — `assertEquals(404, mvc.perform(get(url)).andReturn().response.status)`
- `FinancialDelegationIntegrationTest.kt:169` — `assertFalse(mvc.perform(get("/api/receivables/accounts")).andReturn().response.contentAsString.contains(f.account.toString()))`

Permissões nas futuras rotas de cobrança/saque/reembolso serão verificadas nas tarefas que criam essas rotas; não existem ainda.

## Separação das entregas e casos adicionais de delegação

Para permitir merge/deploy da base antes do onboarding sem alterar checksum de migração,
provider_created_at foi movido para V50. O teste de migração passou a esperar duas
migrações após baseline46; a preservação de dados e a reaplicação sem alterações permanecem verificadas.

Foram adicionados dois cenários B2 à suíte de delegação: remoção pelo titular (além da saída voluntária)
e troca do dono do grupo sem transferência de conta/histórico. Oito testes de delegação passam.

## Integração com a main e contrato central

Rebase sobre 6f86f8f6 incorporou o trial desenvolvido em paralelo e sua correção
preexistente da fronteira de exceções de subscriptions. V48 passou a ser trial,
V49 base financeira, V50 data de criação remota. O teste agregado valida o conjunto.

ReceivablesEligibilityTest tem quatro testes: trial ativo/expirado, Titular/Organizador/
Ilimitado, corte exato de downgrade e limites de cancelamento/inadimplência. O adapter
não inicia trial nem calcula sua duração. Ativação e execução do corte continuam pendentes.

Gate após rebase: receivables check, subscriptions test/integrationTest, groups test,
architecture-tests test e bootstrap (delegação, migração agregada, criação e endpoint
de trial) passaram. O gate isolado de elegibilidade também passou.

Sete mutações foram detectadas em cópia temporária: ignorar corte comercial; aceitar
delegado revogado; remover conta do AAD; reexecutar operação incerta; ignorar consentimento;
calcular comissão com taxa do provedor; remover callback transacional de revogação.
Nenhuma dessas mutações foi aplicada à árvore de trabalho.

Gate amplo final após integração: 381 bootstrap + 12 unitários receivables + 17 integração receivables + 20 arquitetura, zero falhas/erros/ignorados; exit 0.

## T07 — catálogo e simulação HTTP

FinancialConditionsIntegrationTest adiciona três testes derivados de B3/A2:
- `simulation has no fallback fee and creates no account acceptance or debt`: ausência de
  configuração retorna CONFIGURATION_UNAVAILABLE, quote em centavos e nenhuma escrita financeira.
- `new quotes use effective published fees and keep historical terms accessible`: limite exato
  de vigência, publicação de tarifa/termos e consulta de condições históricas.
- `HTTP rejects fractional missing and overflowing cents and returns the request identifier`:
  contrato HTTP real, rejeição de frações e overflow, requestId, quote e termos publicados.

Gate JDK 21: receivables check (12 unitários + 20 integração), architecture-tests test
(20 casos) e bootstrap compileKotlin, exit 0. Sem tarifa padrão ou publicação real.
A simulação não garante autorização de emissão: aprovação, plano e grupo serão validados
na emissão. A disponibilidade desta consulta é intencional mesmo antes do cadastro.
