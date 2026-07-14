#!/bin/sh

set -eu

workspace=${1:-}
if [ "$workspace" != "backend" ]; then
    printf 'usage: %s backend\n' "$0" >&2
    exit 2
fi

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-workspace-isolation.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM

cp -R "$repository_root/backend" "$scratch_root/backend"
find "$scratch_root/backend" -type d \( -name .gradle -o -name build \) -prune -exec rm -rf {} +

if [ -e "$scratch_root/mobile" ] || [ -e "$scratch_root/frontend" ]; then
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
    rm -rf "$scratch_root/mobile" "$scratch_root/frontend"
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
