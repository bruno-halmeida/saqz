#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
scratch_root=$(mktemp -d "${TMPDIR:-/tmp}/saqz-brand-test.XXXXXX")
trap 'rm -rf "$scratch_root"' EXIT HUP INT TERM
count=0

SOURCE_SHA=0c732546309e7143f60203472c368a3cebbb3a53721f142898724023aa33a473
svg_rel=landing-page/assets/saqz-logo.svg
wordmark_rel=mobile/core/design-system/src/commonMain/composeResources/drawable/saqz_wordmark.xml
symbol_rel=mobile/core/design-system/src/commonMain/composeResources/drawable/saqz_symbol.xml
prov_rel=mobile/brand/PROVENANCE.md

fail() {
    printf 'not ok - %s\n' "$1" >&2
    exit 1
}

ok() {
    count=$((count + 1))
    printf 'ok %d - %s\n' "$count" "$1"
}

# Structural contract for the derived brand assets under a repo root. Returns
# non-zero on any hash, viewBox, pathData, color or missing-output divergence.
validate_assets() {
    root=$1
    svg="$root/$svg_rel"
    wordmark="$root/$wordmark_rel"
    symbol="$root/$symbol_rel"

    [ -f "$svg" ] || return 1
    [ -f "$wordmark" ] || return 1
    [ -f "$symbol" ] || return 1
    [ -f "$root/$prov_rel" ] || return 1

    [ "$(shasum -a 256 "$svg" | cut -d' ' -f1)" = "$SOURCE_SHA" ] || return 1

    grep -q 'android:viewportWidth="1200"' "$wordmark" || return 1
    grep -q 'android:viewportHeight="360"' "$wordmark" || return 1
    grep -q 'android:viewportWidth="360"' "$symbol" || return 1
    grep -q 'android:viewportHeight="360"' "$symbol" || return 1

    for id in blue white-details green-accent; do
        d=$(sed -n "s/.*<path id=\"$id\" d=\"\([^\"]*\)\".*/\1/p" "$svg")
        [ -n "$d" ] || return 1
        grep -Fq "$d" "$wordmark" || return 1
        grep -Fq "$d" "$symbol" || return 1
    done

    for color in "#0638DF" "#FFFFFF" "#C7F300"; do
        grep -q "$color" "$wordmark" || return 1
        grep -q "$color" "$symbol" || return 1
    done

    return 0
}

stage() {
    root=$1
    mkdir -p "$root/$(dirname "$svg_rel")" \
             "$root/$(dirname "$wordmark_rel")" \
             "$root/$(dirname "$prov_rel")"
    cp "$repository_root/$svg_rel" "$root/$svg_rel"
    cp "$repository_root/$wordmark_rel" "$root/$wordmark_rel"
    cp "$repository_root/$symbol_rel" "$root/$symbol_rel"
    cp "$repository_root/$prov_rel" "$root/$prov_rel"
}

# Each scenario proves the checker accepts the pristine assets and rejects one
# targeted mutation.
scenario() {
    label=$1
    mutate=$2
    dir="$scratch_root/$label"
    stage "$dir"
    validate_assets "$dir" || fail "$label: pristine assets rejected"
    "$mutate" "$dir"
    if validate_assets "$dir"; then
        fail "$label: mutation accepted"
    fi
    ok "$label"
}

mutate_source_hash() { printf 'x' >>"$1/$svg_rel"; }
mutate_wordmark_viewbox() {
    sed 's/android:viewportWidth="1200"/android:viewportWidth="1000"/' \
        "$1/$wordmark_rel" >"$1/$wordmark_rel.tmp" && mv "$1/$wordmark_rel.tmp" "$1/$wordmark_rel"
}
mutate_symbol_viewbox() {
    sed 's/android:viewportWidth="360"/android:viewportWidth="512"/' \
        "$1/$symbol_rel" >"$1/$symbol_rel.tmp" && mv "$1/$symbol_rel.tmp" "$1/$symbol_rel"
}
mutate_path_data() {
    sed 's/M 84.02 348.93/M 84.02 348.94/' \
        "$1/$wordmark_rel" >"$1/$wordmark_rel.tmp" && mv "$1/$wordmark_rel.tmp" "$1/$wordmark_rel"
}
mutate_color() {
    sed 's/#C7F300/#C7F301/' \
        "$1/$symbol_rel" >"$1/$symbol_rel.tmp" && mv "$1/$symbol_rel.tmp" "$1/$symbol_rel"
}
mutate_missing_output() { rm -f "$1/$wordmark_rel"; }

scenario sourceHashPinned mutate_source_hash
scenario wordmarkViewBox mutate_wordmark_viewbox
scenario symbolViewBox mutate_symbol_viewbox
scenario pathDataPreserved mutate_path_data
scenario colorsPreserved mutate_color
scenario missingOutputFails mutate_missing_output

printf '# %d brand asset scenarios\n' "$count"
