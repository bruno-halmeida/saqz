#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch=$(mktemp -d "${TMPDIR:-/tmp}/saqz-check-bruno.XXXXXX")
trap 'rm -rf "$scratch"' EXIT HUP INT TERM

make_fixture() {
    target=$1
    mkdir -p "$target/backend/demo/src/main/kotlin" "$target/bruno/environments"
    cp "$repository_root/scripts/check-bruno" "$target/check-bruno"
    printf '{}\n' >"$target/bruno/bruno.json"
    printf 'vars {\n  backendUrl: http://localhost:8080\n}\n' >"$target/bruno/environments/Dev.bru"
    printf '@GetMapping("/api/demo")\n' >"$target/backend/demo/src/main/kotlin/Demo.kt"
}

happy="$scratch/happy"
make_fixture "$happy"
printf 'get {\n  url: {{backendUrl}}/api/demo\n}\n\ntests {\n  test("ok", () => expect(res.status).to.equal(200));\n}\n' >"$happy/bruno/Demo.bru"
SAQZ_REPOSITORY_ROOT="$happy" "$happy/check-bruno" >/dev/null

missing="$scratch/missing"
make_fixture "$missing"
if SAQZ_REPOSITORY_ROOT="$missing" "$missing/check-bruno" >/dev/null 2>&1; then
    printf 'missing request unexpectedly passed\n' >&2
    exit 1
fi

wrong_method="$scratch/wrong-method"
make_fixture "$wrong_method"
printf 'post {\n  url: {{backendUrl}}/api/demo\n}\n\ntests {\n  test("ok", () => expect(res.status).to.equal(200));\n}\n' >"$wrong_method/bruno/Demo.bru"
if SAQZ_REPOSITORY_ROOT="$wrong_method" "$wrong_method/check-bruno" >/dev/null 2>&1; then
    printf 'wrong method unexpectedly passed\n' >&2
    exit 1
fi

printf 'ok - Bruno contract mutations\n'
