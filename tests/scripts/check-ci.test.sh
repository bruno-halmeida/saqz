#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
workflow="$repository_root/.github/workflows/initialization-gate.yml"
pages_workflow="$repository_root/.github/workflows/deploy-pages.yml"
evaluator="$repository_root/scripts/evaluate-ci-gates"
count=0

ok() {
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$1"
}

assert_workflow() {
    pattern=$1
    label=$2
    grep -Eq "$pattern" "$workflow" || {
        printf 'missing workflow contract: %s\n' "$label" >&2
        exit 1
    }
}

assert_workflow '^[[:space:]]*pull_request:[[:space:]]*$' 'pull request trigger'
assert_workflow '^[[:space:]]*branches:[[:space:]]*\[[[:space:]]*main[[:space:]]*\]' 'main PR branch'

assert_workflow '^[[:space:]]*gradle-gate:[[:space:]]*$' 'gradle job'
assert_workflow 'scripts/check-gradle' 'gradle command'
awk '
    /^[[:space:]]*gradle-gate:[[:space:]]*$/ { in_gradle = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_gradle = 0 }
    in_gradle && /actions\/setup-node/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$workflow" >/dev/null 2>&1 && {
    printf 'gradle job must not set up Node\n' >&2
    exit 1
}
ok 'gradle job identity and command'

if grep -Eqi 'angular-gate|npm --prefix frontend|frontend/package-lock' "$workflow"; then
    printf 'retired Angular job is still present\n' >&2
    exit 1
fi
ok 'angular job retired'

assert_workflow '^[[:space:]]*ios-gate:[[:space:]]*$' 'ios job'
assert_workflow 'run:[[:space:]]*scripts/check-ios' 'ios command'
ok 'ios job identity and command'

awk '
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_ios = 1 }
    /^[[:space:]]*landing-gate:[[:space:]]*$/ { in_ios = 0 }
    in_ios && /DEVELOPER_DIR:[[:space:]]*\/Applications\/Xcode_26\.4\.app\/Contents\/Developer/ { xcode = 1 }
    in_ios && /^[[:space:]]*timeout-minutes:[[:space:]]*45[[:space:]]*$/ { timeout = 1 }
    END { exit xcode && timeout ? 0 : 1 }
' "$workflow" || {
    printf 'iOS job must pin Xcode 26.4 and have a finite timeout\n' >&2
    exit 1
}
ok 'ios xcode and runtime guard'

assert_workflow '^[[:space:]]*landing-gate:[[:space:]]*$' 'landing job'
assert_workflow 'run:[[:space:]]*scripts/check-landing' 'landing command'
ok 'landing job identity and command'

assert_workflow 'runs-on:[[:space:]]*macos-' 'ios macos runner'
ok 'macos ios runner'

assert_workflow 'uses:[[:space:]]*ReactiveCircus/android-emulator-runner@v2' 'android emulator runner action'
assert_workflow 'avd-name:[[:space:]]*saqz-ci' 'android avd name'
assert_workflow 'script:[[:space:]]*scripts/check-gradle' 'gradle gate under emulator action'
assert_workflow 'scripts/check-gradle' 'gradle gate under emulator'
ok 'gradle emulator provisioning'

assert_workflow 'api-level:[[:space:]]*30' 'android automated test device api level'
assert_workflow 'target:[[:space:]]*google_atd' 'android automated test device target'
assert_workflow 'arch:[[:space:]]*x86' 'android automated test device abi'
assert_workflow 'profile:[[:space:]]*pixel_2' 'android automated test device profile'
assert_workflow 'ram-size:[[:space:]]*2048M' 'android automated test device memory'
ok 'android automated test device image'

assert_workflow 'sudo chmod 0666 /dev/kvm' 'direct Android KVM permission'
assert_workflow 'test -r /dev/kvm' 'Android KVM read access check'
assert_workflow 'test -w /dev/kvm' 'Android KVM write access check'
ok 'android kvm access guard'

