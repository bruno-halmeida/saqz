#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-mobile-nav-deps-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM
count=0

make_repo() {
    target=$1
    git clone -q "$repository_root" "$target/repo"
    rm -rf "$target/repo/mobile"
    rsync -a --exclude='build' --exclude='.gradle' --exclude='.kotlin' "$repository_root/mobile/" "$target/repo/mobile/"
    cp "$repository_root/scripts/check-mobile-navigation-dependencies" "$target/repo/scripts/check-mobile-navigation-dependencies"
    chmod +x "$target/repo/scripts/check-mobile-navigation-dependencies"
    (
        cd "$target/repo"
        git config user.email test@example.invalid
        git config user.name 'Mobile Navigation Dependencies Test'
        git add -A
        git diff --cached --quiet || git commit -qm nav-deps-fixture
    )
}

pass_case() {
    label=$1
    dir="$scratch_root/$label"
    make_repo "$dir"
    shift 1
    (cd "$dir/repo" && scripts/check-mobile-navigation-dependencies "$@") >/dev/null
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

fail_case() {
    label=$1
    expected=$2
    dir="$scratch_root/$label"
    make_repo "$dir"
    shift 2
    setup="$1"
    shift 1
    (
        cd "$dir/repo"
        eval "$setup"
        git add -A
    )
    if (cd "$dir/repo" && scripts/check-mobile-navigation-dependencies "$@") >"$dir/stdout" 2>"$dir/stderr"; then
        cat "$dir/stdout" "$dir/stderr" >&2
        printf 'expected mobile navigation dependency gate failure for %s\n' "$label" >&2
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

pass_case clean-repository-no-flag

fail_case feature-depends-on-navigation-module \
    'a feature module depends on :navigation' \
    'sed -i.bak "s#implementation(project(\":core:domain\"))#implementation(project(\":core:domain\"))\n            implementation(project(\":navigation\"))#" mobile/features/groups/build.gradle.kts && rm -f mobile/features/groups/build.gradle.kts.bak'

fail_case feature-imports-navigation-package \
    'a feature module imports the :navigation package' \
    'mkdir -p mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/leak && printf "package br.com.saqz.access.leak\n\nimport br.com.saqz.navigation.ProductNavigationHost\n" >mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/leak/NavLeak.kt'

fail_case feature-declares-navigation3-ui-dependency \
    'a feature module declares a direct navigation3-ui Gradle dependency' \
    'sed -i.bak "s#implementation(project(\":core:domain\"))#implementation(project(\":core:domain\"))\n            implementation(\"org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1\")#" mobile/features/access/build.gradle.kts && rm -f mobile/features/access/build.gradle.kts.bak'

fail_case feature-imports-navigation3-ui \
    'a feature module imports Navigation Compose 3 UI' \
    'mkdir -p mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/leak && printf "package br.com.saqz.groups.leak\n\nimport androidx.navigation3.ui.NavDisplay\n" >mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/leak/NavDisplayLeak.kt'

fail_case navigation-module-declares-own-framework \
    ':navigation declares its own iOS framework/export block' \
    'printf "\nkotlin {\n    listOf(iosArm64()).forEach { it.binaries.framework { baseName = \"nav\" } }\n}\n" >>mobile/navigation/build.gradle.kts'

fail_case compose-app-exports-navigation-types \
    ':navigation types are exported through the SaqzMobile framework API' \
    'printf "\n// export(project(\":navigation\"))\n" >mobile/compose-app/build.gradle.kts && sed -i.bak "s#// export(project(\":navigation\"))#export(project(\":navigation\"))#" mobile/compose-app/build.gradle.kts && rm -f mobile/compose-app/build.gradle.kts.bak'

fail_case require-no-legacy-rejects-nav2-restored \
    'legacy navigation-compose:2.9.2 dependency is still present' \
    'printf "\nnavigation-compose = { module = \"org.jetbrains.androidx.navigation:navigation-compose\", version = \"2.9.2\" }\n" >>mobile/gradle/libs.versions.toml' \
    --require-no-legacy

fail_case removed-access-page-reintroduced \
    'a removed manual Access navigation artifact was reintroduced (T24)' \
    'mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/leak && printf "package br.com.saqz.composeapp.leak\n\nenum class AccessPage { CONTEXT }\n" >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/leak/AccessPageLeak.kt'

fail_case removed-access-destination-stack-reintroduced \
    'a removed manual Access navigation artifact was reintroduced (T24)' \
    'mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/leak && printf "package br.com.saqz.composeapp.leak\n\nclass AccessDestinationStack\n" >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/leak/StackLeak.kt'

fail_case removed-groups-navigation-viewmodel-reintroduced \
    'a removed legacy Groups navigation artifact was reintroduced (T25)' \
    'mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/leak && printf "package br.com.saqz.composeapp.leak\n\nclass GroupsNavigationViewModel\n" >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/leak/GroupsVmLeak.kt'

fail_case removed-groups-destination-content-reintroduced \
    'a removed legacy Groups navigation artifact was reintroduced (T25)' \
    'mkdir -p mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/leak && printf "package br.com.saqz.groups.leak\n\nfun GroupsDestinationContent() = Unit\n" >mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/leak/ContentLeak.kt'

# Positive case for --require-no-legacy: remove every legacy navigation-compose
# reference from the catalog/build files, then the flagged run must pass.
dir="$scratch_root/require-no-legacy-passes-once-nav2-removed"
make_repo "$dir"
(
    cd "$dir/repo"
    grep -rl 'navigation-compose' mobile --include='*.toml' --include='*.gradle.kts' 2>/dev/null | while IFS= read -r file; do
        sed -i.bak '/navigation-compose/d' "$file" && rm -f "$file.bak"
    done
    git add -A
)
(cd "$dir/repo" && ./scripts/check-mobile-navigation-dependencies --require-no-legacy) >/dev/null
count=$((count + 1))
printf 'ok %d - require-no-legacy-passes-once-nav2-removed\n' "$count"

[ "$count" -eq 9 ]
