# Saqz

Saqz is a mobile-first monorepo with independent product workspaces for the
Kotlin backend and Compose Multiplatform app, plus the preserved static
pre-launch landing page.

## Prerequisites

- macOS for the complete local gate, because `scripts/check-ios` requires Xcode
  and an available iOS Simulator.
- JDK 21. `scripts/check-ios` defaults to
  `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`; override
  with `SAQZ_JAVA_HOME` when needed.
- Node 26.5.0 and npm 12.0.1 for the pinned Firebase CLI and session fixture.
- Android SDK, `adb`, and a running Android emulator/device for
  `:android-app:connectedDevDebugAndroidTest`.
- Xcode 26.4 with an installed iOS Simulator runtime.
- Firebase CLI 15.23.0 through `npx --yes firebase-tools@15.23.0`.
- Docker with a running daemon. Backend access integration tests start
  PostgreSQL 16 containers through Testcontainers.
- Gitleaks 8.30.1 is optional locally; `scripts/check-credentials` runs it
  when installed and always runs the built-in credential scanner.

## Local Backend With Firebase Dev

Place the `saqz-dev` Firebase Admin service-account key at
`.secrets/firebase-dev-service-account.json`, then run:

```bash
docker compose up --build -d
docker compose ps
curl http://localhost:8080/actuator/health
```

The stack uses the real `saqz-dev` Firebase project and a local PostgreSQL 16
database; it does not start the Auth Emulator. Database data persists in the
`saqz-postgres-data` Docker volume. Follow logs or stop the stack with:

```bash
docker compose logs -f backend
docker compose down
```

Use `docker compose down --volumes` only when you intentionally want to reset
all local backend data.

### Bruno API Collection

Open `bruno/` in Bruno and select the `Dev` environment. Copy the environment
template and fill it with a Firebase Dev test account:

```bash
cp bruno/.env.example bruno/.env
```

Requests are grouped by context. Start with `System/Health`, then run
`Authentication/Firebase Dev Login` and `Authentication/Session`; continue with
the `Groups`, `Memberships`, and `Invitations` contexts as needed. The login
stores the Firebase ID token only in Bruno runtime memory. Every observable
backend HTTP contract change must update its request and assertions in `bruno/`;
`scripts/check-bruno` enforces recursive route coverage.

## Landing Page

The pre-launch landing page lives in `landing-page/` and is intentionally
independent from the product workspaces.

Serve it locally with any static HTTP server:

```bash
python3 -m http.server 8080 --directory landing-page
```

Open `http://localhost:8080`.

The workflow `.github/workflows/deploy-pages.yml` publishes `landing-page/`
after changes to that folder are pushed to `main`. Initialization work must not
change the landing content; `scripts/check-landing` compares it with baseline
`c03a8ccbc800b70a982b1f9bb93a4b0af3d87c44`, verifies the Pages workflow
contract, and checks `index.html` plus local assets over HTTP.

## Remotion Media

Code-driven Saqz videos live in the independent `remotion/` workspace. It is
media-production tooling, not a browser product or a dependency of backend,
mobile, or the landing page.

```bash
cd remotion
npm install
npm run check
npm run render:stills
npm run render:all
```

Remotion Studio is available through `npm run dev`. Generated PNG and MP4 files
are written to the ignored `remotion/out/` directory.

## Local Gates

### Authentication and access test environment

The authenticated backend matrix is disposable and credential-free. Docker must be available for PostgreSQL 16 through Testcontainers;
each database container is removed after the suite. No local database URL or database credential is required.

Firebase authentication tests use `firebase/session-fixture`. The fixture runs
`npx --yes firebase-tools@15.23.0 emulators:start --only auth --project saqz-local --config firebase.json`,
creates a temporary verified account, exposes its test token only through a
private temporary directory, and removes the account and emulator process on
exit. `firebase.json` binds the Auth Emulator to `127.0.0.1:9099`; these tests
never read a Firebase Dev or production credential.

