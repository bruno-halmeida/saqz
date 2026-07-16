#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-check-all-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM
count=0

make_repo() {
    target=$1
    mkdir -p "$target/repo/scripts" "$target/repo/bin"
    cp "$repository_root/scripts/check-all" "$target/repo/scripts/check-all"
    chmod +x "$target/repo/scripts/check-all"
    : >"$target/repo/invocations.log"

    for script in check-gradle check-ios check-landing; do
        cat >"$target/repo/scripts/$script" <<'SH'
#!/bin/sh
set -eu
name=${0##*/}
printf '%s\n' "$name" >>"$LOG_FILE"
case "$name" in
    check-gradle) [ "${FAIL_GRADLE:-0}" = 0 ] || exit 11 ;;
    check-ios) [ "${FAIL_IOS:-0}" = 0 ] || exit 13 ;;
    check-landing) [ "${FAIL_LANDING:-0}" = 0 ] || exit 14 ;;
esac
if [ "$name" = "check-gradle" ] && [ "${LONG_RUNNING:-0}" = 1 ]; then
    python3 -m http.server "$SIGNAL_PORT" --bind 127.0.0.1 >/dev/null 2>&1 &
    server_pid=$!
    trap 'kill "$server_pid" 2>/dev/null || true; wait "$server_pid" 2>/dev/null || true; exit 143' TERM INT
    wait "$server_pid"
fi
SH
        chmod +x "$target/repo/scripts/$script"
    done

    cat >"$target/repo/bin/uname" <<'SH'
#!/bin/sh
set -eu
printf '%s\n' "${FAKE_UNAME:-Darwin}"
SH
    chmod +x "$target/repo/bin/uname"
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
    (
        cd "$dir/repo"
        LOG_FILE="$PWD/invocations.log" PATH="$PWD/bin:$PATH" scripts/check-all
    ) >/dev/null
    expected="$dir/expected.log"
    cat >"$expected" <<'EOF'
check-gradle
check-ios
check-landing
EOF
    diff -u "$expected" "$dir/repo/invocations.log"
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

fail_fast_case() {
    label=$1
    env_name=$2
    expected_log=$3
    dir="$scratch_root/$label"
    make_repo "$dir"
    if (
        cd "$dir/repo"
        LOG_FILE="$PWD/invocations.log" PATH="$PWD/bin:$PATH" env "$env_name=1" scripts/check-all
    ) >"$dir/stdout" 2>"$dir/stderr"; then
        cat "$dir/stdout" "$dir/stderr" >&2
        printf 'expected check-all failure for %s\n' "$label" >&2
        exit 1
    fi
    printf '%s\n' "$expected_log" >"$dir/expected.log"
    diff -u "$dir/expected.log" "$dir/repo/invocations.log"
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

unsupported_case() {
    dir="$scratch_root/unsupported-host"
    make_repo "$dir"
    if (
        cd "$dir/repo"
        LOG_FILE="$PWD/invocations.log" PATH="$PWD/bin:$PATH" FAKE_UNAME=Linux scripts/check-all
    ) >"$dir/stdout" 2>"$dir/stderr"; then
        cat "$dir/stdout" "$dir/stderr" >&2
        printf 'expected unsupported host failure\n' >&2
        exit 1
    fi
    grep -q 'requires macOS' "$dir/stderr"
    [ ! -s "$dir/repo/invocations.log" ]
    count=$((count + 1))
    printf 'ok %d - unsupported-host\n' "$count"
}

manual_checklist_case() {
    dir="$scratch_root/manual-checklist-not-required"
    make_repo "$dir"
    mkdir -p "$dir/repo/.specs"
    printf 'status: pending\n' >"$dir/repo/.specs/manual-checklist.md"
    (
        cd "$dir/repo"
        LOG_FILE="$PWD/invocations.log" PATH="$PWD/bin:$PATH" scripts/check-all
    ) >/dev/null
    expected="$dir/expected.log"
    cat >"$expected" <<'EOF'
check-gradle
check-ios
check-landing
EOF
    diff -u "$expected" "$dir/repo/invocations.log"
    count=$((count + 1))
    printf 'ok %d - manual-checklist-not-required\n' "$count"
}

signal_case() {
    signal=$1
    label=$2
    dir="$scratch_root/$label"
    make_repo "$dir"
    port=$(free_port)
    if ! python3 - "$dir/repo" "$port" "$signal" >"$dir/stdout" 2>"$dir/stderr" <<'PY'
import os
import signal
import socket
import subprocess
import sys
import time
import urllib.request

repo, port, signal_name = sys.argv[1], int(sys.argv[2]), sys.argv[3]
env = os.environ.copy()
env.update({
    "LOG_FILE": os.path.join(repo, "invocations.log"),
    "PATH": os.path.join(repo, "bin") + os.pathsep + env["PATH"],
    "LONG_RUNNING": "1",
    "SIGNAL_PORT": str(port),
})

def prepare_child():
    os.setsid()
    signal.signal(signal.SIGINT, signal.SIG_DFL)
    signal.signal(signal.SIGTERM, signal.SIG_DFL)

process = subprocess.Popen(
    ["scripts/check-all"],
    cwd=repo,
    env=env,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    preexec_fn=prepare_child,
)

ready = False
for _ in range(50):
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port}", timeout=0.2):
            ready = True
            break
    except Exception:
        if process.poll() is not None:
            break
        time.sleep(0.1)

if not ready:
    out, err = process.communicate(timeout=1) if process.poll() is not None else ("", "")
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
    sys.stderr.write(out)
    sys.stderr.write(err)
    raise SystemExit("signal test server did not become ready")

# V1: signal the aggregate owner; child cleanup must be caused by its trap.
# The process group is reserved for emergency cleanup after a timeout.
os.kill(process.pid, getattr(signal, "SIG" + signal_name))
try:
    out, err = process.communicate(timeout=5)
except subprocess.TimeoutExpired:
    os.killpg(process.pid, signal.SIGTERM)
    out, err = process.communicate(timeout=2)
    sys.stderr.write(out)
    sys.stderr.write(err)
    raise SystemExit(f"check-all did not exit after {signal_name}")

sys.stdout.write(out)
sys.stderr.write(err)
if process.returncode == 0:
    raise SystemExit(f"expected signal failure for {signal_name}")
PY
    then
        cat "$dir/stdout" "$dir/stderr" "$dir/repo/invocations.log" >&2 2>/dev/null || true
        exit 1
    fi
    assert_port_bindable "$port"
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$label"
}

pass_case all-success
fail_fast_case gradle-failure FAIL_GRADLE 'check-gradle'
fail_fast_case ios-failure FAIL_IOS 'check-gradle
check-ios'
fail_fast_case landing-failure FAIL_LANDING 'check-gradle
check-ios
check-landing'
unsupported_case
signal_case INT sigint-cleanup
signal_case TERM sigterm-cleanup
manual_checklist_case

[ "$count" -eq 8 ]
