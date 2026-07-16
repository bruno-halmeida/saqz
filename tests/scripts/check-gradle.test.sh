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
    cat >"$target/repo/backend/gradlew" <<'SH'
#!/bin/sh
set -eu
printf 'backend %s\n' "$*" >>"$LOG_FILE"
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
results_dir="$mobile_dir/build/test-results/stub"
mkdir -p "$results_dir"
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
        "$target/repo/scripts/check-bruno" \
        "$target/repo/backend/gradlew" "$target/repo/mobile/gradlew" "$target/repo/bin/adb"
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
backend -p REPO/backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :bootstrap:test :bootstrap:emulatorTest :architecture-tests:test --console=plain
adb devices
mobile -p REPO/mobile :core:common:allTests :core:design-system:allTests :compose-app:allTests :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain
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

required_suites=':core:common:allTests :core:design-system:allTests :compose-app:allTests :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest'

# exactInventory: the mobile invocation lists exactly the five required suites.
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
    /^mobile / { seen["mobile"] = ++i }
    END {
        ok = seen["credentials"] < seen["scope"] &&
             seen["scope"] < seen["bruno"] &&
             seen["bruno"] < seen["backend"] &&
             seen["backend"] < seen["adb"] &&
             seen["adb"] < seen["mobile"]
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

# backendInventoryUnchanged: the backend invocation is byte-for-byte the original.
backend_expected=':shared-kernel:check :features:identity:test :features:identity:emulatorTest :bootstrap:test :bootstrap:emulatorTest :architecture-tests:test'
backend_actual=$(grep '^backend ' "$happy_log" | sed -E 's#.* -p [^ ]+/backend ##; s# --console=plain$##')
[ "$backend_actual" = "$backend_expected" ] || {
    printf 'backendInventoryUnchanged: backend suites were %s\n' "$backend_actual" >&2
    exit 1
}
count=$((count + 1))
printf 'ok %d - backendInventoryUnchanged\n' "$count"

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

[ "$count" -eq 20 ]
