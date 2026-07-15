#!/bin/sh

set -eu

workspace=${1:-}
if [ "$workspace" != "backend" ] && [ "$workspace" != "mobile" ]; then
    printf 'usage: %s backend|mobile\n' "$0" >&2
    exit 2
fi

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-workspace-isolation.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM

if [ "$workspace" = "mobile" ]; then
    cp -R "$repository_root/mobile" "$scratch_root/mobile"
    find "$scratch_root/mobile" -type d \( -name .gradle -o -name build \) -prune -exec rm -rf {} +
    rm -f \
        "$scratch_root/mobile/android-app/src/prod/google-services.json" \
        "$scratch_root/mobile/ios-app/SaqzIOS/Config/Prod/GoogleService-Info.plist"

    if [ -e "$scratch_root/backend" ]; then
        printf 'scratch workspace unexpectedly contains a sibling product workspace\n' >&2
        exit 1
    fi
    if [ -e "$scratch_root/landing-page" ] || [ -e "$scratch_root/frontend" ]; then
        printf 'scratch workspace unexpectedly contains a web workspace\n' >&2
        exit 1
    fi
    if [ -e "$scratch_root/mobile/ios-app/SaqzIOS/Config/Prod/GoogleService-Info.plist" ]; then
        printf 'scratch workspace unexpectedly contains production iOS credentials\n' >&2
        exit 1
    fi
    if [ -e "$scratch_root/mobile/android-app/src/prod/google-services.json" ]; then
        printf 'scratch workspace unexpectedly contains production Android credentials\n' >&2
        exit 1
    fi

    bin_dir="$scratch_root/bin"
    mkdir -p "$bin_dir"
    command_log="$scratch_root/mobile-command.log"
    : >"$command_log"
    export COMMAND_LOG="$command_log"
    export SCRATCH_MOBILE="$scratch_root/mobile"

    cat >"$scratch_root/mobile/gradlew" <<'SH'
#!/bin/sh
set -eu
printf 'gradle %s\n' "$*" >>"$COMMAND_LOG"
case " $* " in
    *" :core:common:allTests "*|*" :core:design-system:allTests "*|*" :compose-app:allTests "*|*" :android-app:testDevDebugUnitTest "*|*" :android-app:connectedDevDebugAndroidTest "*)
        printf 'BUILD SUCCESSFUL\nExecuted 5 tests\n'
        ;;
    *" help "*)
        if grep -q 'includeBuild("../backend")' "$SCRATCH_MOBILE/settings.gradle.kts"; then
            printf 'Mobile build must not include a sibling workspace or its build logic\n' >&2
            exit 1
        fi
        printf 'BUILD SUCCESSFUL\n'
        ;;
    *)
        printf 'unexpected isolated Gradle command: %s\n' "$*" >&2
        exit 2
        ;;
esac
SH

    cat >"$bin_dir/xcrun" <<'SH'
#!/bin/sh
set -eu
case "$*" in
    "--sdk iphonesimulator --show-sdk-version")
        printf '26.4\n'
        ;;
    "simctl list devices available -j")
        printf '%s\n' '{"devices":{"com.apple.CoreSimulator.SimRuntime.iOS-26-4":[{"isAvailable":true,"udid":"UDID-26-4"}]}}'
        ;;
    *)
        printf 'unexpected isolated xcrun command: %s\n' "$*" >&2
        exit 2
        ;;
esac
SH

    cat >"$bin_dir/xcodebuild" <<'SH'
#!/bin/sh
set -eu
printf 'xcodebuild %s\n' "$*" >>"$COMMAND_LOG"
case " $* " in
    *" test "*)
        printf 'Test Suite passed\nExecuted 5 tests, with 0 failures\n'
        ;;
    *" build "*)
        printf '** BUILD SUCCEEDED **\n'
        ;;
    *)
        printf 'unexpected isolated xcodebuild command: %s\n' "$*" >&2
        exit 2
        ;;
