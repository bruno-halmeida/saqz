#!/bin/sh
set -eu

# Spec do contrato de CI pós-VUL-23: PRs bloqueiam só em gates rápidos e
# path-filtered; emulador Android e iOS vivem em full-ui-gate.yml (nightly +
# workflow_dispatch), fora do caminho de PR.

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
workflow="$repository_root/.github/workflows/initialization-gate.yml"
full_ui_workflow="$repository_root/.github/workflows/full-ui-gate.yml"
probe_workflow="$repository_root/.github/workflows/api35-probe.yml"
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

assert_full_ui() {
    pattern=$1
    label=$2
    grep -Eq "$pattern" "$full_ui_workflow" || {
        printf 'missing full-ui-gate contract: %s\n' "$label" >&2
        exit 1
    }
}

# --- Workflow de PR: rápido e path-filtered --------------------------------

assert_workflow '^[[:space:]]*pull_request:[[:space:]]*$' 'pull request trigger'
assert_workflow '^[[:space:]]*branches:[[:space:]]*\[[[:space:]]*main[[:space:]]*\]' 'main PR branch'
ok 'pr triggers'

assert_workflow '^concurrency:[[:space:]]*$' 'concurrency group'
assert_workflow 'cancel-in-progress:[[:space:]]*true' 'cancel-in-progress'
ok 'concurrency cancels stale runs'

assert_workflow '^[[:space:]]*changes:[[:space:]]*$' 'changes job'
assert_workflow 'uses:[[:space:]]*dorny/paths-filter@v3' 'paths-filter action'
ok 'workspace change detection'

assert_workflow '^[[:space:]]*scripts-gate:[[:space:]]*$' 'scripts job'
assert_workflow 'run:[[:space:]]*scripts/test-scripts' 'scripts command'
ok 'scripts gate identity and command'

assert_workflow '^[[:space:]]*backend-gate:[[:space:]]*$' 'backend job'
assert_workflow 'run:[[:space:]]*scripts/check-gradle --backend[[:space:]]*$' 'backend scoped command'
ok 'backend gate identity and command'

assert_workflow '^[[:space:]]*mobile-fast-gate:[[:space:]]*$' 'mobile job'
assert_workflow 'run:[[:space:]]*scripts/check-gradle --mobile-jvm[[:space:]]*$' 'mobile scoped command'
ok 'mobile fast gate identity and command'

assert_workflow '^[[:space:]]*landing-gate:[[:space:]]*$' 'landing job'
assert_workflow 'run:[[:space:]]*scripts/check-landing' 'landing command'
ok 'landing job identity and command'

# Cada gate de PR só dispara quando o workspace muda (ou fora de PR).
gate_count=$(grep -Ec "if:[[:space:]]*github\.event_name != 'pull_request' \|\| needs\.changes\.outputs\.[a-z]+ == 'true'" "$workflow")
[ "$gate_count" -eq 4 ] || {
    printf 'expected 4 path-filtered gates, found %s\n' "$gate_count" >&2
    exit 1
}
ok 'four path-filtered gates'

# O caminho de PR não pode conter emulador, macOS ou instrumentado.
if grep -Eq 'android-emulator-runner|runs-on:[[:space:]]*macos-|connectedDevDebugAndroidTest|DEVELOPER_DIR|/dev/kvm' "$workflow"; then
    printf 'UI/instrumented workloads must not block the PR path\n' >&2
    exit 1
fi
ok 'pr path free of ui workloads'

if grep -Eqi 'angular-gate|npm --prefix frontend|frontend/package-lock' "$workflow"; then
    printf 'retired Angular job is still present\n' >&2
    exit 1
fi
ok 'angular job retired'

awk '
    /^[[:space:]]*backend-gate:[[:space:]]*$/ { in_backend = 1 }
    /^[[:space:]]*mobile-fast-gate:[[:space:]]*$/ { in_backend = 0 }
    in_backend && /docker info/ { docker = 1 }
    in_backend && /npx --yes firebase-tools@15\.23\.0 --version/ { firebase = 1 }
    END { exit docker && firebase ? 0 : 1 }
' "$workflow" || {
    printf 'backend gate must verify Docker and pinned firebase-tools\n' >&2
    exit 1
}
ok 'backend disposable tooling pinned'

if grep -Eq 'actions/setup-node' "$workflow"; then
    printf 'PR gates must not set up Node\n' >&2
    exit 1
fi
ok 'no node setup in pr gates'

# --- Agregado ---------------------------------------------------------------

