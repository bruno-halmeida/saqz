#!/usr/bin/env bash
#
# Lê ou grava a versão (X.Y.Z) e o build (inteiro) do app nos dois projetos:
# android-app/build.gradle.kts e as seis configurações do pbxproj do iOS.
#
#   ci/version.sh show           imprime "version=0.0.7 build=9"; falha se Android e iOS divergirem
#   ci/version.sh set 0.0.8 12   reescreve os dois projetos e confere o resultado
#
# O CI (mobile-rc.yml) chama "set" antes de commitar o bump; localmente serve para
# conferir o que está no código. Funciona no macOS e no Ubuntu (perl e grep).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE="$ROOT/android-app/build.gradle.kts"
PBX="$ROOT/ios-app/SaqzIOS.xcodeproj/project.pbxproj"
PBX_CONFIGS=6  # Debug/Release/ProdDebug × app e Live Activity

die() { printf 'version.sh: %s\n' "$1" >&2; exit 1; }

# unique_values REGEX FILE: os valores distintos capturados pelo grupo 1 de REGEX.
unique_values() { perl -ne "print \"\$1\\n\" if /$1/" "$2" | sort -u; }

show() {
  local code name pbx_code pbx_name n_code n_name
  code=$(unique_values '^\s*versionCode = environmentProperty\(.*fallback = "(\d+)"\)' "$GRADLE")
  name=$(unique_values '^\s*versionName = environmentProperty\(.*fallback = "(\d+\.\d+\.\d+)"\)' "$GRADLE")
  [[ $(printf '%s\n' "$code" | grep -c .) -eq 1 ]] || die "não achei exatamente um versionCode em $GRADLE"
  [[ $(printf '%s\n' "$name" | grep -c .) -eq 1 ]] || die "não achei exatamente um versionName em $GRADLE"

  pbx_code=$(unique_values '^\s*CURRENT_PROJECT_VERSION = (\d+);' "$PBX")
  pbx_name=$(unique_values '^\s*MARKETING_VERSION = (\d+\.\d+\.\d+);' "$PBX")
  n_code=$(grep -cE '^\s*CURRENT_PROJECT_VERSION = [0-9]+;' "$PBX")
  n_name=$(grep -cE '^\s*MARKETING_VERSION = [0-9]+\.[0-9]+\.[0-9]+;' "$PBX")
  [[ "$n_code" -eq "$PBX_CONFIGS" && "$n_name" -eq "$PBX_CONFIGS" ]] \
    || die "esperava $PBX_CONFIGS CURRENT_PROJECT_VERSION e $PBX_CONFIGS MARKETING_VERSION no pbxproj; achei $n_code e $n_name"
  [[ "$pbx_code" == "$code" ]] || die "build diverge: Android $code, iOS ${pbx_code//$'\n'/,}"
  [[ "$pbx_name" == "$name" ]] || die "versão diverge: Android $name, iOS ${pbx_name//$'\n'/,}"

  printf 'version=%s build=%s\n' "$name" "$code"
}

set_version() {
  local name="${1:-}" code="${2:-}"
  [[ "$name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "versão deve ser X.Y.Z (recebi '$name')"
  [[ "$code" =~ ^[0-9]+$ ]] || die "build deve ser inteiro (recebi '$code')"

  perl -pi -e "s/^(\s*versionCode = environmentProperty\(.*fallback = \")\d+(\"\))/\${1}$code\${2}/;
               s/^(\s*versionName = environmentProperty\(.*fallback = \")\d+\.\d+\.\d+(\"\))/\${1}$name\${2}/" "$GRADLE"
  perl -pi -e "s/^(\s*CURRENT_PROJECT_VERSION = )\d+;/\${1}$code;/;
               s/^(\s*MARKETING_VERSION = )\d+\.\d+\.\d+;/\${1}$name;/" "$PBX"

  local result
  result=$(show)
  [[ "$result" == "version=$name build=$code" ]] || die "gravei mas a leitura devolveu '$result'"
  printf '%s\n' "$result"
}

case "${1:-}" in
  show) show ;;
  set) set_version "${2:-}" "${3:-}" ;;
  *) sed -n '2,10p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac
