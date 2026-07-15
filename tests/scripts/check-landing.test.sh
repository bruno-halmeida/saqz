#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-landing-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM
count=0

make_repo() {
    target=$1
    git clone -q "$repository_root" "$target/repo"
    cp "$repository_root/scripts/check-landing" "$target/repo/scripts/check-landing"
    chmod +x "$target/repo/scripts/check-landing"
}

free_port() {
    python3 - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

assert_port_bindable() {
    port=$1
    python3 - "$port" <<'PY'
import socket
import sys
import time

last_error = None
for _ in range(20):
    try:
        with socket.socket() as sock:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(("127.0.0.1", int(sys.argv[1])))
            break
    except OSError as error:
        last_error = error
        time.sleep(0.1)
else:
    raise last_error
PY
}

pass_case() {
    label=$1
    dir="$scratch_root/$label"
    make_repo "$dir"
    port=$(free_port)
    (cd "$dir/repo" && SAQZ_LANDING_PORT=$port scripts/check-landing) >/dev/null
    assert_port_bindable "$port"
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

fail_case() {
    label=$1
    expected=$2
    dir="$scratch_root/$label"
    make_repo "$dir"
    port=$(free_port)
    shift 2
    (
        cd "$dir/repo"
        "$@"
    )
    if (cd "$dir/repo" && SAQZ_LANDING_PORT=$port scripts/check-landing) >"$dir/stdout" 2>"$dir/stderr"; then
        cat "$dir/stdout" "$dir/stderr" >&2
        printf 'expected landing gate failure for %s\n' "$label" >&2
        exit 1
    fi
    grep -q "$expected" "$dir/stderr" || {
        cat "$dir/stderr" >&2
        printf 'missing expected failure marker %s for %s\n' "$expected" "$label" >&2
        exit 1
    }
    assert_port_bindable "$port"
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

pass_case clean-repository

fail_case landing-content-drift 'landing-page differs from baseline' sh -c \
    "printf '\n<!-- drift -->\n' >>landing-page/index.html"

fail_case workflow-contract-drift 'Pages workflow missing manual dispatch trigger' sh -c \
    "grep -v 'workflow_dispatch' .github/workflows/deploy-pages.yml >.github/workflows/deploy-pages.yml.tmp && mv .github/workflows/deploy-pages.yml.tmp .github/workflows/deploy-pages.yml"

[ "$count" -eq 3 ]