assert_workflow '^[[:space:]]*initialization-gate:[[:space:]]*$' 'aggregate job'
assert_workflow 'needs:[[:space:]]*\[scripts-gate, backend-gate, mobile-fast-gate, landing-gate\]' 'aggregate needs'
assert_workflow 'if:[[:space:]]*always\(\)' 'aggregate always'
assert_workflow 'scripts/evaluate-ci-gates "\$SCRIPTS_RESULT" "\$BACKEND_RESULT" "\$MOBILE_RESULT" "\$LANDING_RESULT"' 'aggregate evaluator'
assert_workflow 'MOBILE_RESULT:[[:space:]]*\$\{\{[[:space:]]*needs\.mobile-fast-gate\.result[[:space:]]*\}\}' 'mobile aggregate result binding'
ok 'aggregate wiring'

"$evaluator" success success success success >/dev/null
ok 'evaluator accepts four successes'

"$evaluator" success skipped skipped success >/dev/null
ok 'evaluator accepts skipped gates'

"$evaluator" skipped skipped skipped skipped >/dev/null
ok 'evaluator accepts all skipped'

grep -Eq 'scripts-result backend-result mobile-result landing-result' "$evaluator" || {
    printf 'aggregate evaluator usage must name all four results\n' >&2
    exit 1
}
ok 'evaluator usage names four results'

if "$evaluator" success success success >/dev/null 2>&1; then
    printf 'aggregate evaluator accepted a missing job result\n' >&2
    exit 1
fi
ok 'aggregate rejects missing result'

if "$evaluator" success '' success success >/dev/null 2>&1; then
    printf 'aggregate evaluator accepted an empty job result\n' >&2
    exit 1
fi
ok 'aggregate rejects empty result'

if "$evaluator" success success success success success >/dev/null 2>&1; then
    printf 'aggregate evaluator accepted an extra job result\n' >&2
    exit 1
fi
ok 'aggregate rejects extra result'

for gate in scripts backend mobile landing; do
    scripts=success; backend=success; mobile=success; landing=success
    case "$gate" in
        scripts) scripts=failure ;;
        backend) backend=failure ;;
        mobile) mobile=failure ;;
        landing) landing=failure ;;
    esac
    if "$evaluator" "$scripts" "$backend" "$mobile" "$landing" >/dev/null 2>&1; then
        printf 'aggregate accepted %s failure\n' "$gate" >&2
        exit 1
    fi
    ok "aggregate rejects $gate failure"
done

for gate in scripts backend mobile landing; do
    scripts=success; backend=success; mobile=success; landing=success
    case "$gate" in
        scripts) scripts=cancelled ;;
        backend) backend=cancelled ;;
        mobile) mobile=cancelled ;;
        landing) landing=cancelled ;;
    esac
    if "$evaluator" "$scripts" "$backend" "$mobile" "$landing" >/dev/null 2>&1; then
        printf 'aggregate accepted %s cancellation\n' "$gate" >&2
        exit 1
    fi
    ok "aggregate rejects $gate cancellation"
done

# --- full-ui-gate.yml: emulador + iOS fora do caminho de PR -----------------

assert_full_ui '^[[:space:]]*schedule:[[:space:]]*$' 'nightly schedule trigger'
assert_full_ui '^[[:space:]]*workflow_dispatch:[[:space:]]*$' 'manual trigger'
if grep -Eq '^[[:space:]]*pull_request:' "$full_ui_workflow"; then
    printf 'full UI gate must not trigger on pull requests\n' >&2
    exit 1
fi
ok 'full ui triggers nightly and manual only'

assert_full_ui '^[[:space:]]*android-ui-gate:[[:space:]]*$' 'android ui job'
assert_full_ui 'uses:[[:space:]]*ReactiveCircus/android-emulator-runner@v2' 'android emulator runner action'
assert_full_ui 'script:[[:space:]]*scripts/check-gradle --instrumented[[:space:]]*$' 'instrumented scoped command'
ok 'android ui job identity and command'

assert_full_ui 'api-level:[[:space:]]*30' 'android api level'
assert_full_ui 'target:[[:space:]]*google_atd' 'android target'
assert_full_ui 'arch:[[:space:]]*x86' 'android abi'
assert_full_ui 'profile:[[:space:]]*pixel_2' 'android profile'
assert_full_ui 'ram-size:[[:space:]]*2048M' 'android memory'
assert_full_ui 'avd-name:[[:space:]]*saqz-ci' 'android avd name'
assert_full_ui 'emulator-build:[[:space:]]*13823996' 'pinned emulator build'
assert_full_ui 'emulator-boot-timeout:[[:space:]]*300' 'bounded emulator boot'
assert_full_ui 'pre-emulator-launch-script:[[:space:]]*adb start-server' 'adb before emulator'
ok 'android emulator tuple pinned'