Branch test mode uses `key_test_saqz_local_fixture` and `saqz.test-app.link` as committed fake values on Android and iOS; it does not require a live Branch key.
It does not call the short-link API, and test links contain only opaque invite
codes. Production Branch values remain external configuration and must not be
committed.

Run the focused authenticated suites with the same tasks included in the
aggregate Gradle gate:

```bash
backend/gradlew -p backend :features:access:test :features:access:integrationTest --console=plain
backend/gradlew -p backend :features:groups:test :features:groups:integrationTest --console=plain
backend/gradlew -p backend :bootstrap:test :bootstrap:emulatorTest --console=plain
mobile/gradlew -p mobile :core:domain:allTests :core:network:allTests --console=plain
mobile/gradlew -p mobile :features:access:domain:allTests :features:access:data:allTests :features:access:compileAndroidMain :features:access:allTests --console=plain
mobile/gradlew -p mobile :features:groups:domain:allTests :features:groups:data:allTests :features:groups:compileAndroidMain :features:groups:allTests --console=plain
```

Run the complete local gate on macOS:

```bash
scripts/check-all
```

Native gates are also directly runnable:

```bash
scripts/check-gradle
scripts/check-ios
scripts/check-landing
scripts/check-credentials
scripts/check-scope
scripts/check-mobile-boundaries
scripts/test-scripts
```

`scripts/check-gradle` runs credential and scope checks first, then the mobile
domain/data boundary gate, then:

```bash
backend/gradlew -p backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :features:access:test :features:access:integrationTest :features:groups:test :features:groups:integrationTest :bootstrap:test :bootstrap:emulatorTest :architecture-tests:test --console=plain
mobile/gradlew -p mobile :core:common:allTests :core:design-system:allTests :core:design-system:bundleAndroidMainAar :core:domain:allTests :core:network:allTests :features:access:domain:allTests :features:access:data:allTests :features:access:compileAndroidMain :features:access:allTests :features:groups:domain:allTests :features:groups:data:allTests :features:groups:compileAndroidMain :features:groups:allTests :navigation:allTests :compose-app:allTests :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain
```

`scripts/check-ios` runs credential-free simulator tests:

```bash
xcodebuild -project mobile/ios-app/SaqzIOS.xcodeproj -scheme SaqzDev -destination "id=<available-simulator-udid>" CODE_SIGNING_ALLOWED=NO test
xcodebuild -project mobile/ios-app/SaqzIOS.xcodeproj -scheme SaqzProd -configuration Release -destination "id=<available-simulator-udid>" CODE_SIGNING_ALLOWED=NO build
xcodebuild -project mobile/ios-app/SaqzIOS.xcodeproj -scheme SaqzProd -configuration Release -destination "id=<available-simulator-udid>" -only-testing:SaqzIOSTests CODE_SIGNING_ALLOWED=NO ENABLE_TESTABILITY=YES test
```

GitHub Actions runs platform gates separately in
`.github/workflows/initialization-gate.yml`: the complete Gradle/API 30 gate,
the focused API 35 gate and landing on Linux, plus iOS on macOS through
`scripts/check-ios --dev-only`. API 30 retains the `google_atd` x86_64 complete
gate. API 35 uses the pinned `google_apis` x86_64 image, `pixel_7`, 4096 MB,
AVD `saqz-api35-probe`, emulator build 13823996, and a 300-second boot limit.
The remote iOS gate runs the complete SaqzDev unit + UI suite.
SaqzProd remains in the complete local gate; its dedicated production CI is
deferred until that pipeline is designed. The aggregate `initialization-gate`
passes only when all four jobs pass.

## Agent Workflow

`AGENTS.md` is the versioned operating contract for coding agents. `.specs/` is versioned
project memory: `STATE.md` records active architectural decisions and handoff state, while
`features/<feature>/` holds requirements, design, tasks, and validation evidence at the depth
required by each feature. Agents load only the active feature documents and must keep specs
free of credentials and other secrets.

## Workspace Boundaries

