#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-gradle-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM
count=0

make_repo() {
    target=$1
    mkdir -p "$target/repo/scripts" "$target/repo/backend" "$target/repo/mobile" "$target/repo/bin"
    cp "$repository_root/scripts/check-gradle" "$target/repo/scripts/check-gradle"
    chmod +x "$target/repo/scripts/check-gradle"
    log="$target/repo/invocations.log"

    cat >"$target/repo/scripts/check-credentials" <<'SH'
#!/bin/sh
set -eu
printf 'credentials\n' >>"$LOG_FILE"
[ "${FAIL_CREDENTIALS:-0}" = 0 ] || exit 41
SH
    cat >"$target/repo/scripts/check-scope" <<'SH'
#!/bin/sh
set -eu
printf 'scope\n' >>"$LOG_FILE"
[ "${FAIL_SCOPE:-0}" = 0 ] || exit 42
SH
    cat >"$target/repo/scripts/check-bruno" <<'SH'
#!/bin/sh
set -eu
printf 'bruno\n' >>"$LOG_FILE"
[ "${FAIL_BRUNO:-0}" = 0 ] || exit 46
SH
    cat >"$target/repo/scripts/check-mobile-boundaries" <<'SH'
#!/bin/sh
set -eu
printf 'mobile-boundaries\n' >>"$LOG_FILE"
[ "${FAIL_MOBILE_BOUNDARIES:-0}" = 0 ] || exit 47
SH
cat >"$target/repo/backend/gradlew" <<'SH'
#!/bin/sh
set -eu
printf 'backend %s\n' "$*" >>"$LOG_FILE"
backend_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
results_dir="$backend_dir/build/test-results/stub"
mkdir -p "$results_dir"
tests_attr=1
[ "${ZERO_BACKEND_TESTS:-0}" = 0 ] || tests_attr=0
printf '<testsuite tests="%s"></testsuite>\n' "$tests_attr" >"$results_dir/TEST-stub.xml"
if [ -n "${FAIL_TASK:-}" ]; then
    case " $* " in *" $FAIL_TASK "*) exit 45 ;; esac
fi
case " $* " in
    *" :features:identity:emulatorTest "*|*" :bootstrap:emulatorTest "*)
        [ "${FAIL_FIREBASE_EMULATOR:-0}" = 0 ] || exit 43
        ;;
esac
SH
    cat >"$target/repo/mobile/gradlew" <<'SH'
#!/bin/sh
set -eu
printf 'mobile %s\n' "$*" >>"$LOG_FILE"
mobile_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
aar_dir="$mobile_dir/core/design-system/build/outputs/aar"
results_dir="$mobile_dir/build/test-results/stub"
mkdir -p "$aar_dir" "$results_dir"
: >"$aar_dir/design-system.aar"
tests_attr=1
[ "${ZERO_TESTS:-0}" = 0 ] || tests_attr=0
printf '<testsuite tests="%s"></testsuite>\n' "$tests_attr" >"$results_dir/TEST-stub.xml"
if [ -n "${FAIL_TASK:-}" ]; then
    case " $* " in
        *" $FAIL_TASK "*) exit 45 ;;
    esac
fi
case " $* " in
    *" :android-app:connectedDevDebugAndroidTest "*)
        [ "${FAIL_ANDROID_INSTRUMENTED:-0}" = 0 ] || exit 44
        ;;
esac
SH
    cat >"$target/repo/bin/unzip" <<'SH'
#!/bin/sh
set -eu
[ "${MISSING_PREVIEW_RESOURCE:-0}" = 0 ] || exit 0
printf '%s\n' 'assets/composeResources/br.com.saqz.designsystem.resources/drawable/preflight_sentinel.xml'
SH
    cat >"$target/repo/bin/adb" <<'SH'
#!/bin/sh
set -eu
printf 'adb %s\n' "$*" >>"$LOG_FILE"
if [ "${NO_ANDROID_DEVICE:-0}" = 1 ]; then
    printf 'List of devices attached\n\n'
else
    printf 'List of devices attached\nemulator-5554\tdevice\n'