esac
SH

    chmod +x "$scratch_root/mobile/gradlew" "$bin_dir/xcrun" "$bin_dir/xcodebuild"
    export PATH="$bin_dir:$PATH"
    export JAVA_HOME="${SAQZ_JAVA_HOME:-/tmp/saqz-isolated-jdk21}"

    run_and_log() {
        name=$1
        shift
        log="$scratch_root/$name.log"
        if ! "$@" >"$log" 2>&1; then
            cat "$log" >&2
            printf 'mobile isolated step failed: %s\n' "$name" >&2
            exit 1
        fi
        if grep -F "$repository_root" "$log" "$command_log" >/dev/null 2>&1; then
            cat "$log" >&2
            printf 'mobile isolated step leaked original repository path: %s\n' "$name" >&2
            exit 1
        fi
    }

    run_xcode_tests() {
        name=$1
        shift
        run_and_log "$name" "$@"
        if ! grep -Eq 'Executed [1-9][0-9]* test' "$scratch_root/$name.log"; then
            cat "$scratch_root/$name.log" >&2
            printf 'mobile isolated xcode step ran zero tests: %s\n' "$name" >&2
            exit 1
        fi
    }

    gradle="$scratch_root/mobile/gradlew"
    mobile_dir="$scratch_root/mobile"
    project="$scratch_root/mobile/ios-app/SaqzIOS.xcodeproj"
    sdk_version=$(xcrun --sdk iphonesimulator --show-sdk-version)
    simulator_udid="$(xcrun simctl list devices available -j | ruby -rjson -e '
sdk_version = ARGV.fetch(0)
devices = JSON.parse(STDIN.read).fetch("devices")
runtime_suffix = ".iOS-#{sdk_version.tr(".", "-")}"
runtime, simulators = devices.find { |name, entries| name.end_with?(runtime_suffix) && entries.any? { |entry| entry["isAvailable"] } }
abort "No available iOS #{sdk_version} Simulator is installed. Install the runtime matching the active Xcode SDK." unless runtime
puts simulators.find { |entry| entry["isAvailable"] }.fetch("udid")
' "$sdk_version")"
    destination="id=$simulator_udid"

    run_and_log core-common "$gradle" -p "$mobile_dir" :core:common:allTests --console=plain
    run_and_log core-design-system "$gradle" -p "$mobile_dir" :core:design-system:allTests --console=plain
    run_and_log compose-app "$gradle" -p "$mobile_dir" :compose-app:allTests --console=plain
    run_and_log android-unit "$gradle" -p "$mobile_dir" :android-app:testDevDebugUnitTest --console=plain
    run_and_log android-instrumented "$gradle" -p "$mobile_dir" :android-app:connectedDevDebugAndroidTest --console=plain
    run_xcode_tests saqz-dev xcodebuild -project "$project" -scheme SaqzDev \
        -destination "$destination" JAVA_HOME="$JAVA_HOME" CODE_SIGNING_ALLOWED=NO test
    run_and_log saqz-prod-build xcodebuild -project "$project" -scheme SaqzProd \
        -configuration Release -destination "$destination" JAVA_HOME="$JAVA_HOME" \
        CODE_SIGNING_ALLOWED=NO build
    run_xcode_tests saqz-prod-unit xcodebuild -project "$project" -scheme SaqzProd \
        -configuration Release -destination "$destination" -only-testing:SaqzIOSTests \
        JAVA_HOME="$JAVA_HOME" CODE_SIGNING_ALLOWED=NO ENABLE_TESTABILITY=YES test

    expected_log="gradle -p $mobile_dir :core:common:allTests --console=plain
gradle -p $mobile_dir :core:design-system:allTests --console=plain
gradle -p $mobile_dir :compose-app:allTests --console=plain
gradle -p $mobile_dir :android-app:testDevDebugUnitTest --console=plain
gradle -p $mobile_dir :android-app:connectedDevDebugAndroidTest --console=plain
xcodebuild -project $project -scheme SaqzDev -destination $destination JAVA_HOME=$JAVA_HOME CODE_SIGNING_ALLOWED=NO test
xcodebuild -project $project -scheme SaqzProd -configuration Release -destination $destination JAVA_HOME=$JAVA_HOME CODE_SIGNING_ALLOWED=NO build
xcodebuild -project $project -scheme SaqzProd -configuration Release -destination $destination -only-testing:SaqzIOSTests JAVA_HOME=$JAVA_HOME CODE_SIGNING_ALLOWED=NO ENABLE_TESTABILITY=YES test"
    if [ "$(cat "$command_log")" != "$expected_log" ]; then
        printf 'mobile isolated command inventory/order drifted\nexpected:\n%s\nactual:\n%s\n' \
            "$expected_log" "$(cat "$command_log")" >&2
        exit 1
    fi

    if grep -Eq '(^|/)(backend|landing-page|frontend)(/|$)' "$command_log"; then
        printf 'mobile isolation command touched a sibling workspace\n' >&2
        exit 1
    fi

    settings="$scratch_root/mobile/settings.gradle.kts"
    printf '\nincludeBuild("../backend")\n' >>"$settings"
    mutation_log="$scratch_root/mobile-sibling-composite.log"
    if "$scratch_root/mobile/gradlew" -p "$scratch_root/mobile" help \
        --console=plain >"$mutation_log" 2>&1; then
        printf 'mobile settings accepted forbidden sibling composite\n' >&2
        exit 1
    fi
    if ! grep -q 'Mobile build must not include a sibling workspace or its build logic' "$mutation_log"; then
        cat "$mutation_log" >&2
        printf 'mobile settings failed before rejecting sibling composite\n' >&2
        exit 1
    fi

    printf 'ok - mobile workspace isolation\n'
    exit 0
