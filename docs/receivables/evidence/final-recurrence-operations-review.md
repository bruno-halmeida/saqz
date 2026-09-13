# Final recurrence and operations backend verification

Date: 2026-09-13\
Verifier: independent dispatched worker `task_1c3196ba9cf8` (not the implementation author)\
Verdict: **PASS**

## Scope and method

This review verifies the backend implementation against `final-recurrence-contract.md`, `final-operations-contract.md`, their author evidence, and the operations runbook. It covers exact-value monthly recurrence, payer consent and ownership, the one-live-recurrence invariant, card hosted checkout, Pix recurrence and renewal, cutoff cancellation, fresh-consent resume, GET-before-write recovery, read-only administrative observation, public terms/return endpoints, and passive residual-cost accounting. It does not exercise the real Asaas service.

The verification ran from an isolated `/tmp` copy assembled from Git `HEAD` plus the working-tree backend files. The reviewed base was `cd0a4a3c19224a5b254a7ff860f6a4f00a60d2d3`. No repository source, Git index, external provider, or credential was mutated by this verifier; only this report was added. At the final integrity check, every recurrence/operations file used by the tests matched the repository; two concurrently changed wallet-only files were outside this review's scope.

## Result by contract area

| Area | Result | Evidence |
|---|---|---|
| Exact cents and immutable quote | PASS | `RecurrencePayments.kt:72-96,131-155` re-quotes before authorization, binds all cent fields and terms into the fingerprint, and passes `totalCents`; provider payloads use exact decimal conversion in `HttpAsaasRecurrenceProvider.kt:47-68`. Mutation M1 was killed. |
| Consent, payer ownership, eligibility | PASS | `RecurrencePayments.kt:77-91` requires explicit `accepted`, valid payer, matching fingerprint, actor ownership on replay, and no existing live recurrence. `:131-145` enforces approved registration, enabled operations/method, owner plan eligibility, rollout, webhook readiness, active member and the configured due day. |
| One live recurrence and resume | PASS | `RecurrencePayments.kt:89` rejects another live recurrence. `:121-129` only resumes a stopped recurrence owned by the actor and routes through `authorize`, therefore requiring a new review fingerprint and explicit acceptance while creating a new recurrence rather than reactivating the old provider object. |
| Card recurring checkout | PASS | `HttpAsaasRecurrenceProvider.kt:60-74` sends `billingTypes=[CREDIT_CARD]`, `chargeTypes=[RECURRENT]`, a monthly subscription description, exact item value, callbacks, and validates an HTTPS Asaas-hosted URL. Mutation M2 was killed. |
| Pix subscription and renewal | PASS | `HttpAsaasRecurrenceProvider.kt:51-59` creates a monthly Pix subscription with exact value. `HttpAsaasPayments.kt:101-111` renews the existing payment by `PUT /payments/{id}` and checks the returned due date. Mutation M4 was killed. |
| Timeout recovery | PASS | `HttpAsaasRecurrenceProvider.kt:77-97` uses provider GETs: known subscription GET, Pix list by `externalReference`, and card payment list by `checkoutSession`; the added verifier test proved card recovery used one GET, linked the returned subscription, and made no POST. |
| Cutoff cancellation | PASS | `HttpAsaasRecurrenceProvider.kt:119-134` first marks the provider subscription `INACTIVE`, deletes only `PENDING`/`OVERDUE` payments strictly after the cutoff, verifies deletion responses, and confirms no future cancellable payment remains. Past-due/on-cutoff obligations remain. Mutation M3 was killed. |
| Read-only admin recovery | PASS | Only uncertain `CREATE_INSTRUMENT` and `CANCEL_INSTRUMENT` are recoverable (`OperationalReceivables.kt:20-25`). Wiring calls `reconcileObserved`; `JdbcPaymentExecution.kt:104-142` performs provider recovery reads, applies authenticated facts, completes stale/unknown operation state, and passively records residual costs. The final wiring test at `ReceivablesRecurrenceIntegrationTest.kt:215-250` proves UNKNOWN-to-SUCCEEDED and zero create/cancel/renew calls. Mutation M6 was killed. |
| Public operations surfaces | PASS | `PublicReceivablesController.kt:31-55` exposes only no-store current/versioned terms and checkout return. Administrative recovery is authenticated and returns `NOT_RECOVERABLE` for forbidden kinds (`AdminReceivablesOperationsController.kt:35-73`). Endpoint tests specifically reject withdrawal recovery before any probe. |
| Passive residual costs; no refund surface | PASS | `HttpAsaasPayments.kt:133-155,183-200` observes provider reversal state, selects only negative `REFUND_REQUEST_FEE` entries correlated by `paymentId`, de-duplicates statement entries, and converts values with exact cents. `JdbcExternalResidualCostLedger.kt:17-30` requires the same-account instrument already be `REFUNDED`/`CHARGEBACK`, writes a negative `RESIDUAL_COST`, and is idempotent by provider reference. The mandatory reconciler dependency is wired in `OneOffPaymentsConfiguration.kt:35-38`. There is no recurrence/operations refund command or refund HTTP endpoint; refund data is observation-only. Mutation M5 was killed. |