- `backend/` owns its Gradle wrapper, settings, version catalog, and build
  logic. It must build without `mobile/`.
- `mobile/` owns its Gradle wrapper, settings, version catalog, and build
  logic. It must build without `backend/`.
- `landing-page/` is static public content, not a product application
  workspace and not a dependency of backend or mobile builds.
- `remotion/` owns its npm dependencies and media compositions. It may reuse
  versioned Saqz source assets but is not a runtime dependency of any product.
- The repository root has no Gradle wrapper, settings, catalog, or build logic.
- Backend and mobile must not use composite builds, shared build logic, project
  dependencies, or compiled artifacts from sibling workspaces.
- There is no browser product workspace. A future web app requires a new spec
  and architecture decision; the landing remains HTML/CSS.

## Backend Architecture

Backend features use feature-oriented hexagonal modules. Allowed dependency
direction is:

```text
bootstrap -> features:<feature> -> shared-kernel
```

The implemented feature modules are `backend/features/identity`,
`backend/features/access`, and `backend/features/groups`. Access owns verified
identity, account/session bootstrap, and selected-group reconciliation. Groups
owns group profiles, memberships, invitations, venues, games, attendance,
manual charges, and expenses. Both feature directions are independent:
`bootstrap -> features:access -> shared-kernel` and
`bootstrap -> features:groups -> shared-kernel`. Domain, application, and API
contracts must not import Spring, Firebase, HTTP adapters, persistence, another
feature's internals, or client code. Adapters are owned by their feature and are
wired only by the Spring Boot composition root.

To add a backend feature:

1. Create `backend/features/<feature>/` only when it owns implemented behavior.
2. Add `api`, `application`, and domain code inside that module as needed.
3. Add input/output adapters under the feature's adapter packages.
4. Wire the feature from `backend/bootstrap/`; do not wire from another feature.
5. Add architecture rules before exposing cross-module dependencies.

## Mobile Architecture

`mobile/compose-app` is the single shared Compose umbrella consumed by Android
and iOS. `mobile/android-app` is the Android launcher. `mobile/ios-app` is the
Xcode launcher and embeds the one `SaqzMobile` framework.

Each product feature is split into stable Gradle coordinates with a real
compile boundary between them:

- `mobile/core/domain` owns the only cross-feature success/failure vocabulary
  (`SaqzResult`, `SaqzError`, `EmptyResult`, `DataError`, `ValidationDetails`)
  and genuinely shared identifiers (for example `GroupId`). It depends on the
  Kotlin standard library only.
- `mobile/features/access/domain` and `mobile/features/groups/domain` own
  business-facing models, capability contracts, and feature-specific errors.
  Each depends only on `:core:domain`.
- `mobile/features/access/data` and `mobile/features/groups/data` own DTOs,
  Ktor API implementations, and transport-to-domain mapping for their
  feature. Each depends on its own feature's domain module, `:core:domain`,
  `:core:network`, Ktor, and serialization.
- `mobile/features/access` and `mobile/features/groups` remain the stable
  presentation coordinates: ViewModels, state machines, Compose UI, and
  resources. Each depends on its own feature's domain module plus
  `:core:common`/`:core:design-system`/Compose; neither depends on its data
  module, `:core:network`, Ktor, or serialization.
- `mobile/compose-app` is the only module allowed to depend on both a
  feature's presentation and data modules, so it can bind each domain
  contract to its Ktor implementation in Koin and translate Access-owned
  session/membership context into Groups-owned inputs. Feature DTOs and data
  implementations never enter app state or UI contracts.

Access and Groups never depend on each other at any layer; `compose-app` is
the only cross-feature coordination point. The concrete aggregation
directions are:

```text
compose-app -> features:access -> features:access:domain -> core:domain
compose-app -> features:access:data -> features:access:domain -> core:network
compose-app -> features:groups -> features:groups:domain -> core:domain
compose-app -> features:groups:data -> features:groups:domain -> core:network
```

