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
case " $* " in
    *" :android-app:connectedDebugAndroidTest "*)
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
backend -p REPO/backend :shared-kernel:check :features:identity:test :features:identity:emulatorTest :bootstrap:test :bootstrap:emulatorTest :architecture-tests:test --console=plain
adb devices
mobile -p REPO/mobile :compose-app:allTests :android-app:testDebugUnitTest :android-app:connectedDebugAndroidTest --console=plain
EOF
sed -E 's#-p [^ ]+/backend#-p REPO/backend#g; s#-p [^ ]+/mobile#-p REPO/mobile#g' \
    "$dir/repo/invocations.log" >"$dir/actual.log"
diff -u "$expected" "$dir/actual.log"
count=$((count + 1))
printf 'ok %d - inventory\n' "$count"

fail_case credential-failure '' env FAIL_CREDENTIALS=1
fail_case scope-failure '' env FAIL_SCOPE=1
fail_case firebase-emulator-failure '' env FAIL_FIREBASE_EMULATOR=1
fail_case android-instrumented-failure '' env FAIL_ANDROID_INSTRUMENTED=1

[ "$count" -eq 5 ]