## Executed tests

All final targeted commands used `--rerun-tasks`; no result below relies on Gradle's test cache.

| Suite | Tests | Failures / errors / skipped | Result | Log SHA-256 |
|---|---:|---:|---|---|
| Receivables unit: recurrence service, Asaas recurrence provider, operations, public controller | 21 | 0 / 0 / 0 | PASS | `7662ad1cc1dbc599d281e1de2a3f4bffe8913b76d93756e3989b57968456b916` (`final-target-unit-rerun.log`) |
| Receivables integration: operations, management wiring, schema | 14 | 0 / 0 / 0 | PASS | `81f3dcbac381490aa78730a2847dbb046197fc82aaede8b0ead28edacfa14160` (`final-target-integration.log`) |
| Bootstrap integration: recurrence wiring, operations endpoint, one-off regression | 46 | 0 / 0 / 0 | PASS | `62d445be7819f4484732617e58b5642efd12e7594da70439dbb54d288b07c314` (`final-target-bootstrap.log`) |
| Verifier-only card recovery probe | 8 in provider class | 0 / 0 / 0 | PASS | `727a20f6a8bc2d8f831e186534c2a92f3c44b3a652fe7f06b0b77907952bfdb` (`verifier-card-recovery.log`) |

The three required target suites comprise **81 tests, 0 failures, 0 errors, 0 skipped**. A broader command over `:features:receivables:test`, `:features:receivables:integrationTest`, and `:bootstrap:test` also completed successfully (`198e7825b2f32495feefd5bf72a91a9d418b1767a0007178143929afe447e8fa`). Temporary verifier tests and every mutation were removed from the scratch copy after execution.

## Schema inventory

`ReceivablesSchemaIntegrationTest.kt:17-29` ran Flyway against a fresh PostgreSQL instance and asserted:

- exactly **13** filesystem migrations executed;
- a second migration pass executed **0** migrations;
- exactly **32** `receivable_%` public tables exist.

The final integration suite passed those assertions. It also passed the cross-account bank-destination constraint fixture after that fixture was corrected to satisfy the newer non-null audit-column contract.

## Fault injection

Each mutation was applied only in the scratch copy, exercised by the narrowest relevant test, observed failing, and then restored.

| ID | Injected fault | Killing sensor | Result / log SHA-256 |
|---|---|---|---|
| M1 | Increase recurrence provider total by one cent | `RecurrencePaymentsTest` exact quote test | KILLED — `5429d641d4e91ebf4ee822b55ac17e561c16b72df26bb29540ac966897295a02` |
| M2 | Change card checkout charge type from `RECURRENT` | `HttpAsaasRecurrenceProviderTest` checkout contract | KILLED — `434b3b3349370e226e0ce1e6416fb85bf6301b2fe332336261eb391616b9257f` |
| M3 | Reverse cutoff comparison and target prior obligations | provider cutoff tests | KILLED (two tests) — `402b2939e752de950f2a4a3832f66b1b377c40aa60f5d9c4104dceb0446166aa` |
| M4 | Change Pix renewal from PUT to POST | renewal request contract test | KILLED — `19e1946b4eaa1c9e7e9cba5603802d6982b6aac1da5c97ea292cd8e404420477` |
| M5 | Remove statement `paymentId` correlation | residual-cost correlation test | KILLED — `400141284addd0cb0170aa986cdf027a1f802f7eeef3dc77959abb66c8aaa730` |
| M6 | Wire admin recovery to write-capable `reconcile` | final bootstrap wiring test | KILLED — `d80c2d75020e2d97aa09cfb5b0c2652bceea8b3f6749b0c0fdd5f822eea1f503` |