Authenticated transport lives in `mobile/core/network` and is consumed only
by data modules; no domain or presentation module imports it, and no mobile
module imports backend implementation types.
`scripts/check-mobile-boundaries` enforces this graph and the equivalent
Kotlin import boundaries deterministically; run it directly or through
`scripts/check-gradle`, which invokes it before the mobile Gradle aggregate.

Presentation follows one MVI contract (AD-025). Every ViewModel extends
`MviViewModel<State, Intent, Effect>` (`core/common`): immutable `StateFlow`
state updated atomically, a single `onIntent` entry, and buffered one-shot
`Effect`s. ViewModels take only constructor-provided business dependencies —
no test-scope or dispatcher-replacement hooks (tests drive time via
`Dispatchers.setMain` + `runTest`). Routes split into a Root composable (DI
via `koinViewModel`, `collectAsStateWithLifecycle`, effect observation via the
shared `ObserveAsEvents`, cross-feature callbacks) and a stateless
`(state, onIntent)` screen. Result text reaches the UI only through `UiText`
(`core/design-system`), never raw transport errors. Essential in-progress
input survives process death: forms with a durable `DraftsModule` draft
reconcile with it (draft wins), and the one form without a draft (GameDetail)
uses a `SavedStateHandle.saved()` snapshot; restored-but-invalid input shows
the normal corrective state and never auto-submits. Authenticated-context
panels (group settings, memberships, invite, and the groups sub-destinations)
still share their route ViewModel per AD-025 until navigation (AD-029)
promotes them to real entries.

Group photos are private authenticated resources and must never be documented
or exposed as public URLs. Charges and expenses are manual tracking only: Saqz
does not process payments, move money, store card data, or claim settlement.
Organizer totals and expense details remain owner/admin-only, while athletes
may see only their own charges.

To add a KMP feature:

1. Create `mobile/features/<feature>/` only when there is real mobile behavior.
2. Split it into `<feature>/domain` (contracts/models), `<feature>/data`
   (DTOs/Ktor), and the existing `<feature>` presentation module, following
   the dependency direction above.
3. Depend on the feature's presentation and data modules from
   `mobile/compose-app` only; bind domain contracts to data implementations
   in the Koin composition root.
4. Keep iOS consuming only the umbrella framework generated by `compose-app`.

Client `domain` packages under `mobile/core` and `mobile/features/*/domain`
are the approved mobile domain layer described above. No client source may
otherwise contain backend business `domain`, `usecase`, or `application`
packages, and no mobile module may re-export or depend on backend code.

## Firebase And Credentials

Local Firebase configuration is fake and committed as code/configuration:

- Project ID: `saqz-local`
- API key: `fake-saqz-local-api-key`
- Android app ID: `1:123456789000:android:saqzlocal`
- Apple app ID: `1:123456789000:ios:5a61717a6c6f6361`
- Auth Emulator endpoints: `127.0.0.1:9099` for backend tooling and iOS
  Simulator, `10.0.2.2:9099` for Android Emulator.
- iOS has shared schemes `SaqzDev` and `SaqzProd`. `SaqzDev` uses the `Debug`
  configuration and reads development Firebase config from ignored local file
  `mobile/ios-app/SaqzIOS/Config/Dev/GoogleService-Info.plist` when present.
  `SaqzProd` uses the `Release` configuration and reads production Firebase
  config from ignored local file
  `mobile/ios-app/SaqzIOS/Config/Prod/GoogleService-Info.plist`. Without a
  matching file, the iOS app falls back to the local Auth Emulator config.
- Android uses `dev` and `prod` product flavors. The `dev` flavor reads
  development Firebase config from ignored local file
  `mobile/android-app/src/dev/google-services.json` when present. Without that
  file, `dev` falls back to the local Auth Emulator config. The `prod` flavor
  reads production Firebase config from ignored local file
  `mobile/android-app/src/prod/google-services.json`.

Do not commit production Firebase credentials, service accounts, signing
identities, production database credentials, `google-services.json`,
`GoogleService-Info.plist`, or non-example `.env` files.
