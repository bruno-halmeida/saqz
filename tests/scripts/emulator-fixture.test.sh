#!/bin/bash

set -u

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
fixture="$repository_root/firebase/session-fixture"
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-emulator-fixture.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT
count=0

assert_cleanup() {
    state=$1
    [ -f "$state/account-deleted" ] || return 1
    [ -f "$state/port-bindable" ] || return 1
    [ ! -f "$state/id-token" ] || return 1
    while IFS= read -r pid; do
        ! kill -0 "$pid" 2>/dev/null || return 1
    done <"$state/pids"
    python3 -c 'import socket; s=socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); s.bind(("127.0.0.1", 9099)); s.close()'
}

run_exit_case() {
    label=$1
    expected=$2
    state="$scratch_root/$label"
    mkdir -p "$state"
    set +e
    SAQZ_FIXTURE_STATE_DIR="$state" SAQZ_FIXTURE_FAIL="$expected" "$fixture" >"$state/test.log" 2>&1
    status=$?
    set -e
    if [ "$expected" = false ]; then
        [ "$status" -eq 0 ]
    else
        [ "$status" -ne 0 ]
    fi
    assert_cleanup "$state"
    count=$((count + 1))
    printf 'ok %d - %s cleanup\n' "$count" "$label"
}

run_signal_case() {
    label=$1
    signal=$2
    state="$scratch_root/$label"
    mkdir -p "$state"
    SAQZ_FIXTURE_STATE_DIR="$state" SAQZ_FIXTURE_HOLD=true \
        python3 -c 'import os, signal, sys; signal.signal(signal.SIGINT, signal.SIG_DFL); os.setsid(); os.execv(sys.argv[1], sys.argv[1:])' "$fixture" \
        >"$state/test.log" 2>&1 &
    fixture_pid=$!
    deadline=$((SECONDS + 60))
    while [ ! -f "$state/ready" ] && kill -0 "$fixture_pid" 2>/dev/null; do
        [ "$SECONDS" -lt "$deadline" ] || break
        sleep 1
    done
    [ -f "$state/ready" ]
    kill -s "$signal" "$fixture_pid"
    set +e
    wait "$fixture_pid"
    status=$?
    set -e
    [ "$status" -ne 0 ]
    assert_cleanup "$state"
    count=$((count + 1))
    printf 'ok %d - %s cleanup\n' "$count" "$label"
}

set -e
run_exit_case normal false
run_exit_case failure true
run_signal_case SIGINT INT
run_signal_case SIGTERM TERM

[ "$count" -eq 4 ]