Final mutation score for the six contract faults is **6/6 killed (100%)**.

## Defects found and closed during verification

Two real issues were exposed in the first pass and corrected by the implementation owner before the final rerun:

1. The schema constraint fixture omitted a newly mandatory `updated_at`, causing SQLSTATE `23502` before the intended foreign-key assertion. Initial failing log SHA-256: `895652d030dfb88670e8c382800c879d8945760def55d79ad89f2c5031a3295a`.
2. The read-only admin probe applied observed payment facts but left the operation `UNKNOWN`, so positive provider proof could not report confirmation. Initial fault-revealing log SHA-256: `f99c888e4f24b8970b5dc5d8f6c7101f228682d0c9270161e739f2943b068bce`. The final implementation updates only expired/unknown operation bookkeeping from authenticated observations and the final wiring test proves it without a provider write.

No open correctness defect remains in the reviewed scope.

## Provider-document cross-check

The request shapes and lifecycle assumptions were checked against official Asaas documentation:

- [Create a checkout](https://docs.asaas.com/reference/create-new-checkout)
- [Recurring checkout](https://docs.asaas.com/docs/checkout-com-assinatura-recorrente)
- [List payments (`checkoutSession` filter)](https://docs.asaas.com/reference/list-payments)
- [Update an existing payment](https://docs.asaas.com/reference/update-existing-payment)
- [Update a subscription (`INACTIVE`)](https://docs.asaas.com/reference/update-existing-subscription)
- [Create a subscription](https://docs.asaas.com/reference/create-new-subscription)
- [Retrieve financial transactions](https://docs.asaas.com/reference/recuperar-extrato)
- [Checkout link and customer redirection](https://docs.asaas.com/docs/link-do-checkout-e-redirecionamento-do-cliente)

## Reproducibility and integrity

The final key-file hash manifest has SHA-256 `b60ca0197c8aaa14c97d1596e5ba81f5b324a26b8d8072953bfa105af09ddc3e`. Selected reviewed file hashes are:

- `RecurrencePayments.kt`: `513509b98c884094cfa7fc7018f4fa2c1ab95709a6f0bab955e2eeb8db3ba9dd`
- `HttpAsaasRecurrenceProvider.kt`: `a6d274f27685899d0561f73730e11dd57a3d81243afbef7d834e5af4d904d3ab`
- `HttpAsaasPayments.kt`: `305a05c374a0c1337f5ccb9fdb47bb1adde11e24bb1b7223501edc097b856f89`
- `JdbcPaymentExecution.kt`: `b9ec5a3724418f841b7ce2bca83ced2a775a16dbeb8516b471cb65624307cbd8`
- `JdbcExternalResidualCostLedger.kt`: `92dddba6c7ae7161bba5810b3c57fe961e9317c26a4da0ed212c27cb0bf6da46`
- `ReceivablesRecurrenceIntegrationTest.kt`: `2f6ac19c49c6f44aaeb4b6c76ba4457ae66600e6f46805dd20c5512d71552591`
- `ReceivablesSchemaIntegrationTest.kt`: `acc84ebacc45b0168b5312e56586383a71a0e51de7a8be0780aeeb2d9363aa5f`

Logs and manifests are retained under `/tmp/saqz-final-verifier-current.ac8PPA`; the initial failure logs are under `/tmp/saqz-final-verifier.S4iHfX`. These are machine-local scratch artifacts, not committed evidence. No real-provider test was performed, so provider sandbox/production connectivity, credentials, webhook delivery, and provider-side operational availability remain deployment checks rather than claims of this PASS.