fi
SH
    chmod +x "$target/repo/scripts/check-credentials" "$target/repo/scripts/check-scope" \
        "$target/repo/scripts/check-bruno" "$target/repo/scripts/check-mobile-boundaries" \
        "$target/repo/backend/gradlew" "$target/repo/mobile/gradlew" \
        "$target/repo/bin/adb" "$target/repo/bin/unzip"
    : >"$log"
}

fail_case() {
    label=$1
    expected=$2
    dir="$scratch_root/$label"
    make_repo "$dir"
    shift 2
    if (
        cd "$dir/repo"
        LOG_FILE="$PWD/invocations.log" PATH="$PWD/bin:$PATH" "$@" scripts/check-gradle
    ) >"$dir/stdout" 2>"$dir/stderr"; then
        cat "$dir/stdout" "$dir/stderr" >&2
        printf 'expected Gradle gate failure for %s\n' "$label" >&2
        exit 1
    fi
    if [ -n "$expected" ]; then
        grep -q "$expected" "$dir/stderr" || {
            cat "$dir/stderr" >&2
            printf 'missing expected failure marker %s for %s\n' "$expected" "$label" >&2
            exit 1
        }
    fi
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

dir="$scratch_root/inventory"
make_repo "$dir"
(
    cd "$dir/repo"
    LOG_FILE="$PWD/invocations.log" PATH="$PWD/bin:$PATH" scripts/check-gradle
) >/dev/null
expected="$dir/expected.log"
cat >"$expected" <<'EOF'
credentials
scope
bruno
backend -p REPO/backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :features:access:test :features:access:integrationTest :features:groups:test :features:groups:integrationTest :bootstrap:test :bootstrap:emulatorTest :architecture-tests:test --console=plain
adb devices
mobile-boundaries
mobile -p REPO/mobile :core:common:allTests :core:design-system:allTests :core:design-system:bundleAndroidMainAar :core:domain:allTests :core:network:allTests :features:access:domain:allTests :features:access:data:allTests :features:access:compileAndroidMain :features:access:allTests :features:groups:domain:allTests :features:groups:data:allTests :features:groups:compileAndroidMain :features:groups:allTests :compose-app:allTests :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain
EOF
sed -E 's#-p [^ ]+/backend#-p REPO/backend#g; s#-p [^ ]+/mobile#-p REPO/mobile#g' \
    "$dir/repo/invocations.log" >"$dir/actual.log"
diff -u "$expected" "$dir/actual.log"
count=$((count + 1))
printf 'ok %d - inventory\n' "$count"

fail_case credential-failure '' env FAIL_CREDENTIALS=1
fail_case scope-failure '' env FAIL_SCOPE=1
fail_case bruno-failure '' env FAIL_BRUNO=1
fail_case firebase-emulator-failure '' env FAIL_FIREBASE_EMULATOR=1
fail_case android-instrumented-failure '' env FAIL_ANDROID_INSTRUMENTED=1
fail_case mobile-boundaries-failure '' env FAIL_MOBILE_BOUNDARIES=1

# mobileBoundariesBeforeAggregate: the boundary gate runs before the real mobile
# Gradle aggregate and never triggers a mobile Gradle invocation itself.
boundaries_dir="$scratch_root/mobile-boundaries-blocks-aggregate"
make_repo "$boundaries_dir"
(
    cd "$boundaries_dir/repo"
    LOG_FILE="$PWD/invocations.log" PATH="$PWD/bin:$PATH" FAIL_MOBILE_BOUNDARIES=1 scripts/check-gradle
) >/dev/null 2>&1 || true
grep -q '^mobile-boundaries$' "$boundaries_dir/repo/invocations.log"
if grep -q '^mobile ' "$boundaries_dir/repo/invocations.log"; then
    printf 'mobileBoundariesBeforeAggregate: mobile Gradle aggregate ran despite boundary gate failure\n' >&2
    exit 1
fi
count=$((count + 1)); printf 'ok %d - mobileBoundariesBeforeAggregate\n' "$count"

# --- T38: mandatory KMP + Android suites -----------------------------------

mobile_line() {
    grep '^mobile ' "$1" | sed -E 's#.* -p [^ ]+/mobile ##; s# --console=plain$##'
}

happy="$scratch_root/happy"
make_repo "$happy"
(
    cd "$happy/repo"
    LOG_FILE="$PWD/invocations.log" PATH="$PWD/bin:$PATH" scripts/check-gradle
) >/dev/null
happy_log="$happy/repo/invocations.log"

required_suites=':core:common:allTests :core:design-system:allTests :core:design-system:bundleAndroidMainAar :core:domain:allTests :core:network:allTests :features:access:domain:allTests :features:access:data:allTests :features:access:compileAndroidMain :features:access:allTests :features:groups:domain:allTests :features:groups:data:allTests :features:groups:compileAndroidMain :features:groups:allTests :compose-app:allTests :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest'

# exactInventory: the mobile invocation lists exactly the required suites.
[ "$(mobile_line "$happy_log")" = "$required_suites" ] || {
    printf 'exactInventory: mobile suites were %s\n' "$(mobile_line "$happy_log")" >&2
    exit 1
}
count=$((count + 1))
printf 'ok %d - exactInventory\n' "$count"

# exactOrder: credentials, scope, Bruno, backend, adb precede mobile suites.
awk '
    /^credentials$/ { seen["credentials"] = ++i }
    /^scope$/ { seen["scope"] = ++i }
    /^bruno$/ { seen["bruno"] = ++i }
    /^backend / { seen["backend"] = ++i }
    /^adb / { seen["adb"] = ++i }
    /^mobile-boundaries$/ { seen["mobile-boundaries"] = ++i }
    /^mobile / { seen["mobile"] = ++i }
    END {
        ok = seen["credentials"] < seen["scope"] &&
             seen["scope"] < seen["bruno"] &&
             seen["bruno"] < seen["backend"] &&
             seen["backend"] < seen["adb"] &&
             seen["adb"] < seen["mobile-boundaries"] &&
             seen["mobile-boundaries"] < seen["mobile"]
        exit ok ? 0 : 1
    }
' "$happy_log" || { printf 'exactOrder: stages out of order\n' >&2; exit 1; }
count=$((count + 1))
printf 'ok %d - exactOrder\n' "$count"

# credentialsFirst / scopeSecond: gate order guards run before any build.
[ "$(sed -n '1p' "$happy_log")" = credentials ] || { printf 'credentialsFirst failed\n' >&2; exit 1; }
count=$((count + 1))
printf 'ok %d - credentialsFirst\n' "$count"
[ "$(sed -n '2p' "$happy_log")" = scope ] || { printf 'scopeSecond failed\n' >&2; exit 1; }
count=$((count + 1))
printf 'ok %d - scopeSecond\n' "$count"

# eachSuiteFailurePropagates: removing/breaking any one suite is fatal.
for suite in $required_suites; do
    fail_case "suite-failure-$suite" '' env FAIL_TASK="$suite"
done

# adbMissingFails: no adb on PATH at all is fatal (clean PATH, no stub bin).
adb_dir="$scratch_root/adbMissingFails"
make_repo "$adb_dir"
rm -f "$adb_dir/repo/bin/adb"
if (
    cd "$adb_dir/repo"
    LOG_FILE="$PWD/invocations.log" PATH="/usr/bin:/bin" scripts/check-gradle
) >"$adb_dir/stdout" 2>"$adb_dir/stderr"; then
    cat "$adb_dir/stdout" "$adb_dir/stderr" >&2
    printf 'expected Gradle gate failure for adbMissingFails\n' >&2
    exit 1
fi
grep -q 'adb is required' "$adb_dir/stderr" || { cat "$adb_dir/stderr" >&2; exit 1; }
count=$((count + 1))
printf 'ok %d - adbMissingFails\n' "$count"

fail_case deviceMissingFails 'emulator/device is required' env NO_ANDROID_DEVICE=1
fail_case zeroTestsFails 'zero tests discovered' env ZERO_TESTS=1
# V1: a resource-owning Compose KMP Android library must carry its own resources.
fail_case v1-preview-resource-missing 'missing Compose resources required by Preview' env MISSING_PREVIEW_RESOURCE=1

# backendInventory: the backend invocation is byte-for-byte the required inventory.
backend_expected=':shared-kernel:check :features:identity:test :features:identity:emulatorTest :features:access:test :features:access:integrationTest :features:groups:test :features:groups:integrationTest :bootstrap:test :bootstrap:emulatorTest :architecture-tests:test'
backend_actual=$(grep '^backend ' "$happy_log" | sed -E 's#.* -p [^ ]+/backend ##; s# --console=plain$##')
[ "$backend_actual" = "$backend_expected" ] || {
    printf 'backendInventory: backend suites were %s\n' "$backend_actual" >&2
    exit 1
}
count=$((count + 1))
printf 'ok %d - backendInventory\n' "$count"

identity_build="$repository_root/backend/features/identity/build.gradle.kts"
identity_test="$repository_root/backend/features/identity/src/test/kotlin/br/com/saqz/identity/adapter/output/firebase/FirebaseAdminTokenVerifierEmulatorTest.kt"
grep -Fq 'systemProperty("session.fixture"' "$identity_build"
grep -Fq 'System.getProperty("session.fixture")' "$identity_test"
grep -Fq 'SAQZ_FIXTURE_HOLD' "$identity_test"
grep -Fq 'port-bindable' "$identity_test"
if grep -Fq '"npx", "--yes", "firebase-tools@' "$identity_test"; then
    printf 'identity emulator test must use the shared lifecycle fixture\n' >&2
    exit 1
fi
count=$((count + 1))
printf 'ok %d - shared identity emulator lifecycle\n' "$count"

# --- T56: authentication access inventory and mutations --------------------

case " $backend_actual " in
    *' :features:access:test :features:access:integrationTest '*) ;;
    *) printf 'backend access inventory missing or out of order\n' >&2; exit 1 ;;
