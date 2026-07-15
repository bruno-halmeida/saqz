#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
readme="$repository_root/README.md"
count=0

ok() {
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$1"
}

require() {
    pattern=$1
    label=$2
    grep -Eq "$pattern" "$readme" || {
        printf 'missing README contract: %s\n' "$label" >&2
        exit 1
    }
}

require 'python3 -m http\.server 8080 --directory landing-page' 'landing local server'
require '\.github/workflows/deploy-pages\.yml' 'Pages workflow'
require 'scripts/check-landing' 'landing gate'
ok 'retained landing instructions'

require 'JDK 21' 'JDK prerequisite'
require 'Node 26\.5\.0' 'Node prerequisite'
require 'npm 12\.0\.1' 'npm prerequisite'
require 'Android SDK' 'Android prerequisite'
require 'Xcode 26\.4' 'Xcode prerequisite'
require 'Firebase CLI 15\.23\.0' 'Firebase CLI prerequisite'
require 'Gitleaks 8\.30\.1' 'Gitleaks prerequisite'
ok 'prerequisites'

require 'scripts/check-all' 'aggregate gate'
require 'scripts/check-gradle' 'Gradle gate'
require 'scripts/check-ios' 'iOS gate'
require 'scripts/check-credentials' 'credential gate'
require 'scripts/check-scope' 'scope gate'
ok 'native gate commands'

require 'backend/gradlew -p backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :bootstrap:test :bootstrap:emulatorTest :architecture-tests:test --console=plain' 'backend Gradle command'
require 'mobile/gradlew -p mobile :compose-app:allTests :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain' 'mobile Gradle command'
ok 'Gradle and Android commands'

require 'npm --prefix frontend ci' 'Angular install'
require 'npm --prefix frontend run lint' 'Angular lint'
require 'npm --prefix frontend run test:ci' 'Angular test'
require 'npm --prefix frontend run build' 'Angular build'
ok 'Angular commands'

require 'CODE_SIGNING_ALLOWED=NO test' 'credential-free xcodebuild'
require 'macOS for the complete local gate' 'local platform limitation'
require 'iOS on macOS' 'CI iOS runner'
ok 'iOS and CI platform commands'

require 'bootstrap -> features:<feature> -> shared-kernel' 'dependency direction'
require 'must not import Spring, Firebase, HTTP' 'backend boundary'
require 'must not use composite builds' 'workspace boundary'
ok 'dependency rules'

require 'Create `backend/features/<feature>/`' 'backend feature module'
require 'Wire the feature from `backend/bootstrap/`' 'backend wiring'
require 'Add architecture rules' 'backend architecture tests'
ok 'backend feature procedure'

require 'Create `mobile/features/<feature>/`' 'KMP feature module'
require 'Depend on the feature from `mobile/compose-app`' 'KMP aggregation'
require 'umbrella framework' 'iOS umbrella'
ok 'KMP umbrella procedure'

require 'Project ID: `saqz-local`' 'fake project'
require 'fake-saqz-local-api-key' 'fake API key'
require '127\.0\.0\.1:9099' 'web/iOS emulator'
require '10\.0\.2\.2:9099' 'Android emulator'
ok 'local fake Firebase values'

require 'google-services\.json' 'Android credential exclusion'
require 'GoogleService-Info\.plist' 'Apple credential exclusion'
require 'non-example `\.env`' 'env exclusion'
require '\.specs/` is ignored' 'spec ignored'
ok 'production config exclusions'

require 'scripts/test-scripts' 'script test gate'
require 'initialization-gate' 'CI aggregate'
ok 'script and CI commands'

[ "$count" -eq 12 ]
