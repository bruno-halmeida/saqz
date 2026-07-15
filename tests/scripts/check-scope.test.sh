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
    "printf '\nexport const loginRoute = \"login\";\n' >>frontend/src/app/app.ts"

fail_case openapi-generation 'generated OpenAPI client' sh -c \
    "mkdir -p frontend/src/app/openapi && printf 'export const generatedOpenApiClient = true;\n' >frontend/src/app/openapi/client.ts"

fail_case extra-ui-navigation 'client navigation dependency or route' sh -c \
    "printf '\nimport { provideRouter } from \"@angular/router\";\n' >>frontend/src/app/app.config.ts"

fail_case production-deployment 'production deployment workflow' sh -c \
    "printf '%s\n' 'name: Backend Deploy' 'on: workflow_dispatch' 'jobs:' '  deploy:' '    runs-on: ubuntu-latest' '    steps:' '      - run: deploy backend production' >.github/workflows/backend-deploy.yml"

fail_case cross-workspace-coupling 'cross-workspace Gradle coupling' sh -c \
    "printf '\nincludeBuild(\"../backend\")\n' >>mobile/settings.gradle.kts"

fail_case client-domain-application 'client domain or application source segment' sh -c \
    "mkdir -p frontend/src/app/domain && printf 'export interface UserDomain {}\n' >frontend/src/app/domain/user.ts"

fail_case specs-tracked '.specs contains tracked files' sh -c \
    "mkdir -p .specs/features/project-initialization && printf 'local spec\n' >.specs/features/project-initialization/spec.md && git add -f .specs/features/project-initialization/spec.md && git commit -qm specs-tracked"

[ "$count" -eq 9 ]
