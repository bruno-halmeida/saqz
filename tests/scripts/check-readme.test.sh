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
require 'scripts/check-bruno' 'Bruno contract gate'
require 'scripts/check-mobile-boundaries' 'mobile boundary gate'
ok 'native gate commands'

require 'backend/gradlew -p backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :features:access:test :features:access:integrationTest :features:groups:test :features:groups:integrationTest :bootstrap:test :bootstrap:emulatorTest :architecture-tests:test --console=plain' 'backend Gradle command'
ok 'backend Gradle command'

require 'mobile/gradlew -p mobile :core:common:allTests :core:design-system:allTests :core:design-system:bundleAndroidMainAar :core:domain:allTests :core:network:allTests :features:access:domain:allTests :features:access:data:allTests :features:access:compileAndroidMain :features:access:allTests :features:groups:domain:allTests :features:groups:data:allTests :features:groups:compileAndroidMain :features:groups:allTests :compose-app:allTests :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain' 'mobile Gradle command'

require ':core:common:allTests' 'core common suite'
ok 'core common suite documented'

require ':core:design-system:allTests' 'core design system suite'
ok 'core design system suite documented'

require ':compose-app:allTests' 'compose app suite'
ok 'compose app suite documented'

require ':android-app:testDevDebugUnitTest' 'Android unit suite'
ok 'Android unit suite documented'

require ':android-app:connectedDevDebugAndroidTest' 'Android connected suite'
ok 'Android connected suite documented'

if grep -Eqi 'Angular|frontend/|npm --prefix frontend|Web app ID|scripts/check-web|web-gate' "$readme"; then
    printf 'README still documents the retired Angular workspace\n' >&2
    exit 1
fi
ok 'retired Angular documentation absent'

require 'xcodebuild .*SaqzDev.*CODE_SIGNING_ALLOWED=NO test' 'SaqzDev full suite'
ok 'SaqzDev suite documented'

require 'xcodebuild .*SaqzProd.*-configuration Release.*CODE_SIGNING_ALLOWED=NO build' 'SaqzProd Release build'
require 'xcodebuild .*SaqzProd.*-configuration Release.*-only-testing:SaqzIOSTests.*ENABLE_TESTABILITY=YES.*test' 'SaqzProd unit suite'
ok 'SaqzProd suites documented'

require 'CODE_SIGNING_ALLOWED=NO test' 'credential-free xcodebuild'
require 'macOS for the complete local gate' 'local platform limitation'
require 'iOS on macOS' 'CI iOS runner'
ok 'iOS and CI platform commands'

require 'scripts/check-ios --dev-only' 'iOS CI Dev-only command'
require 'SaqzProd remains in the complete local gate' 'deferred production CI policy'
ok 'iOS CI Dev-only policy'

require 'bootstrap -> features:<feature> -> shared-kernel' 'dependency direction'
require 'must not import Spring, Firebase, HTTP' 'backend boundary'
require 'must not use composite builds' 'workspace boundary'
ok 'dependency rules'

require 'Create `backend/features/<feature>/`' 'backend feature module'
require 'Wire the feature from `backend/bootstrap/`' 'backend wiring'
require 'Add architecture rules' 'backend architecture tests'
ok 'backend feature procedure'

require 'Create `mobile/features/<feature>/`' 'KMP feature module'
require "Depend on the feature's presentation and data modules from" 'KMP aggregation'
require 'umbrella framework' 'iOS umbrella'
ok 'KMP umbrella procedure'

require 'Project ID: `saqz-local`' 'fake project'
require 'fake-saqz-local-api-key' 'fake API key'
require '127\.0\.0\.1:9099' 'backend/iOS emulator'
require '10\.0\.2\.2:9099' 'Android emulator'
ok 'local fake Firebase values'

require 'google-services\.json' 'Android credential exclusion'
require 'GoogleService-Info\.plist' 'Apple credential exclusion'
require 'non-example `\.env`' 'env exclusion'
require '`AGENTS\.md`' 'agent instructions'
require '`\.specs/` is versioned' 'versioned project memory'
ok 'production config exclusions'

