#!/usr/bin/env bash
# A frota do roteiro multi-aparelho: 2 emuladores Android (Saqz_QA_A/B, Android 36 com Play Store, arm64)
# e 2 simuladores iOS (iPhone 17 e iPhone 17e). O mobile-mcp lista os quatro depois de "subir".
# Os emuladores sobem sempre em cold boot (-no-snapshot): tentar carregar o snapshot "default_boot"
# numa AVD recém-criada deixava o emulador travado em 7% de CPU, sem nunca aparecer no adb.
#
#   tests/multi-aparelhos/aparelhos.sh criar     # cria as duas AVDs (só na primeira vez)
#   tests/multi-aparelhos/aparelhos.sh subir     # sobe emulador-5554, emulator-5556 e os dois simuladores
#   tests/multi-aparelhos/aparelhos.sh instalar  # instala o APK e o .app gerados por builds.sh nos quatro
#   tests/multi-aparelhos/aparelhos.sh listar
#   tests/multi-aparelhos/aparelhos.sh parar
set -euo pipefail

HOME_DIR="${SAQZ_MULTIDEV_HOME:-$HOME/.saqz-multidev}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
IMAGE="system-images;android-36;google_apis_playstore;arm64-v8a"
AVDS=(Saqz_QA_A Saqz_QA_B); PORTS=(5554 5556)
SIMS=("iPhone 17" "iPhone 17e")
APK="$ROOT/mobile/android-app/build/outputs/apk/dev/debug/android-app-dev-debug.apk"
APP="$HOME_DIR/DerivedData/Build/Products/Debug-iphonesimulator/SaqzIOS.app"
ADB="$SDK/platform-tools/adb"
mkdir -p "$HOME_DIR/logs"

criar() {
  local avdmanager="$SDK/cmdline-tools/latest/bin/avdmanager"
  [ -x "$avdmanager" ] || { echo "falta $avdmanager (sdkmanager --install 'cmdline-tools;latest')" >&2; exit 1; }
  [ -d "$SDK/system-images/android-36/google_apis_playstore/arm64-v8a" ] || "$SDK/cmdline-tools/latest/bin/sdkmanager" --install "$IMAGE"
  local device=pixel_9; "$avdmanager" list device -c | grep -qx pixel_9 || device=pixel_7
  for avd in "${AVDS[@]}"; do
    [ -d "$HOME/.android/avd/$avd.avd" ] && { echo "$avd já existe"; continue; }
    echo no | "$avdmanager" create avd -n "$avd" -k "$IMAGE" -d "$device" >/dev/null
    cat >> "$HOME/.android/avd/$avd.avd/config.ini" <<INI
hw.ramSize=2048
hw.keyboard=yes
disk.dataPartition.size=4G
hw.gpu.enabled=yes
hw.gpu.mode=auto
INI
    echo "$avd criada ($device, Android 36 Play Store)"
  done
}

subir() {
  local i
  for i in 0 1; do
    local avd=${AVDS[$i]} port=${PORTS[$i]}
    if "$ADB" devices | grep -q "emulator-$port"; then echo "emulator-$port já está de pé"; continue; fi
    nohup "$SDK/emulator/emulator" -avd "$avd" -port "$port" -no-snapshot -no-boot-anim -no-audio -gpu auto -netdelay none -netspeed full \
      > "$HOME_DIR/logs/emulator-$avd.log" 2>&1 &
  done
  for sim in "${SIMS[@]}"; do xcrun simctl boot "$sim" 2>/dev/null || true; done
  for i in 0 1; do
    "$ADB" -s "emulator-${PORTS[$i]}" wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' 
    echo "emulator-${PORTS[$i]} (${AVDS[$i]}) pronto"
  done
  for sim in "${SIMS[@]}"; do xcrun simctl bootstatus "$sim" -b >/dev/null 2>&1; echo "$sim pronto"; done
  open -a Simulator >/dev/null 2>&1 || true
}

instalar() {
  [ -f "$APK" ] || { echo "falta o APK: rode builds.sh" >&2; exit 1; }
  [ -d "$APP" ] || { echo "falta o .app: rode builds.sh" >&2; exit 1; }
  for port in "${PORTS[@]}"; do "$ADB" -s "emulator-$port" install -r -g "$APK" >/dev/null && echo "app.saqz instalado em emulator-$port"; done
  for sim in "${SIMS[@]}"; do xcrun simctl install "$sim" "$APP" && echo "app.saqz instalado em $sim"; done
}

listar() { "$ADB" devices | tail -n +2 | sed '/^$/d'; xcrun simctl list devices booted | grep -E "iPhone|iPad" || true; }

parar() {
  for port in "${PORTS[@]}"; do "$ADB" -s "emulator-$port" emu kill >/dev/null 2>&1 || true; done
  for sim in "${SIMS[@]}"; do xcrun simctl shutdown "$sim" >/dev/null 2>&1 || true; done
  echo "frota parada"
}

case "${1:-}" in
  criar) criar ;; subir) subir ;; instalar) instalar ;; listar) listar ;; parar) parar ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