fi

cp -R "$repository_root/backend" "$scratch_root/backend"
find "$scratch_root/backend" -type d \( -name .gradle -o -name build \) -prune -exec rm -rf {} +

if [ -e "$scratch_root/mobile" ]; then
    printf 'scratch workspace unexpectedly contains a sibling product workspace\n' >&2
    exit 1
fi

clean_log="$scratch_root/backend-clean.log"
if ! "$scratch_root/backend/gradlew" -p "$scratch_root/backend" \
    :shared-kernel:check \
    :features:identity:test \
    :bootstrap:test \
    :architecture-tests:test \
    --console=plain >"$clean_log" 2>&1; then
    cat "$clean_log" >&2
    exit 1
fi
cat "$clean_log"

if grep -Eqi 'Kotlin Gradle plugin.*(loaded multiple times|multiple subprojects)' "$clean_log"; then
    printf 'duplicate Kotlin plugin warning found in isolated backend build\n' >&2
    exit 1
fi

settings="$scratch_root/backend/settings.gradle.kts"
architecture_build="$scratch_root/backend/architecture-tests/build.gradle.kts"
cp "$settings" "$settings.baseline"
cp "$architecture_build" "$architecture_build.baseline"

restore_build_files() {
    cp "$settings.baseline" "$settings"
    cp "$architecture_build.baseline" "$architecture_build"
    rm -rf "$scratch_root/mobile"
}

expect_arch08_rejection() {
    label=$1
    result_dir="$scratch_root/backend/architecture-tests/build/test-results/test"
    mutation_log="$scratch_root/$label.log"
    rm -rf "$result_dir"

    if "$scratch_root/backend/gradlew" -p "$scratch_root/backend" \
        :architecture-tests:test \
        --tests '*ARCH-08*' \
        --rerun-tasks \
        --console=plain >"$mutation_log" 2>&1; then
        printf 'ARCH-08 accepted forbidden %s coupling\n' "$label" >&2
        exit 1
    fi

    result_file="$result_dir/TEST-br.com.saqz.architecture.BackendArchitectureTest.xml"
    if [ ! -f "$result_file" ] ||
        ! grep -q 'ARCH-08 separates backend and client build graphs' "$result_file" ||
        ! grep -q '<failure' "$result_file"; then
        cat "$mutation_log" >&2
        printf '%s failed before the ARCH-08 rule rejected it\n' "$label" >&2
        exit 1
    fi

    restore_build_files
}

mkdir -p "$scratch_root/mobile"
printf 'rootProject.name = "forbidden-mobile-composite"\n' >"$scratch_root/mobile/settings.gradle.kts"
printf '\nincludeBuild("../mobile")\n' >>"$settings"
expect_arch08_rejection sibling-composite

mkdir -p "$scratch_root/mobile/build-logic"
printf 'rootProject.name = "forbidden-shared-build-logic"\n' >"$scratch_root/mobile/build-logic/settings.gradle.kts"
printf '\nincludeBuild("../mobile/build-logic") { name = "mobile-build-logic" }\n' >>"$settings"
expect_arch08_rejection sibling-build-logic

mkdir -p "$scratch_root/mobile"
printf 'plugins { java }\n' >"$scratch_root/mobile/build.gradle.kts"
printf '\ninclude(":mobile")\nproject(":mobile").projectDir = file("../mobile")\n' >>"$settings"
printf '\ndependencies { testImplementation(project(":mobile")) }\n' >>"$architecture_build"
expect_arch08_rejection sibling-project

printf '\ndependencies { testImplementation(files("../../mobile/build/libs/mobile.jar")) }\n' >>"$architecture_build"
expect_arch08_rejection sibling-artifact

printf 'ok - backend workspace isolation\n'
