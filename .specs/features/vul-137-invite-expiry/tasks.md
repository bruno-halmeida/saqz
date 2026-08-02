# VUL-137 Execution Plan

## Gate commands

- Quick: `export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home && ./gradlew :features:groups:test --tests '<target>'`
- Full: `export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home && ./gradlew :features:groups:test :features:groups:integrationTest :bootstrap:test --tests '<target>'`
- Build: `export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home && ./gradlew :features:groups:test :features:groups:integrationTest :bootstrap:test detekt`

## Tasks

### T1 — Persist invite expiration and metadata port

- **Files**: migration V24, invite management/redeem models and repository adapters, repository integration tests.
- **Done when**: the schema is backfilled and non-null; rotate persists/renews `expires_at`; repository reads digest, expiration, creation time and creator display name; idempotent membership behavior remains covered.
- **Gate**: full groups unit and integration tests.
- **Status**: completed

### T2 — Rotate expiration

- **Files**: `RotateInvite`, rotate models, bootstrap wiring, unit and endpoint test fixtures.
- **Done when**: owner/admin rotate succeeds with `inviteUrl` and `expiresAt` equal to injected now plus seven days; authorization is unchanged.
- **Gate**: groups unit tests and targeted bootstrap tests.
- **Status**: completed

### T3 — Redeem expiration and idempotency

- **Files**: `RedeemInvite`, redeem models/adapter, unit and endpoint integration tests.
- **Done when**: expired invites return `InvalidOrExpired` and increment the invalid window; valid invites proceed; existing ADMIN and ATHLETE members receive success with their current role.
- **Gate**: groups unit/integration tests and targeted bootstrap tests.
- **Status**: completed

### T4 — GET invite metadata

- **Files**: metadata use case, controller, repository/configuration, endpoint integration tests.
- **Done when**: active, expired, absent, 404 and 403 responses match the contract; no response contains the invite URL or digest.
- **Gate**: groups unit/integration tests and bootstrap tests.
- **Status**: pending

### T5 — Final verification and publication

- **Files**: validation report only if needed by the verification workflow.
- **Done when**: the migration number is reconfirmed against `origin/main`; JDK 21 unit/integration/detekt gates pass; commit, ready PR and Linear review update exist.
- **Gate**: build gate plus required final checks.
- **Status**: pending
