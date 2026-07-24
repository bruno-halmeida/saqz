#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
readme="$repository_root/README.md"
count=0

ok() {
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$1"
}

require() {
    pattern=$1
    label=$2
    grep -Eq "$pattern" "$readme" || {
        printf 'missing README contract: %s\n' "$label" >&2
        exit 1
    }
}

reject() {
    pattern=$1
    label=$2
    if grep -Eq "$pattern" "$readme"; then
        printf 'forbidden README reference: %s\n' "$label" >&2
        exit 1
    fi
}

require 'JDK 21' 'JDK prerequisite'
require 'DOCKER_HOST|Colima' 'Docker/Colima prerequisite'
ok 'prerequisites'

require 'scripts/check-all' 'aggregate gate'
require 'scripts/check-gradle' 'Gradle gate'
require 'scripts/check-ios' 'iOS gate'
ok 'native gate commands'

require 'linear\.app/vulkz/project/reset-da-apresentacao-mobile' 'Linear project link'
require 'linear\.app/vulkz/document/architecture-decisions-ads' 'Architecture Decisions document link'
ok 'Linear pointers'

require 'mobile/AGENTS\.md' 'mobile AGENTS.md pointer'
ok 'agent contract pointer'

reject '\.specs' 'retired .specs path'
reject '(^|[^/])AGENTS\.md' 'retired root AGENTS.md'
ok 'no references to removed paths'

[ "$count" -eq 5 ]
