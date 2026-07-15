#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-check-ios-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM

mkdir -p "$scratch_root/bin" "$scratch_root/jdk/bin"
: >"$scratch_root/xcodebuild.log"

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
SH

chmod +x "$scratch_root/bin/xcrun" "$scratch_root/bin/xcodebuild"

matching_devices='{"devices":{"com.apple.CoreSimulator.SimRuntime.iOS-26-5":[{"isAvailable":true,"udid":"UDID-26-5"}],"com.apple.CoreSimulator.SimRuntime.iOS-26-4":[{"isAvailable":true,"udid":"UDID-26-4"}]}}'

PATH="$scratch_root/bin:$PATH" \
SAQZ_JAVA_HOME="$scratch_root/jdk" \
FAKE_IOS_SDK_VERSION=26.4 \
FAKE_SIMCTL_JSON="$matching_devices" \
XCODEBUILD_LOG="$scratch_root/xcodebuild.log" \
    "$repository_root/scripts/check-ios" >/dev/null

grep -q -- '-destination id=UDID-26-4' "$scratch_root/xcodebuild.log"
printf 'ok 1 - selects simulator runtime matching active Xcode SDK\n'

missing_devices='{"devices":{"com.apple.CoreSimulator.SimRuntime.iOS-26-5":[{"isAvailable":true,"udid":"UDID-26-5"}]}}'
: >"$scratch_root/xcodebuild.log"

if PATH="$scratch_root/bin:$PATH" \
    SAQZ_JAVA_HOME="$scratch_root/jdk" \
    FAKE_IOS_SDK_VERSION=26.4 \
    FAKE_SIMCTL_JSON="$missing_devices" \
    XCODEBUILD_LOG="$scratch_root/xcodebuild.log" \
        "$repository_root/scripts/check-ios" >"$scratch_root/stdout" 2>"$scratch_root/stderr"; then
    printf 'expected check-ios to reject a mismatched simulator runtime\n' >&2
    exit 1
fi

grep -q 'No available iOS 26.4 Simulator is installed' "$scratch_root/stderr"
[ ! -s "$scratch_root/xcodebuild.log" ]
printf 'ok 2 - rejects missing matching simulator runtime\n'
