#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-credentials-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM
count=0

make_repo() {
    target=$1
    mkdir -p "$target"
    (
        cd "$repository_root"
        git ls-files -z | xargs -0 tar -cf "$target/repo.tar"
    )
    (
        cd "$target"
        mkdir repo
        tar -xf repo.tar -C repo
        rm repo.tar
        mkdir -p repo/scripts
        cp "$repository_root/scripts/check-credentials" repo/scripts/check-credentials
        chmod +x repo/scripts/check-credentials
        cd repo
        git init -q
        git config user.email test@example.invalid
        git config user.name 'Credential Test'
        git add .
        git commit -qm baseline
    )
}

pass_case() {
    label=$1
    dir="$scratch_root/$label"
    make_repo "$dir"
    (cd "$dir/repo" && scripts/check-credentials) >/dev/null
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

fail_case() {
    label=$1
    path=$2
    content=$3
    expected=$4
    dir="$scratch_root/$label"
    make_repo "$dir"
    file="$dir/repo/$path"
    mkdir -p "$(dirname "$file")"
    printf '%s\n' "$content" >"$file"
    (
        cd "$dir/repo"
        git add -f "$path"
        git ls-files --error-unmatch "$path" >/dev/null
        git commit -qm "$label"
    )
    if (cd "$dir/repo" && scripts/check-credentials) >"$dir/stdout" 2>"$dir/stderr"; then
        cat "$dir/stdout" "$dir/stderr" >&2
        printf 'expected credential gate failure for %s\n' "$label" >&2
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
fail_case forbidden-android-config mobile/android-app/google-services.json '{}' 'forbidden Firebase platform file'
fail_case forbidden-apple-config mobile/ios-app/GoogleService-Info.plist '<plist />' 'forbidden Firebase platform file'
fail_case forbidden-env backend/.env 'DATABASE_URL=postgres://secret@example.invalid/app' 'forbidden non-example environment file'
fail_case private-key-literal docs/key.txt '-----BEGIN PRIVATE KEY-----' 'private-key literal'
fail_case service-account-literal docs/service-account.json '{"type":"service_account","private_key_id":"abc"}' 'service-account literal'
fail_case credential-classification docs/aws.txt 'AKIA1234567890ABCDEF' 'credential literal'
fail_case gitleaks-private-key-classification docs/gitleaks-key.txt '-----BEGIN RSA PRIVATE KEY-----' 'private-key literal'
fail_case bearer-token-classification docs/bearer.txt 'Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.example.signature' 'bearer-token literal'

dir="$scratch_root/specs-ignored"
make_repo "$dir"
(
    cd "$dir/repo"
    mkdir -p .specs
    printf '%s\n' 'Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.example.signature' >.specs/local.md
    if ! git check-ignore -q .specs/local.md; then
        printf '.specs/local.md is not ignored\n' >&2
        exit 1
    fi
    if git ls-files --error-unmatch .specs/local.md >/dev/null 2>&1; then
        printf '.specs/local.md is tracked\n' >&2
        exit 1
    fi
)
(cd "$dir/repo" && scripts/check-credentials) >/dev/null
count=$((count + 1))
printf 'ok %d - specs ignored and untracked\n' "$count"

[ "$count" -eq 10 ]
