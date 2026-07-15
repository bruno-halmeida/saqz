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
    chmod +x "$target/repo/scripts/check-scope"
    (
        cd "$target/repo"
        git config user.email test@example.invalid
        git config user.name 'Scope Test'
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
    "printf '\nprivate const val forbiddenNavHost = \"NavHost\"\n' >>mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzApp.kt"

fail_case production-deployment 'production deployment workflow' sh -c \
    "printf '%s\n' 'name: Backend Deploy' 'on: workflow_dispatch' 'jobs:' '  deploy:' '    runs-on: ubuntu-latest' '    steps:' '      - run: deploy backend production' >.github/workflows/backend-deploy.yml"

fail_case cross-workspace-coupling 'cross-workspace Gradle coupling' sh -c \
    "printf '\nincludeBuild(\"../backend\")\n' >>mobile/settings.gradle.kts"

fail_case client-domain-application 'client domain or application source segment' sh -c \
    "mkdir -p mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/domain && printf 'package br.com.saqz.composeapp.domain\ninterface UserDomain\n' >mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/domain/UserDomain.kt"

fail_case retired-frontend-workspace 'retired frontend workspace' sh -c \
    "mkdir -p frontend && printf 'retired workspace\n' >frontend/README.md"

fail_case specs-tracked '.specs contains tracked files' sh -c \
    "mkdir -p .specs/features/project-initialization && printf 'local spec\n' >.specs/features/project-initialization/spec.md && git add -f .specs/features/project-initialization/spec.md && git commit -qm specs-tracked"

[ "$count" -eq 10 ]
