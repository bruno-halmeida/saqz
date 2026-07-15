#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-check-ios-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM

mkdir -p "$scratch_root/bin" "$scratch_root/jdk/bin"

cat >"$scratch_root/bin/xcrun" <<'SH'
#!/bin/sh
set -eu
case "$*" in
    "--sdk iphonesimulator --show-sdk-version")
        printf '%s\n' "$FAKE_IOS_SDK_VERSION"
        ;;
    "simctl list devices available -j")
        printf '%s\n' "$FAKE_SIMCTL_JSON"
        ;;
    *)
        printf 'unexpected xcrun command: %s\n' "$*" >&2
        exit 2
        ;;
esac
SH

cat >"$scratch_root/bin/xcodebuild" <<'SH'
#!/bin/sh
set -eu
printf '%s\n' "$*" >>"$XCODEBUILD_LOG"
ordinal=$(wc -l <"$XCODEBUILD_LOG" | tr -d ' ')
if [ "${FAIL_INVOCATION:-0}" = "$ordinal" ]; then
    printf 'xcodebuild simulated failure\n' >&2
    exit 65
fi
case " $* " in
    *" test "*)
        if [ "${ZERO_TESTS:-0}" = 1 ]; then
            printf 'Test Suite passed\nExecuted 0 tests, with 0 failures\n'
        else
            printf 'Test Suite passed\nExecuted 5 tests, with 0 failures\n'
        fi
        ;;
esac
SH

chmod +x "$scratch_root/bin/xcrun" "$scratch_root/bin/xcodebuild"

matching_devices='{"devices":{"com.apple.CoreSimulator.SimRuntime.iOS-26-5":[{"isAvailable":true,"udid":"UDID-26-5"}],"com.apple.CoreSimulator.SimRuntime.iOS-26-4":[{"isAvailable":true,"udid":"UDID-26-4"}]}}'

run_check_ios() {
    log_target=$1
    shift
    : >"$log_target"
    env PATH="$scratch_root/bin:$PATH" \
        SAQZ_JAVA_HOME="$scratch_root/jdk" \
        FAKE_IOS_SDK_VERSION=26.4 \
        FAKE_SIMCTL_JSON="$matching_devices" \
        XCODEBUILD_LOG="$log_target" \
        "$@" \
        "$repository_root/scripts/check-ios"
}

count=0
pass() { count=$((count + 1)); printf 'ok %d - %s\n' "$count" "$1"; }
has() { case " $1 " in *" $2 "*) return 0 ;; *) return 1 ;; esac; }
require() { has "$1" "$2" || { printf '%s\n  missing token: [%s]\n  in: %s\n' "$3" "$2" "$1" >&2; exit 1; }; }
refuse() { has "$1" "$2" && { printf '%s\n  forbidden token: [%s]\n  in: %s\n' "$3" "$2" "$1" >&2; exit 1; } || true; }

# --- ok 1: runtime selection (unchanged behaviour) --------------------------
happy_log="$scratch_root/happy.log"
run_check_ios "$happy_log" >/dev/null
grep -q -- '-destination id=UDID-26-4' "$happy_log"
pass 'selects simulator runtime matching active Xcode SDK'

# --- ok 2: missingRuntimeFails ----------------------------------------------
missing_devices='{"devices":{"com.apple.CoreSimulator.SimRuntime.iOS-26-5":[{"isAvailable":true,"udid":"UDID-26-5"}]}}'
miss_log="$scratch_root/missing.log"
: >"$miss_log"
if env PATH="$scratch_root/bin:$PATH" SAQZ_JAVA_HOME="$scratch_root/jdk" \
    FAKE_IOS_SDK_VERSION=26.4 FAKE_SIMCTL_JSON="$missing_devices" \
    XCODEBUILD_LOG="$miss_log" \
    "$repository_root/scripts/check-ios" >"$scratch_root/stdout" 2>"$scratch_root/stderr"; then
    printf 'expected check-ios to reject a mismatched simulator runtime\n' >&2
    exit 1