esac
count=$((count + 1)); printf 'ok %d - backendAccessInventory\n' "$count"

backend_position=$(grep -n '^backend ' "$happy_log" | cut -d: -f1)
adb_position=$(grep -n '^adb ' "$happy_log" | cut -d: -f1)
[ "$backend_position" -lt "$adb_position" ]
count=$((count + 1)); printf 'ok %d - backendAccessBeforeAdb\n' "$count"

case " $(mobile_line "$happy_log") " in
    *' :core:network:allTests :features:access:domain:allTests :features:access:data:allTests :features:access:compileAndroidMain :features:access:allTests '*) ;;
    *) printf 'mobile access inventory missing or out of order\n' >&2; exit 1 ;;
esac
count=$((count + 1)); printf 'ok %d - mobileAccessInventory\n' "$count"

fail_case access-unit-failure '' env FAIL_TASK=:features:access:test
fail_case access-integration-failure '' env FAIL_TASK=:features:access:integrationTest
fail_case network-failure '' env FAIL_TASK=:core:network:allTests
fail_case access-mobile-failure '' env FAIL_TASK=:features:access:allTests
fail_case backend-zero-tests 'backend suites reported BUILD SUCCESS with zero tests discovered' env ZERO_BACKEND_TESTS=1

if grep -Eq 'BRANCH_KEY|GOOGLE_CLIENT|service.account|production.secret' "$repository_root/scripts/check-gradle"; then
    printf 'gate must remain credential-free\n' >&2; exit 1
