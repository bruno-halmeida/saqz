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
- Gitleaks 8.30.1 is optional locally; `scripts/check-credentials` runs it
  when installed and always runs the built-in credential scanner.

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

## Local Gates

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
scripts/test-scripts
```

`scripts/check-gradle` runs credential and scope checks first, then:

```bash
backend/gradlew -p backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :bootstrap:test :bootstrap:emulatorTest :architecture-tests:test --console=plain
mobile/gradlew -p mobile :core:common:allTests :core:design-system:allTests :compose-app:allTests :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain
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
`scripts/check-ios --dev-only`. The remote iOS gate runs the complete
SaqzDev unit + UI suite. SaqzProd remains in the complete local gate; its
dedicated production CI is deferred until that pipeline is designed. The
aggregate `initialization-gate` passes only when all four jobs pass.

## Workspace Boundaries

- `backend/` owns its Gradle wrapper, settings, version catalog, and build
  logic. It must build without `mobile/`.
- `mobile/` owns its Gradle wrapper, settings, version catalog, and build
  logic. It must build without `backend/`.
- `landing-page/` is static public content, not a product application
  workspace and not a dependency of backend or mobile builds.
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

Identity is currently the only backend feature module under `backend/features/`.
Domain, application, and API contracts must not import Spring, Firebase, HTTP
adapters, persistence, another feature's internals, or client code. Adapters are
owned by their feature and are wired only by the Spring Boot composition root.

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

To add a KMP feature:

1. Create `mobile/features/<feature>/` only when there is real mobile behavior.
2. Keep feature internals inside that module.
3. Depend on the feature from `mobile/compose-app`.
4. Keep iOS consuming only the umbrella framework generated by `compose-app`.

No client source may contain backend business `domain`, `usecase`, or
`application` packages.

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
`GoogleService-Info.plist`, or non-example `.env` files. `.specs/` is ignored
and must remain untracked.
