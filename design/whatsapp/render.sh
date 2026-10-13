#!/bin/sh
# Gera os JPGs das imagens de WhatsApp a partir do HTML desta pasta.
# Uso: design/whatsapp/render.sh   (precisa do Chrome headless do HyperFrames ou CHROME=<binário>)
set -eu
cd "$(dirname "$0")"
CHROME=${CHROME:-$(find "$HOME/.cache/hyperframes/chrome" -name chrome-headless-shell -type f | head -1)}
OUT=../../links-page/assets
for name in lembrete link; do
  "$CHROME" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --virtual-time-budget=3000 --allow-file-access-from-files --window-size=1200,630 \
    --screenshot="$PWD/$name.png" "file://$PWD/$name.html" 2>/dev/null
  sips -s format jpeg -s formatOptions 88 "$name.png" --out "$OUT/whatsapp-$name.jpg" >/dev/null
  rm "$name.png"
  echo "$OUT/whatsapp-$name.jpg"
done
