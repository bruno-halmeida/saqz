# VUL-140 validation

Date: 2026-08-02
Diff under validation: `origin/main..HEAD`

## Requirement traceability

| Acceptance criterion | Evidence | Result |
| --- | --- | --- |
| AC1 — public POST with optional bearer | `IdentitySecurityConfiguration.kt:32-40,53-58`, `AccessInvitePreviewController.kt:75-96`; anonymous endpoint test at `InvitePreviewEndpointIntegrationTest.kt:76-109` | Pass |
| AC2 — contract card without `groupId` | Response mapping at `AccessInvitePreviewController.kt:24-55`; JDBC assembly at `JdbcInvitePreviewRepository.kt:69-128`; exact property-set and field assertions at `InvitePreviewEndpointIntegrationTest.kt:80-108` | Pass |
| AC3 — malformed/unknown is 404 `INVITE_INVALID` | Use-case lookup and result at `PreviewInvite.kt:27-33`; unit coverage at `PreviewInviteTest.kt:73-89`; HTTP problem assertion at `InvitePreviewEndpointIntegrationTest.kt:111-118` | Pass |
| AC4 — soft-deleted group is indistinguishable | Port exposes only an internal deletion flag at `JdbcInvitePreviewRepository.kt:71-74,109-114`; use case maps it to `Invalid` at `PreviewInvite.kt:34-37`; unit coverage at `PreviewInviteTest.kt:91-97` | Pass |
| AC5 — expiry contract with base-branch fallback | Expiry result handling at `PreviewInvite.kt:34-37`; expired unit coverage at `PreviewInviteTest.kt:99-106`; adapter explicitly documents absent VUL-137 column and returns null at `JdbcInvitePreviewRepository.kt:111-124`; HTTP null field is asserted at `InvitePreviewEndpointIntegrationTest.kt:104-105` | Pass with documented schema fallback |
| AC6 — authenticated redeem-compatible invalid window | Lock/update port and SQL at `JdbcInvitePreviewRepository.kt:24-67`; authenticated path at `PreviewInvite.kt:20-25,41-55`; integration coverage at `JdbcInvitePreviewRepositoryIntegrationTest.kt:125-137`; endpoint coverage at `InvitePreviewEndpointIntegrationTest.kt:142-152` | Pass |
| AC7 — anonymous IP window, 30/10 min, 429 retry | Limiter and `ponytail:` scale-up note at `PreviewInvite.kt:72-118`; unit coverage at `PreviewInviteTest.kt:108-126`; endpoint coverage at `InvitePreviewEndpointIntegrationTest.kt:120-130` | Pass |
| AC8 — no broad anonymous access | Only `/api/invites/preview` is optional/permitted at `IdentitySecurityConfiguration.kt:53-58,70-74`; negative `/api/session` test at `InvitePreviewEndpointIntegrationTest.kt:132-140` | Pass |

## Verification evidence

- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home ./gradlew :features:groups:test` — pass; `PreviewInviteTest`: 9/9.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home DOCKER_HOST=unix:///Users/bruno_almeida/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew :features:groups:integrationTest --tests br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInvitePreviewRepositoryIntegrationTest --max-workers=1` — pass; 5/5.
- `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home DOCKER_HOST=unix:///Users/bruno_almeida/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew :bootstrap:test --tests br.com.saqz.bootstrap.InvitePreviewEndpointIntegrationTest --max-workers=1` — pass; 5/5.
- Full `:bootstrap:test` was also run with JDK 21 and Colima and passed (246 tests).
- `git diff --check` — pass.

The full backend `check` was attempted with JDK 21, Colima, Ryuk disabled, and `--max-workers=1`. It was stopped after the groups integration suite blocked on Flyway's PostgreSQL advisory lock in the existing `JdbcChargeTransactionRepositoryIntegrationTest.reset` while VUL-137/VUL-138 workers in other worktrees were using the same Colima PostgreSQL resources. The directly affected integration gates above passed serially.

`./gradlew detekt` is not configured in the backend build (`Task 'detekt' not found`); Detekt is present in mobile build logic only. No unrelated build plugin was added for this ticket.

## Independent verifier sensors

- Mutated the soft-delete branch in a temporary worktree to return success. `PreviewInviteTest` failed in `deleted group is indistinguishable from invalid and does not count` (1 failure), so AC4 is discriminating.
- Mutated the preview security matcher in a temporary worktree from `permitAll()` to `authenticated()`. `InvitePreviewEndpointIntegrationTest` failed its public success and anonymous invalid-window scenarios (3 failures), so AC1/AC7 are discriminating.
- Temporary worktree was removed; the implementation worktree remained clean after verification.