fi
grep -q 'No available iOS 26.4 Simulator is installed' "$scratch_root/stderr"
[ ! -s "$miss_log" ]
pass 'missingRuntimeFails'

# --- exactThreeInvocations --------------------------------------------------
[ "$(wc -l <"$happy_log" | tr -d ' ')" -eq 3 ] || {
    printf 'expected exactly three xcodebuild invocations, got:\n' >&2
    cat "$happy_log" >&2
    exit 1
}
pass 'exactThreeInvocations'

line1=$(sed -n '1p' "$happy_log")
line2=$(sed -n '2p' "$happy_log")
line3=$(sed -n '3p' "$happy_log")

# --- sameUdid ---------------------------------------------------------------
for line in "$line1" "$line2" "$line3"; do
    case " $line " in
        *" -destination id=UDID-26-4 "*) ;;
        *) printf 'invocation used a different UDID: %s\n' "$line" >&2; exit 1 ;;
    esac
done
pass 'sameUdid'

# --- devIncludesUi ----------------------------------------------------------
require "$line1" '-scheme SaqzDev' 'first invocation is not a SaqzDev run'
require "$line1" 'test' 'first invocation is not a test action'
refuse "$line1" '-only-testing:SaqzIOSTests' 'SaqzDev must run the full suite, not unit-only'
dev_scheme="$repository_root/mobile/ios-app/SaqzIOS.xcodeproj/xcshareddata/xcschemes/SaqzDev.xcscheme"
grep -q 'BuildableName="SaqzIOSUITests.xctest"' "$dev_scheme"
grep -q 'skipped="NO"' "$dev_scheme"
pass 'devIncludesUi'

# --- prodBuildIsRelease -----------------------------------------------------
require "$line2" '-scheme SaqzProd' 'second invocation is not a SaqzProd run'
require "$line2" '-configuration Release' 'SaqzProd build is not Release'
require "$line2" 'build' 'second invocation is not a build action'
refuse "$line2" 'test' 'SaqzProd build invocation must not run tests'
pass 'prodBuildIsRelease'

# --- prodOnlyRunsUnit -------------------------------------------------------
require "$line3" '-scheme SaqzProd' 'third invocation is not a SaqzProd run'
require "$line3" '-configuration Release' 'SaqzProd test is not Release'
require "$line3" '-only-testing:SaqzIOSTests' 'SaqzProd test is not unit-only'
require "$line3" 'test' 'third invocation is not a test action'
refuse "$line3" '-only-testing:SaqzIOSUITests' 'SaqzProd must never run UI tests'
pass 'prodOnlyRunsUnit'

# --- codeSigningDisabled ----------------------------------------------------
for line in "$line1" "$line2" "$line3"; do
    case " $line " in
        *" CODE_SIGNING_ALLOWED=NO "*) ;;
        *) printf 'invocation did not disable code signing: %s\n' "$line" >&2; exit 1 ;;
    esac
done
pass 'codeSigningDisabled'

# --- eachInvocationFailurePropagates ----------------------------------------
for inv in 1 2 3; do
    prop_log="$scratch_root/fail-$inv.log"
    if run_check_ios "$prop_log" env FAIL_INVOCATION="$inv" \
        >"$scratch_root/stdout" 2>"$scratch_root/stderr"; then
        printf 'check-ios did not propagate failure of invocation %s\n' "$inv" >&2
        exit 1
    fi
    pass "eachInvocationFailurePropagates-$inv"
done

# --- zeroTestsFails ---------------------------------------------------------
zero_log="$scratch_root/zero.log"
if run_check_ios "$zero_log" env ZERO_TESTS=1 \
    >"$scratch_root/stdout" 2>"$scratch_root/stderr"; then
    printf 'check-ios accepted a run with zero XCTest cases\n' >&2
    exit 1
fi
grep -q 'ran zero tests' "$scratch_root/stderr"
pass 'zeroTestsFails'

[ "$count" -eq 12 ]