assert_workflow 'emulator-build:[[:space:]]*13823996' 'pinned stable Android emulator build'
assert_workflow 'emulator-boot-timeout:[[:space:]]*300' 'bounded Android emulator boot'
assert_workflow 'pre-emulator-launch-script:[[:space:]]*adb start-server' 'ADB starts before Android emulator'
awk '
    /^[[:space:]]*gradle-gate:[[:space:]]*$/ { in_gradle = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_gradle = 0 }
    in_gradle && /^[[:space:]]*timeout-minutes:[[:space:]]*45[[:space:]]*$/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$workflow" || {
    printf 'gradle job must have a finite timeout\n' >&2
    exit 1
}
ok 'android emulator boot guard'

grep -Eq 'SAQZ_JAVA_HOME' "$repository_root/scripts/check-ios"
grep -Eq '/usr/libexec/java_home -v 21' "$repository_root/scripts/check-ios" || {
    printf 'iOS gate must select JDK 21 explicitly before using local fallback\n' >&2
    exit 1
}
ok 'ios java home fallback'

assert_workflow '^[[:space:]]*android-api35-probe:[[:space:]]*$' 'api35 probe job'
ok 'api35 probe jobExists'

awk '
    /^[[:space:]]*android-api35-probe:[[:space:]]*$/ { in_probe = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_probe = 0 }
    in_probe && /^[[:space:]]*api-level:[[:space:]]*35[[:space:]]*$/ { api = 1 }
    in_probe && /^[[:space:]]*target:[[:space:]]*google_apis[[:space:]]*$/ { target = 1 }
    in_probe && /^[[:space:]]*arch:[[:space:]]*x86_64[[:space:]]*$/ { arch = 1 }
    in_probe && /^[[:space:]]*profile:[[:space:]]*pixel_7[[:space:]]*$/ { profile = 1 }
    in_probe && /^[[:space:]]*ram-size:[[:space:]]*4096M[[:space:]]*$/ { ram = 1 }
    in_probe && /^[[:space:]]*avd-name:[[:space:]]*saqz-api35-probe[[:space:]]*$/ { avd = 1 }
    in_probe && /^[[:space:]]*emulator-build:[[:space:]]*13823996[[:space:]]*$/ { build = 1 }
    END { exit api && target && arch && profile && ram && avd && build ? 0 : 1 }
' "$workflow" || {
    printf 'api35 probe tuple must be pinned exactly\n' >&2
    exit 1
}
ok 'api35 tuplePinned'

awk '
    /^[[:space:]]*android-api35-probe:[[:space:]]*$/ { in_probe = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_probe = 0 }
    in_probe && /^[[:space:]]*emulator-boot-timeout:[[:space:]]*300[[:space:]]*$/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$workflow" || {
    printf 'api35 probe must keep boot timeout at 300 seconds\n' >&2
    exit 1
}
ok 'api35 bootTimeoutIs300'

awk '
    /^[[:space:]]*android-api35-probe:[[:space:]]*$/ { in_probe = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_probe = 0 }
    in_probe && /sudo chmod 0666 \/dev\/kvm/ { chmod = 1 }
    in_probe && /test -r \/dev\/kvm/ { read = 1 }
    in_probe && /test -w \/dev\/kvm/ { write = 1 }
    END { exit chmod && read && write ? 0 : 1 }
' "$workflow" || {
    printf 'api35 probe must enable and verify KVM access\n' >&2
    exit 1
}
ok 'api35 kvmEnabled'

awk '
    /^[[:space:]]*android-api35-probe:[[:space:]]*$/ { in_probe = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_probe = 0 }
    in_probe && /:android-app:connectedDevDebugAndroidTest/ { connected = 1 }
    in_probe && /android\.testInstrumentationRunnerArguments\.class=br\.com\.saqz\.androidapp\.ModernAndroidBehaviorTest/ { modern = 1 }
    in_probe && /scripts\/check-gradle/ { full_gate = 1 }
    END { exit connected && modern && !full_gate ? 0 : 1 }
