#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-scope-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM
count=0

make_repo() {
    target=$1
    git clone -q "$repository_root" "$target/repo"
    cp "$repository_root/scripts/check-scope" "$target/repo/scripts/check-scope"
    cp "$repository_root/.gitignore" "$target/repo/.gitignore"
    cp "$repository_root/AGENTS.md" "$target/repo/AGENTS.md"
    rm -rf "$target/repo/.specs"
    cp -R "$repository_root/.specs" "$target/repo/.specs"
    chmod +x "$target/repo/scripts/check-scope"
    (
        cd "$target/repo"
        git config user.email test@example.invalid
        git config user.name 'Scope Test'
        git add .gitignore AGENTS.md .specs scripts/check-scope
        # V1: setup works whether the cloned HEAD already contains governance or not.
        git diff --cached --quiet || git commit -qm governance-fixture
    )
}

pass_case() {
    label=$1
    dir="$scratch_root/$label"
    make_repo "$dir"
    (cd "$dir/repo" && scripts/check-scope) >/dev/null
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

pass_case_with() {
    label=$1
    dir="$scratch_root/$label"
    make_repo "$dir"
    shift
    (
        cd "$dir/repo"
        "$@"
        git add -A
    )
    if (cd "$dir/repo" && scripts/check-scope) >"$dir/stdout" 2>"$dir/stderr"; then
        count=$((count + 1))
        printf 'ok %d - %s\n' "$count" "$label"
    else
        cat "$dir/stdout" "$dir/stderr" >&2
        printf 'expected scope gate pass for %s\n' "$label" >&2
        exit 1
    fi
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
    if (cd "$dir/repo" && scripts/check-scope) >"$dir/stdout" 2>"$dir/stderr"; then
        cat "$dir/stdout" "$dir/stderr" >&2
        printf 'expected scope gate failure for %s\n' "$label" >&2
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

pass_case clean-repository

fail_case database-persistence 'database or persistence artifact' sh -c \
    "mkdir -p backend/features/identity/src/main/kotlin/br/com/saqz/identity/adapter/output/persistence && printf 'package br.com.saqz.identity.adapter.output.persistence\nclass UserRepository\n' >backend/features/identity/src/main/kotlin/br/com/saqz/identity/adapter/output/persistence/UserRepository.kt"

fail_case auth-product-ui 'auth or product UI flow' sh -c \
    "printf '\nprivate const val loginRoute = \"login\"\n' >>mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzApp.kt"

fail_case openapi-generation 'generated OpenAPI client' sh -c \
    "mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/openapi && printf 'package br.com.saqz.composeapp.openapi\nconst val generatedOpenApiClient = true\n' >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/openapi/Client.kt"

fail_case extra-ui-navigation 'client navigation dependency or route' sh -c \
    "printf '\nprivate const val forbiddenNavFramework = \"voyager\"\n' >>mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzApp.kt"

fail_case production-deployment 'production deployment workflow' sh -c \
    "printf '%s\n' 'name: Backend Deploy' 'on: workflow_dispatch' 'jobs:' '  deploy:' '    runs-on: ubuntu-latest' '    steps:' '      - run: deploy backend production' >.github/workflows/backend-deploy.yml"

fail_case cross-workspace-coupling 'cross-workspace Gradle coupling' sh -c \
    "printf '\nincludeBuild(\"../backend\")\n' >>mobile/settings.gradle.kts"

fail_case client-domain-application 'client domain or application source segment' sh -c \
    "mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/domain && printf 'package br.com.saqz.composeapp.domain\ninterface UserDomain\n' >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/domain/UserDomain.kt"

fail_case retired-frontend-workspace 'retired frontend workspace' sh -c \
    "mkdir -p frontend && printf 'retired workspace\n' >frontend/README.md"

fail_case specs-ignored '.specs is ignored' sh -c \
    "printf '\n.specs/\n' >>.gitignore"

fail_case agents-untracked 'AGENTS.md is not tracked' sh -c \
    "git rm -q AGENTS.md"

fail_case state-untracked '.specs/STATE.md is not tracked' sh -c \
    "git rm -q .specs/STATE.md"

fail_case feature-specs-untracked '.specs contains no tracked feature specifications' sh -c \
    "git rm -q .specs/features/*/spec.md"

# --- Fundação mobile aprovada: positivos ---

pass_case_with allow-core-common-module sh -c \
    "mkdir -p mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/state && printf 'package br.com.saqz.core.common.state\n\nsealed interface SaqzUiState\n' >mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/state/SaqzUiState.kt && printf 'plugins { id(\"saqz.kmp-compose-library\") }\n' >mobile/core/common/build.gradle.kts"

pass_case_with allow-design-system-and-navigation sh -c \
    "mkdir -p mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component && printf 'package br.com.saqz.designsystem.component\n\nfun SaqzButton() = Unit\n' >mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzButton.kt && printf '\nandroidx-navigation-compose = { module = \"org.jetbrains.androidx.navigation:navigation-compose\", version = \"2.9.2\" }\n' >>mobile/gradle/libs.versions.toml"

# --- Categorias proibidas: negativos ---

fail_case ais-legacy-identifier 'legacy Ais identifier' sh -c \
    "mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/legacy && printf 'package br.com.saqz.composeapp.legacy\n\nfun AisButton() = Unit\n' >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/legacy/AisButton.kt"

fail_case web-ui-target 'web ui target' sh -c \
    "printf '\nkotlin { wasmJs { browser() } }\n' >>mobile/compose-app/build.gradle.kts"

fail_case client-persistence 'database or persistence artifact' sh -c \
    "mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/storage && printf 'package br.com.saqz.composeapp.storage\n\nconst val driver = \"jdbc:sqlite\"\n' >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/storage/Db.kt"

fail_case client-logout-ui 'auth or product UI flow' sh -c \
    "printf '\nprivate const val logoutLabel = \"Sair\"\n' >>mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzApp.kt"

fail_case openapi-client-path 'generated OpenAPI client path' sh -c \
    "mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/generated && printf 'package br.com.saqz.composeapp.generated\n\nval placeholder = 1\n' >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/generated/Endpoint.kt"

fail_case backend-domain-import 'client build depends on backend workspace' sh -c \
    "mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/data && printf 'package br.com.saqz.composeapp.data\n\nimport br.com.saqz.identity.User\n' >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/data/BackendRef.kt"

fail_case client-usecase-segment 'client domain or application source segment' sh -c \
    "mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/usecase && printf 'package br.com.saqz.composeapp.usecase\n\nval placeholder = 1\n' >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/usecase/DoThing.kt"

fail_case extra-features-module 'extra client UI source path' sh -c \
    "mkdir -p mobile/features/games/src/commonMain/kotlin && printf 'package games\n\nval placeholder = 1\n' >mobile/features/games/src/commonMain/kotlin/Games.kt"

fail_case mobile-production-deploy 'production deployment workflow' sh -c \
    "printf '%s\n' 'name: Mobile Deploy' 'on: workflow_dispatch' 'jobs:' '  deploy:' '    runs-on: macos-latest' '    steps:' '      - run: deploy mobile production' >.github/workflows/mobile-deploy.yml"

fail_case second-backend-feature 'backend/features must contain only identity' sh -c \
    "mkdir -p backend/features/payments/src/main/kotlin && printf 'package payments\n\nval placeholder = 1\n' >backend/features/payments/src/main/kotlin/Payments.kt"

[ "$count" -eq 25 ]
