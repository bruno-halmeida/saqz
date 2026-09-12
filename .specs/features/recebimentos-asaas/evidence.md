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
| B1 migração aditiva | 25–29 `assertEquals(1/0/2300/17, ...)` | migra uma vez; preserva cobrança manual; cria 17 tabelas financeiras |
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
Nenhum teste existente alterado; sem teste de biblioteca isolado ou asserção somente de número de chamadas.
