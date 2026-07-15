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
    /^[[:space:]]*angular-gate:[[:space:]]*$/ { in_gradle = 0 }
    in_gradle && /actions\/setup-node/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$workflow" >/dev/null 2>&1 && {
    printf 'gradle job must not set up Node\n' >&2
    exit 1
}
ok 'gradle job identity and command'

assert_workflow '^[[:space:]]*angular-gate:[[:space:]]*$' 'angular job'
assert_workflow 'run:[[:space:]]*npm --prefix frontend ci' 'angular install'
assert_workflow 'run:[[:space:]]*npm --prefix frontend run lint' 'angular lint'
assert_workflow 'run:[[:space:]]*npm --prefix frontend run test:ci' 'angular test'
assert_workflow 'run:[[:space:]]*npm --prefix frontend run build' 'angular build'
ok 'angular job identity and commands'

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
assert_workflow 'api-level:[[:space:]]*35' 'android emulator api level'
assert_workflow 'target:[[:space:]]*google_apis' 'android emulator target'
assert_workflow 'arch:[[:space:]]*x86_64' 'android emulator abi'
assert_workflow 'avd-name:[[:space:]]*saqz-ci' 'android avd name'
assert_workflow 'script:[[:space:]]*scripts/check-gradle' 'gradle gate under emulator action'
assert_workflow 'scripts/check-gradle' 'gradle gate under emulator'
ok 'gradle emulator provisioning'

assert_workflow 'emulator-build:[[:space:]]*13823996' 'pinned stable Android emulator build'
assert_workflow 'emulator-boot-timeout:[[:space:]]*300' 'bounded Android emulator boot'
assert_workflow 'pre-emulator-launch-script:[[:space:]]*adb start-server' 'ADB starts before Android emulator'
awk '
    /^[[:space:]]*gradle-gate:[[:space:]]*$/ { in_gradle = 1 }
    /^[[:space:]]*angular-gate:[[:space:]]*$/ { in_gradle = 0 }
    in_gradle && /^[[:space:]]*timeout-minutes:[[:space:]]*45[[:space:]]*$/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$workflow" || {
    printf 'gradle job must have a finite timeout\n' >&2
    exit 1
}
ok 'android emulator boot guard'

grep -Eq 'SAQZ_JAVA_HOME:-\$\{JAVA_HOME:-' "$repository_root/scripts/check-ios" || {
    printf 'iOS gate must preserve runner-provided JAVA_HOME before using local fallback\n' >&2
    exit 1
}
ok 'ios java home fallback'

grep -Eq 'scripts/check-credentials' "$repository_root/scripts/check-gradle"
grep -Eq 'scripts/check-scope' "$repository_root/scripts/check-gradle"

assert_workflow '^[[:space:]]*initialization-gate:[[:space:]]*$' 'aggregate job'
assert_workflow 'needs:[[:space:]]*\[gradle-gate, angular-gate, ios-gate, landing-gate\]' 'aggregate needs'
assert_workflow 'if:[[:space:]]*always\(\)' 'aggregate always'
assert_workflow 'scripts/evaluate-ci-gates "\$GRADLE_RESULT" "\$ANGULAR_RESULT" "\$IOS_RESULT" "\$LANDING_RESULT"' 'aggregate evaluator'
"$evaluator" success success success success >/dev/null

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

for gate in gradle angular ios landing; do
    gradle=success
    angular=success
    ios=success
    landing=success
    case "$gate" in
        gradle) gradle=failure ;;
        angular) angular=failure ;;
        ios) ios=failure ;;
        landing) landing=failure ;;
    esac
    if "$evaluator" "$gradle" "$angular" "$ios" "$landing" >/dev/null 2>&1; then
        printf 'aggregate accepted %s failure\n' "$gate" >&2
        exit 1
    fi
    ok "aggregate rejects $gate failure"
done

for gate in gradle angular ios landing; do
    gradle=success
    angular=success
    ios=success
    landing=success
    case "$gate" in
        gradle) gradle=cancelled ;;
        angular) angular=cancelled ;;
        ios) ios=cancelled ;;
        landing) landing=cancelled ;;
    esac
    if "$evaluator" "$gradle" "$angular" "$ios" "$landing" >/dev/null 2>&1; then
        printf 'aggregate accepted %s cancellation\n' "$gate" >&2
        exit 1
    fi
    ok "aggregate rejects $gate cancellation"
done

[ "$count" -eq 19 ]
