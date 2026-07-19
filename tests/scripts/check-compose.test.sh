#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
compose="$repository_root/compose.yaml"

require() {
    pattern=$1
    label=$2
    grep -Eq "$pattern" "$compose" || {
        printf 'missing Compose contract: %s\n' "$label" >&2
        exit 1
    }
}

require '^  database:$' 'PostgreSQL service'
require 'image: postgres:16-alpine' 'PostgreSQL 16 image'
require 'SPRING_DATASOURCE_URL: jdbc:postgresql://database:5432/saqz' 'backend JDBC URL'
require 'SPRING_DATASOURCE_USERNAME: saqz' 'backend database user'
require 'SPRING_DATASOURCE_PASSWORD: saqz-local-dev-only' 'backend local-only database password'
require 'SAQZ_BRANCH_DOMAIN: https://saqz.test-app.link' 'backend local Branch domain'
require 'condition: service_healthy' 'database readiness dependency'
require 'pg_isready -U saqz -d saqz' 'database health check'

printf 'ok - local Compose database wiring\n'