assert_full_ui 'sudo chmod 0666 /dev/kvm' 'direct Android KVM permission'
assert_full_ui 'test -r /dev/kvm' 'Android KVM read access check'
assert_full_ui 'test -w /dev/kvm' 'Android KVM write access check'
ok 'android kvm access guard'

assert_full_ui '^[[:space:]]*ios-gate:[[:space:]]*$' 'ios job'
assert_full_ui 'runs-on:[[:space:]]*macos-' 'ios macos runner'
assert_full_ui 'run:[[:space:]]*scripts/check-ios --dev-only[[:space:]]*$' 'iOS Dev-only command'
awk '
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_ios = 1 }
    in_ios && /DEVELOPER_DIR:[[:space:]]*\/Applications\/Xcode_26\.4\.app\/Contents\/Developer/ { xcode = 1 }
    in_ios && /^[[:space:]]*timeout-minutes:[[:space:]]*45[[:space:]]*$/ { timeout = 1 }
    END { exit xcode && timeout ? 0 : 1 }
' "$full_ui_workflow" || {
    printf 'iOS job must pin Xcode 26.4 and have a finite timeout\n' >&2
    exit 1
}
ok 'ios job pinned and bounded'

awk '
    /^[[:space:]]*android-ui-gate:[[:space:]]*$/ { in_android = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_android = 0 }
    in_android && /^[[:space:]]*timeout-minutes:[[:space:]]*45[[:space:]]*$/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$full_ui_workflow" || {
    printf 'android ui job must have a finite timeout\n' >&2
    exit 1
}
ok 'android ui timeout bounded'

grep -Eq 'SAQZ_JAVA_HOME' "$repository_root/scripts/check-ios"
grep -Eq '/usr/libexec/java_home -v 21' "$repository_root/scripts/check-ios" || {
    printf 'iOS gate must select JDK 21 explicitly before using local fallback\n' >&2
    exit 1
}
ok 'ios java home fallback'

# --- api35 probe: inalterado ------------------------------------------------

assert_probe() {
    pattern=$1
    label=$2
    grep -Eq "$pattern" "$probe_workflow" || {
        printf 'missing probe contract: %s\n' "$label" >&2
        exit 1
    }
}

assert_probe '^[[:space:]]*android-api35-gate:[[:space:]]*$' 'api35 gate job'
ok 'api35 probeJobExists'

if grep -Eq '^[[:space:]]*android-api35-gate:[[:space:]]*$' "$workflow"; then
    printf 'api35 gate must not block the PR initialization gate\n' >&2
    exit 1
fi
ok 'api35 outOfPrPath'

assert_probe '^[[:space:]]*schedule:[[:space:]]*$' 'nightly schedule trigger'
assert_probe '^[[:space:]]*workflow_dispatch:[[:space:]]*$' 'manual trigger'
ok 'api35 nightlyAndManualTriggers'

awk '
    /^[[:space:]]*android-api35-gate:[[:space:]]*$/ { in_gate = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_gate = 0 }
    in_gate && /^[[:space:]]*api-level:[[:space:]]*35[[:space:]]*$/ { api = 1 }
    in_gate && /^[[:space:]]*target:[[:space:]]*google_apis[[:space:]]*$/ { target = 1 }
    in_gate && /^[[:space:]]*arch:[[:space:]]*x86_64[[:space:]]*$/ { arch = 1 }
    in_gate && /^[[:space:]]*profile:[[:space:]]*pixel_7[[:space:]]*$/ { profile = 1 }
    in_gate && /^[[:space:]]*ram-size:[[:space:]]*4096M[[:space:]]*$/ { ram = 1 }
    in_gate && /^[[:space:]]*avd-name:[[:space:]]*saqz-api35-probe[[:space:]]*$/ { avd = 1 }
    in_gate && /^[[:space:]]*emulator-build:[[:space:]]*13823996[[:space:]]*$/ { build = 1 }
    END { exit api && target && arch && profile && ram && avd && build ? 0 : 1 }
' "$probe_workflow" || {
    printf 'api35 gate tuple must be pinned exactly\n' >&2
    exit 1
}
ok 'api35 tuplePinned'

awk '
    /^[[:space:]]*android-api35-gate:[[:space:]]*$/ { in_gate = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_gate = 0 }
    in_gate && /^[[:space:]]*emulator-boot-timeout:[[:space:]]*300[[:space:]]*$/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$probe_workflow" || {
    printf 'api35 gate must keep boot timeout at 300 seconds\n' >&2
    exit 1
}
ok 'api35 bootTimeoutIs300'