fi
count=$((count + 1)); printf 'ok %d - credentialFreeAccessGate\n' "$count"

# --- T67: mandatory Groups inventory and mutation resistance ---------------

case " $backend_actual " in
    *' :features:access:integrationTest :features:groups:test :features:groups:integrationTest :bootstrap:test '*) ;;
    *) printf 'backend Groups inventory missing or out of order\n' >&2; exit 1 ;;
esac
count=$((count + 1)); printf 'ok %d - backendGroupsInventory\n' "$count"

case " $(mobile_line "$happy_log") " in
    *' :features:access:allTests :features:groups:domain:allTests :features:groups:data:allTests :features:groups:compileAndroidMain :features:groups:allTests :compose-app:allTests '*) ;;
    *) printf 'mobile Groups inventory missing or out of order\n' >&2; exit 1 ;;
esac
count=$((count + 1)); printf 'ok %d - mobileGroupsInventory\n' "$count"

fail_case groups-unit-failure '' env FAIL_TASK=:features:groups:test
fail_case groups-integration-failure '' env FAIL_TASK=:features:groups:integrationTest
fail_case groups-mobile-compile-failure '' env FAIL_TASK=:features:groups:compileAndroidMain
fail_case groups-mobile-tests-failure '' env FAIL_TASK=:features:groups:allTests

[ "$count" -eq 49 ]