' "$workflow" || {
    printf 'api35 probe must run only ModernAndroidBehaviorTest, not check-gradle\n' >&2
    exit 1
}
ok 'api35 exactModernClassRuns'

awk '
    /^[[:space:]]*android-api35-probe:[[:space:]]*$/ { in_probe = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_probe = 0 }
    in_probe && /continue-on-error/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$workflow" >/dev/null 2>&1 && {
    printf 'api35 probe must be strict internally, without continue-on-error\n' >&2
    exit 1
}
ok 'api35 internalFailureIsFatal'

assert_workflow 'needs:[[:space:]]*\[gradle-gate, ios-gate, landing-gate\]' 'aggregate excludes api35 probe'
if grep -Eq 'needs:[[:space:]]*\[[^]]*android-api35-probe' "$workflow"; then
    printf 'api35 probe must stay outside aggregate until T42 promotion\n' >&2
    exit 1
fi
ok 'api35 probeOutsideAggregate'

"$evaluator" success success success >/dev/null
ok 'api35 missingProbeDoesNotFailCurrentEvaluator'

grep -Eq 'scripts/check-credentials' "$repository_root/scripts/check-gradle"
grep -Eq 'scripts/check-scope' "$repository_root/scripts/check-gradle"

assert_workflow '^[[:space:]]*initialization-gate:[[:space:]]*$' 'aggregate job'
assert_workflow 'needs:[[:space:]]*\[gradle-gate, ios-gate, landing-gate\]' 'aggregate needs'
assert_workflow 'if:[[:space:]]*always\(\)' 'aggregate always'
assert_workflow 'scripts/evaluate-ci-gates "\$GRADLE_RESULT" "\$IOS_RESULT" "\$LANDING_RESULT"' 'aggregate evaluator'
"$evaluator" success success success >/dev/null

if "$evaluator" success success >/dev/null 2>&1; then
    printf 'aggregate evaluator accepted a missing job result\n' >&2
    exit 1
fi
ok 'aggregate rejects missing result'

if "$evaluator" success success success success >/dev/null 2>&1; then
    printf 'aggregate evaluator accepted an extra job result\n' >&2
    exit 1
fi
ok 'aggregate rejects extra result'

if grep -Eqi 'secret|GOOGLE_APPLICATION_CREDENTIALS|service-account|signing|database|deploy-pages|firebase deploy' "$workflow"; then
    printf 'workflow requires forbidden secret/deployment contract\n' >&2
    exit 1
fi
ok 'no production secret contract'

git -C "$repository_root" diff --quiet -- "$pages_workflow" || {
    printf 'Pages workflow has unstaged changes\n' >&2
    exit 1
}
ok 'pages workflow preserved'

for gate in gradle ios landing; do
    gradle=success
    ios=success
    landing=success
    case "$gate" in
        gradle) gradle=failure ;;
        ios) ios=failure ;;
        landing) landing=failure ;;
    esac
    if "$evaluator" "$gradle" "$ios" "$landing" >/dev/null 2>&1; then
        printf 'aggregate accepted %s failure\n' "$gate" >&2
        exit 1
    fi
    ok "aggregate rejects $gate failure"
done

for gate in gradle ios landing; do
    gradle=success
    ios=success
    landing=success
    case "$gate" in
        gradle) gradle=cancelled ;;
        ios) ios=cancelled ;;
        landing) landing=cancelled ;;
    esac
    if "$evaluator" "$gradle" "$ios" "$landing" >/dev/null 2>&1; then
        printf 'aggregate accepted %s cancellation\n' "$gate" >&2
        exit 1
    fi
    ok "aggregate rejects $gate cancellation"
done

[ "$count" -eq 29 ]
