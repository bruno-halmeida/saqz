#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-mobile-boundaries-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM
count=0

make_repo() {
    target=$1
    git clone -q "$repository_root" "$target/repo"
    git -C "$repository_root" diff --binary HEAD -- mobile >"$target/mobile.patch"
    if [ -s "$target/mobile.patch" ]; then
        git -C "$target/repo" apply <"$target/mobile.patch"
    fi
    cp "$repository_root/scripts/check-mobile-boundaries" "$target/repo/scripts/check-mobile-boundaries"
    chmod +x "$target/repo/scripts/check-mobile-boundaries"
    (
        cd "$target/repo"
        git config user.email test@example.invalid
        git config user.name 'Mobile Boundaries Test'
        git add -A
        git diff --cached --quiet || git commit -qm boundary-fixture
    )
}

pass_case() {
    label=$1
    dir="$scratch_root/$label"
    make_repo "$dir"
    (cd "$dir/repo" && scripts/check-mobile-boundaries) >/dev/null
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

fail_case() {
    label=$1
    expected=$2
    dir="$scratch_root/$label"
    make_repo "$dir"
    shift 2
    (
        cd "$dir/repo"
        "$@"
        git add -A
    )
    if (cd "$dir/repo" && scripts/check-mobile-boundaries) >"$dir/stdout" 2>"$dir/stderr"; then
        cat "$dir/stdout" "$dir/stderr" >&2
        printf 'expected mobile boundary gate failure for %s\n' "$label" >&2
        exit 1
    fi
    grep -q "$expected" "$dir/stderr" || {
        cat "$dir/stderr" >&2
        printf 'missing expected failure marker %s for %s\n' "$expected" "$label" >&2
        exit 1
    }
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

# inject_dependency_case LABEL EXPECTED FILE DEPENDENCY_LINE - clone the repo, insert
# DEPENDENCY_LINE right after the module's existing `:core:domain` dependency inside
# FILE's build.gradle.kts, then assert the gate rejects it with the EXPECTED marker.
inject_dependency_case() {
    label=$1
    expected=$2
    file=$3
    line=$4
    fail_case "$label" "$expected" sh -c \
        "sed -i.bak 's#implementation(project(\":core:domain\"))#implementation(project(\":core:domain\"))\\n            $line#' '$file' && rm -f '$file.bak'"
}

pass_case clean-repository

inject_dependency_case presentation-to-data-edge \
    'presentation depends on its own data module' \
    mobile/features/groups/build.gradle.kts \
    'implementation(project(\":features:groups:data\"))'

inject_dependency_case presentation-to-network-edge \
    'presentation depends on core:network' \
    mobile/features/access/build.gradle.kts \
    'implementation(project(\":core:network\"))'

inject_dependency_case domain-to-compose-edge \
    'depends on more than :core:domain' \
    mobile/features/groups/domain/build.gradle.kts \
    'implementation(\"org.jetbrains.compose.runtime:runtime:1.11.1\")'

inject_dependency_case domain-to-koin-edge \
    'depends on more than :core:domain' \
    mobile/features/access/domain/build.gradle.kts \
    'implementation(libs.koin.core)'

inject_dependency_case domain-to-ktor-edge \
    'depends on more than :core:domain' \
    mobile/features/groups/domain/build.gradle.kts \
    'implementation(libs.ktor.client.core)'

inject_dependency_case domain-to-serialization-edge \
    'depends on more than :core:domain' \
    mobile/features/access/domain/build.gradle.kts \
    'implementation(libs.kotlinx.serialization.json)'

inject_dependency_case data-to-presentation-edge \
    'data module depends on its own presentation module' \
    mobile/features/groups/data/build.gradle.kts \
    'implementation(project(\":features:groups\"))'

inject_dependency_case access-to-groups-edge \
    'presentation depends on the other feature' \
    mobile/features/access/build.gradle.kts \
    'implementation(project(\":features:groups\"))'

inject_dependency_case groups-to-access-edge \
    'presentation depends on the other feature' \
    mobile/features/groups/build.gradle.kts \
    'implementation(project(\":features:access\"))'

fail_case forbidden-source-import 'access presentation imports its own data layer' sh -c \
    "mkdir -p mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/leak && printf 'package br.com.saqz.access.leak\n\nimport br.com.saqz.access.data.session.SessionDto\n' >mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/leak/Leak.kt"

fail_case compatibility-adapter-path 'compatibility adapter path found' sh -c \
    "mkdir -p mobile/features/access/data/src/commonMain/kotlin/br/com/saqz/access/data/legacy && printf 'package br.com.saqz.access.data.legacy\n\nclass SessionCompatAdapter\n' >mobile/features/access/data/src/commonMain/kotlin/br/com/saqz/access/data/legacy/SessionCompatAdapter.kt"

# The @Serializable annotation is allowed in presentation (Nav3 route keys, saved()
# snapshots) but the transport engine stays forbidden in production sources.
fail_case presentation-json-engine-import 'access presentation imports the serialization transport engine' sh -c \
    "mkdir -p mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/leak && printf 'package br.com.saqz.access.leak\n\nimport kotlinx.serialization.json.Json\n' >mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/leak/JsonLeak.kt"

[ "$count" -eq 13 ]
