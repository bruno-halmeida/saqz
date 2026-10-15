#!/usr/bin/env bash
# Gera os dois builds de desenvolvimento apontando para o stack local (stack.sh):
# APK devDebug (API em http://10.0.2.2:18080) e SaqzIOS.app de simulador (API em http://127.0.0.1:18080).
# Os dois usam o Firebase Auth Emulator (projeto saqz-local), porque não há google-services.json nem
# GoogleService-Info.plist de dev no checkout. Demora alguns minutos na primeira vez.
# O simulador é só arm64: o Gradle não tem o alvo ios_x64, e o destino genérico pediria os dois.
# Assinatura ad-hoc (CODE_SIGN_IDENTITY=-), não CODE_SIGNING_ALLOWED=NO: sem assinatura o app perde os
# entitlements e o Keychain recusa gravar sessão e convite pendente ("Não foi possível entrar").
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOME_DIR="${SAQZ_MULTIDEV_HOME:-$HOME/.saqz-multidev}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
ANDROID_URL=http://10.0.2.2:18080
IOS_URL=http://127.0.0.1:18080

[ -f "$ROOT/mobile/android-app/src/dev/google-services.json" ] && { echo "src/dev/google-services.json presente: o devDebug deixaria de usar o Auth Emulator" >&2; exit 1; }

echo "== Android devDebug ($ANDROID_URL)"
"$ROOT/mobile/gradlew" -p "$ROOT/mobile" :android-app:assembleDevDebug "-Psaqz.api.devBaseUrl=$ANDROID_URL" --console=plain -q
ls -la "$ROOT/mobile/android-app/build/outputs/apk/dev/debug/android-app-dev-debug.apk"

echo "== iOS SaqzDev Debug, simulador ($IOS_URL)"
"$ROOT/mobile/gradlew" -p "$ROOT/mobile" :compose-app:generateIosApiConfig "-Psaqz.api.devBaseUrl=$IOS_URL" --console=plain -q
env "ORG_GRADLE_PROJECT_saqz.api.devBaseUrl=$IOS_URL" xcodebuild -project "$ROOT/mobile/ios-app/SaqzIOS.xcodeproj" -scheme SaqzDev \
  -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath "$HOME_DIR/DerivedData" \
  ARCHS=arm64 EXCLUDED_ARCHS=x86_64 CODE_SIGN_IDENTITY=- CODE_SIGNING_REQUIRED=NO build -quiet
APP="$HOME_DIR/DerivedData/Build/Products/Debug-iphonesimulator/SaqzIOS.app"
# Build incremental já gravou o endereço padrão no Info.plist: o app falava com o que estivesse em
# 127.0.0.1:8080 e mostrava "Não foi possível carregar sua conta" (15/10). Para aqui em vez de seguir.
api_url=$(/usr/libexec/PlistBuddy -c "Print :SaqzAPIBaseURL" "$APP/Info.plist")
echo "$api_url"
if [ "$api_url" != "$IOS_URL" ]; then
  echo "erro: o app aponta para $api_url, não para $IOS_URL; rode de novo com um build limpo" >&2
  exit 1
fi
echo "ok: $APP"