awk '
    /^[[:space:]]*android-api35-gate:[[:space:]]*$/ { in_gate = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_gate = 0 }
    in_gate && /sudo chmod 0666 \/dev\/kvm/ { chmod = 1 }
    in_gate && /test -r \/dev\/kvm/ { read = 1 }
    in_gate && /test -w \/dev\/kvm/ { write = 1 }
    END { exit chmod && read && write ? 0 : 1 }
' "$probe_workflow" || {
    printf 'api35 gate must enable and verify KVM access\n' >&2
    exit 1
}
ok 'api35 kvmEnabled'

awk '
    /^[[:space:]]*android-api35-gate:[[:space:]]*$/ { in_gate = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_gate = 0 }
    in_gate && /:android-app:connectedDevDebugAndroidTest/ { connected = 1 }
    in_gate && /android\.testInstrumentationRunnerArguments\.class=br\.com\.saqz\.androidapp\.ModernAndroidBehaviorTest/ { modern = 1 }
    in_gate && /scripts\/check-gradle$/ { full_gate = 1 }
    END { exit connected && modern && !full_gate ? 0 : 1 }
' "$probe_workflow" || {
    printf 'api35 gate must run only ModernAndroidBehaviorTest, not the full check-gradle\n' >&2
    exit 1
}
ok 'api35 exactModernClassRuns'

awk '
    /^[[:space:]]*android-api35-gate:[[:space:]]*$/ { in_gate = 1 }
    /^[[:space:]]*ios-gate:[[:space:]]*$/ { in_gate = 0 }
    in_gate && /continue-on-error/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$probe_workflow" >/dev/null 2>&1 && {
    printf 'api35 gate must be strict internally, without continue-on-error\n' >&2
    exit 1
}
ok 'api35 internalFailureIsFatal'

# --- Contratos transversais -------------------------------------------------

grep -Eq 'scripts/check-credentials' "$repository_root/scripts/check-gradle"
grep -Eq 'scripts/check-scope' "$repository_root/scripts/check-gradle"
grep -Eq 'scripts/check-bruno' "$repository_root/scripts/check-gradle"
ok 'static gates wired in check-gradle'

check_gradle="$repository_root/scripts/check-gradle"
isolation="$repository_root/tests/scripts/check-workspace-isolation.test.sh"

grep -Fq ':features:access:test' "$check_gradle"
ok 'access backend unit suite required'
grep -Fq ':features:access:integrationTest' "$check_gradle"
ok 'access PostgreSQL suite required'
grep -Fq ':bootstrap:emulatorTest' "$check_gradle"
ok 'access Firebase endpoint suite required'
grep -Fq ':core:network:allTests' "$check_gradle"
ok 'network KMP suite required'
grep -Fq ':features:access:compileAndroidMain' "$check_gradle"
grep -Fq ':features:access:allTests' "$check_gradle"
ok 'access Android and shared suites required'
grep -Fq ':features:access:test' "$isolation"
ok 'backend isolation includes access unit'
grep -Fq ':features:access:integrationTest' "$isolation"
ok 'backend isolation includes access PostgreSQL'
grep -Fq ':core:network:allTests' "$isolation"
ok 'mobile isolation includes network'
grep -Fq ':features:access:allTests' "$isolation"
ok 'mobile isolation includes access'

if grep -Eqi '\.specs|checklist|manual gate' "$workflow" "$evaluator"; then
    printf 'manual checklist must not be an input to CI gates\n' >&2
    exit 1
fi
ok 'manual checklist is not a gate input'

if grep -Eqi 'secret|GOOGLE_APPLICATION_CREDENTIALS|service-account|signing|database|deploy-pages|firebase deploy' "$workflow" "$full_ui_workflow"; then
    printf 'workflow requires forbidden secret/deployment contract\n' >&2
    exit 1
fi
ok 'no production secret contract'

if grep -Eq 'GOOGLE_APPLICATION_CREDENTIALS=|service-account|BRANCH_KEY|GOOGLE_CLIENT' "$workflow" "$full_ui_workflow" "$check_gradle"; then
    printf 'authenticated CI must remain credential-free\n' >&2
    exit 1
fi
ok 'authenticated CI credential-free'

git -C "$repository_root" diff --quiet -- "$pages_workflow" || {
    printf 'Pages workflow has unstaged changes\n' >&2
    exit 1
}
ok 'pages workflow preserved'

[ "$count" -eq 57 ]
