# T04 final credential recovery — independent verification

Date: 2026-09-13. Verifier dispatch: `ctx_c5fdbfa9e927`, task `task_e2a220467cd6`; author dispatch: `ctx_895f1f42aa79`, plus coordinator-owned bootstrap command integration.

**PASS — final T04 implementation and fixes independently verified.** The synchronized frozen snapshot passed 75 selected tests with zero failures/errors/skips, including the runnable backend command; 11 actual scratch source fault injections each failed behaviorally and passed after restoration. No unresolved implementation finding remains in this bounded review. All work is bounded to T04. The verifier changed no production source, ran no Git mutations, spawned no agents, and contacted no financial API. Provider traffic went only to loopback fixtures; PostgreSQL databases and fault injections were isolated under `/tmp/t04-credential-verifier`. Workspace writes are limited to this report.

## Contract and source assessment

The previous `OnboardFinancialAccount.provision` recovery confirms CREATE_ACCOUNT only when local credentials already exist; otherwise UNKNOWN remains unresolved without another provider create. This is preserved. The new operational import bridges that missing-credential state using an externally obtained key; it does not rotate an existing local key or create another provider resource.

Official public documentation checked independently:

- [Listar subcontas](https://docs.asaas.com/reference/listar-subcontas): authenticated parent lists linked subaccounts and supports `cpfCnpj`; require complete unique response and exact provider id, wallet and normalized legal identity.
- [Recuperar dados comerciais](https://docs.asaas.com/reference/recuperar-dados-comerciais): candidate-key GET returns the authenticated account’s commercial identity.
- [Recuperar WalletId](https://docs.asaas.com/reference/recuperar-walletid): GET identifies the authenticated account’s wallet; the official embedded OpenAPI specifies `hasMore`, `totalCount`, `data[].id`. The retrieved markdown/OpenAPI is preserved at `/tmp/t04-credential-verifier/wallet-doc.md`.
- [Gerenciamento de chaves de API de subcontas](https://docs.asaas.com/docs/gerenciamento-de-chaves-de-api-de-subcontas): a lost key value cannot be retrieved; issuance is a separate authorized provider action, subject to temporary management enablement, whitelist and eligibility. Documentation is preserved at `/tmp/t04-credential-verifier/key-management-doc.md`.

V63 creates encrypted recovery intent plus immutable REQUESTED/IMPORTED events. Request identity includes account, original creation operation, owner, operator and remote references; changed secret or identity conflicts. Account and operation are rechecked under locks after remote validation, including account version, original actor, existing provider references and absence of credentials. Import writes account credential, original operation completion and immutable event atomically. Event write failure must roll back the key and operation. No new public controller, secret request DTO, provider mutation capability or scheduler is introduced.

The bootstrap main dispatches `recover-receivables-credential` before normal Spring startup. Its explicit NON_WEB context has no component scanning, auto-configuration, migrations or jobs. It accepts identifiers in argv and candidate secret through bounded console/stdin only; malformed/unknown/duplicate options are rejected before context creation. Typed output and input-buffer clearing are tested. Operator authorization is deployment/host/vault access, not trust in the supplied audit UUID; this boundary is explicit in the runbook.

The final runbook now documents manual external key issuance, exact runnable invocation, original deployment configuration, stdin/console handling, unchanged UNKNOWN on failed proof, retry semantics and result/exit mapping. It does not promise automatic issuance or access to the lost key.

## Independent behavioral evidence

Scratch backend: `/tmp/t04-credential-verifier/backend`; JDK21 resolved with `/usr/libexec/java_home -v 21`. The verifier authored three scratch-only test classes:

| Class | Coverage |
| --- | --- |
| `IndependentCredentialProviderTest` | Exact proof; foreign provider account, parent wallet, parent/candidate legal identity and credential wallet; incomplete/ambiguous list; GET method, empty body and correct parent/candidate authentication; sanitized failure; malformed total-count overflow regression. |
| `IndependentCredentialStoreTest` | Exact import/replay; changed request secret/operator; foreign local owner/original operation; known foreign references; existing-secret rejection; owner/version change during proof; durable timeout/mismatch and retry; simultaneous same/different requests; encrypted intent; no payment enablement; immutable audit. |
| `IndependentCredentialEntrypointTest` | Builds and runs actual backend bootJar subprocess with disposable PostgreSQL and loopback HTTP fixture; supplies key by stdin; checks RECOVERED, ALREADY_RECOVERED, CONFLICT and invalid secret argv; only three authenticated GETs; no secret in output; original identity preserved. Configured web port is occupied, so accidental normal web startup cannot silently pass. |

A genuine HTTP NO_RESPONSE fixture exercises the default 30-second request timeout and asserts UNAVAILABLE, PENDING intent, no local credential and original CREATE_ACCOUNT still UNKNOWN. Separate provider-exception injection checks the same invariant and exact retry.

Initial independent baselines passed 10 existing onboarding tests, 7 provider tests, 9 store tests and the packaged-command subprocess test. A transitional combined run is also preserved as failed: it copied new author event tests before the final V63/store snapshot and encountered missing event table errors; this was scratch snapshot skew, not an accepted result. Final synchronized results supersede that run.

## Actual source fault injections

Each mutation changed scratch production source, ran the named independent test, produced an assertion failure (not compilation failure), restored source and reran successfully. The first eight tests were repeated against the immutable-event implementation; three additional mutations then exercised the final audit and proof-guard deltas. Runner: `/tmp/t04-credential-verifier/mutate.py`; machine-readable outcomes: `/tmp/t04-credential-verifier/mutation-results.json`; logs: `<mutation>-mutant.log` and `<mutation>-restored.log` in the same scratch root. The final delta runner and outcomes are `mutate-final-delta.py` and `final-delta-mutation-results.json`.

| Mutation | Deliberately introduced fault | Discriminating test | Result |
| --- | --- | --- | --- |
| M01 | Bypass expected parent account id | `foreignAccountRejected` | Killed; restored pass |
| M02 | Skip candidate legal-owner comparison | `foreignLegalIdentityRejected` | Killed; restored pass |
| M03 | Accept wrong authenticated wallet | `credentialWalletRejected` | Killed; restored pass |
| M04 | Change provider GET to POST | `exactIdentityAndReadOnly` | Killed; restored pass |
| M05 | Ignore changed key on same request | `exactRecoveryAndDuplicateRequest` | Killed; restored pass |
| M06 | Omit local owner recheck after proof | `ownerChangeDuringProviderValidationRejected` | Killed; restored pass |
| M07 | Report success after provider timeout | `timeoutAndMismatchStayUnresolvedAndExactRetrySucceeds` | Killed; restored pass |
| M08 | Return raw key from secret wrapper toString | `providerFailureDoesNotExposeSecrets` | Killed; restored pass |
| M09 | Omit immutable IMPORTED event | `exactRecoveryAndDuplicateRequest` | Killed; restored pass |
| M10 | Reintroduce narrowing total-count conversion | `overflowingCountCannotProveUniqueIdentity` | Killed; restored pass |
| M11 | Accept any 2xx proof response | `http202CannotProveIdentityAtAnyStage` | Killed; restored pass |

## Findings resolved and final snapshot

1. **Independent finding, fixed by coordinator and verified:** `JsonNode.intValue()` silently truncated `totalCount=4294967297` to 1. A single-item parent list incorrectly passed uniqueness despite the incompatible count. The independent test failed against unmodified author source, recorded in `/tmp/t04-credential-verifier/overflow-count-before-fix.log`. Final code uses integral type validation and exact `bigIntegerValue() == BigInteger.ONE`. Both parent and wallet regressions now reject 2^32+1 and 2^64+1; M10 reintroduced the defect and failed again, followed by restored pass.
2. **Coordinator finding, fixed and independently verified:** proof GETs accepted arbitrary 2xx, including 202. Final code requires HTTP 200. Independent tests exercise a valid-looking 202 body at every proof stage; author/coordinator integration tests additionally assert UNKNOWN, PENDING, no credentials, no IMPORTED event and exact stopped read count. M11 reintroduced broad 2xx acceptance and failed, followed by restored pass.

The production delta consists of new recovery application/provider/JDBC files, V63, explicit command/context files, and the main dispatch branch. Existing onboarding source is untouched. New tests expand recovery/command coverage; the existing schema inventory changes from 13 to 14 migrations and 32 to 34 tables for the two V63 tables, without weakening other assertions. The immutable-event delta was inspected and exercised through insertion-failure rollback, rejected UPDATE/DELETE, exact replay and M09 omission detection.

Final independent command (from the scratch backend, JDK21):

```sh
./gradlew :bootstrap:bootJar \
  :features:receivables:integrationTest \
  --tests '*IndependentCredential*' \
  --tests '*FinancialCredentialRecoveryIntegrationTest' \
  --tests '*FinancialOnboardingIntegrationTest' \
  --tests '*ReceivablesSchemaIntegrationTest' \
  :bootstrap:test \
  --tests '*CredentialRecoveryCommandLineTest' \
  --tests '*IndependentCredentialEntrypointTest' \
  :architecture-tests:test --console=plain
```

Result: **BUILD SUCCESSFUL in 45s**. Evidence: `/tmp/t04-credential-verifier/final-frozen-gate.log`, `final-test-summary.json` and preserved XML in `final-frozen-test-results/` (all paths under that scratch root).

| Final class | Passed |
| --- | ---: |
| FinancialCredentialRecoveryIntegrationTest | 14 |
| FinancialOnboardingIntegrationTest | 10 |
| IndependentCredentialProviderTest | 9 |
| IndependentCredentialStoreTest | 10 |
| ReceivablesSchemaIntegrationTest | 6 |
| CredentialRecoveryCommandLineTest | 5 |
| IndependentCredentialEntrypointTest | 1 |
| BackendArchitectureTest | 20 |
| **Total** | **75** |

Final scratch and workspace bytes match for all ten reviewed source/test/migration files below; equality was rechecked after all mutants were restored. SHA-256 inventory is also stored at `/tmp/t04-credential-verifier/verified-source-sha256.txt`.

```text
89fcf3809dc055375006acbfd188b4946661790af576e75597f879a739b196e4  backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/SaqzApplication.kt
c03b535f1ab5b645c215cbc998614fe42ba50d2a7b8073505025af83b341c5a9  backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/operations/CredentialRecoveryCommandLine.kt
8c87fd9d996133dfa85e1703edc1a3f8ca924664ee46b9a702f5ce8bbbddec38  backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/operations/CredentialRecoveryContext.kt
137b707fc8f562f372f8e1a3750c55ecbc1bc7fd6c22d1edec13acacd1ac377a  backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/operations/CredentialRecoveryCommandLineTest.kt
9570025fde3c813fa9c9f2cf3f0e7955e6fee12fd1ed09868f1f077304ed2968  backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/FinancialCredentialRecoveryIntegrationTest.kt
8e6c463c7c46a5de923b8c04198ecbc04f53f93cc87befca3453ebf0089f9f9a  backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/ReceivablesSchemaIntegrationTest.kt
f5a1b26133f620bace9cec677016201bed98d6a922171bba2ca39846c57e47e3  backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasCredentialRecovery.kt
6655967777639e335f250e65e347cc18cba5f333360c3c38e5fc9be611d1a3a7  backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcFinancialCredentialRecovery.kt
3f10af55b4ba761fb86d8a2c8f2d01c62798e2bb8cce5db30c15f7f3d2970ae9  backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/FinancialCredentialRecovery.kt
a9f88565f8ca4159d067a0cf01baf2f2d1b421653f35c2091da9716d83787865  backend/features/receivables/src/main/resources/db/migration/V63__financial_credential_recovery.sql
```

**Disposition:** T04 local implementation gate passes, including coordinator integration and both final proof fixes. Real provider eligibility, manual issuance/capture and authorized homologation remain operational dependencies; this report does not attest a live recovery or deployment. No production database, provider account, financial resource or real credential was read or changed during verification.