require 'scripts/test-scripts' 'script test gate'
require 'initialization-gate' 'CI aggregate'
ok 'script and CI commands'

test_scripts="$repository_root/scripts/test-scripts"
grep -Fq 'tests/scripts/check-ci.test.sh' "$test_scripts"
grep -Fq 'tests/scripts/check-bruno.test.sh' "$test_scripts"
grep -Fq 'tests/scripts/check-readme.test.sh' "$test_scripts"
ok 'script aggregate includes CI and README contracts'

require '`backend/features/access`' 'backend access module'
require 'bootstrap -> features:access -> shared-kernel' 'backend access dependency direction'
ok 'backend access architecture inventory'

require '`mobile/core/network`' 'mobile network module'
require '`mobile/features/access`' 'mobile access module'
require 'compose-app -> features:access -> features:access:domain -> core:domain' 'mobile access presentation dependency direction'
require 'compose-app -> features:access:data -> features:access:domain -> core:network' 'mobile access data dependency direction'
ok 'mobile access architecture inventory'

require '`backend/features/groups`' 'backend Groups module'
require 'bootstrap -> features:groups -> shared-kernel' 'backend Groups dependency direction'
ok 'backend Groups architecture inventory'

require '`mobile/features/groups`' 'mobile Groups module'
require 'compose-app -> features:groups -> features:groups:domain -> core:domain' 'mobile Groups presentation dependency direction'
require 'compose-app -> features:groups:data -> features:groups:domain -> core:network' 'mobile Groups data dependency direction'
ok 'mobile Groups architecture inventory'

require 'Docker.*PostgreSQL 16.*Testcontainers' 'disposable PostgreSQL prerequisite'
require 'No local database URL or database credential is required' 'credential-free PostgreSQL tests'
ok 'PostgreSQL Testcontainers configuration'

require '`firebase/session-fixture`' 'Firebase session fixture'
require 'npx --yes firebase-tools@15\.23\.0 emulators:start --only auth --project saqz-local' 'Firebase emulator command'
ok 'Firebase emulator fixture command'

require '`firebase\.json`.*`127\.0\.0\.1:9099`' 'Firebase emulator endpoint'
require 'temporary verified account' 'disposable Firebase account'
ok 'Firebase emulator disposable account'

require 'Branch test mode.*`key_test_saqz_local_fixture`.*`saqz\.test-app\.link`' 'fake Branch test configuration'
require 'does not require a live Branch key' 'credential-free Branch tests'
ok 'Branch test configuration'

require 'backend/gradlew -p backend :features:access:test :features:access:integrationTest --console=plain' 'focused backend access suites'
require 'backend/gradlew -p backend :features:groups:test :features:groups:integrationTest --console=plain' 'focused backend Groups suites'
require 'backend/gradlew -p backend :bootstrap:test :bootstrap:emulatorTest --console=plain' 'focused backend HTTP and emulator suites'
ok 'focused backend access commands'

require 'mobile/gradlew -p mobile :core:domain:allTests :core:network:allTests --console=plain' 'focused mobile network suite'
require 'mobile/gradlew -p mobile :features:access:domain:allTests :features:access:data:allTests :features:access:compileAndroidMain :features:access:allTests --console=plain' 'focused mobile access suites'
ok 'focused mobile access commands'

require 'mobile/gradlew -p mobile :features:groups:domain:allTests :features:groups:data:allTests :features:groups:compileAndroidMain :features:groups:allTests --console=plain' 'focused mobile Groups suites'
ok 'focused Groups commands'

require 'Group photos are private authenticated resources' 'private group photos'
require 'manual tracking only' 'manual finance tracking'
require 'does not process payments' 'no payment processing'
require 'athletes' 'athlete finance audience'
require 'only their own charges' 'athlete finance privacy'
ok 'Groups privacy and finance boundaries'

[ "$count" -eq 33 ]
