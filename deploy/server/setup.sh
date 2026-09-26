#!/usr/bin/env bash
# Etapas explícitas: nenhum deploy é feito ao chamar o script sem argumentos.
set -euo pipefail
set +x

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/../.." && pwd)"
traefik_dir=/root/applications/server-setup/traefik
secret_dir=/root/.secrets/saqz-prod
k3s_version=v1.36.4+k3s1

fail() { echo "$*" >&2; exit 1; }
confirm() {
  local answer
  read -r -p "$* Digite sim para continuar: " answer
  [[ "$answer" == sim ]] || fail 'Etapa cancelada.'
}
k() { /usr/local/bin/k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml "$@"; }

case "${1:-ajuda}" in
  ajuda|--help|-h)
    echo 'Uso: bash deploy/server/setup.sh verificar|instalar|segredos|ponte'
    echo 'Leia deploy/README.md. Cada etapa que altera o servidor pede confirmação.'
    exit 0
    ;;
  verificar|instalar|segredos|ponte) ;;
  *) fail 'Etapa desconhecida. Use --help.' ;;
esac
[[ "$EUID" -eq 0 ]] || fail 'Execute no servidor como root.'

case "$1" in
  verificar)
    uname -m
    nproc
    free -h
    df -h /
    ss -lntup
    docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
    docker network inspect local-server-network --format '{{json .IPAM.Config}}'
    if [[ -x /usr/local/bin/k3s ]]; then
      k get nodes -o wide
      k get pods -A
      k -n kube-system get service traefik
    fi
    ;;
  instalar)
    for tool in curl docker python3 systemctl; do
      command -v "$tool" >/dev/null || fail "Ferramenta ausente: $tool"
    done
    [[ "$(uname -m)" == x86_64 ]] || fail 'Esta implantação foi preparada para amd64.'
    [[ ! -e /usr/local/bin/k3s && ! -e /etc/rancher/k3s/config.yaml ]] ||
      fail 'k3s ou configuração já existe. Inspecione antes de reinstalar.'
    docker network inspect local-server-network --format '{{json .IPAM.Config}}' |
      python3 -c 'import json,sys; assert any(x.get("Gateway")=="172.18.0.1" for x in json.load(sys.stdin)), "Gateway Docker diferente do previsto"'
    python3 - <<'PY'
import socket
for host, port in [('172.18.0.1', 6443), ('172.18.0.1', 10250), ('172.18.0.1', 30080)]:
    with socket.socket() as sock:
        sock.bind((host, port))
PY
    confirm "Instalar k3s $k3s_version com containerd e Traefik NodePort?"
    install -d -m 700 /etc/rancher/k3s
    install -m 600 "$script_dir/k3s-config.yaml" /etc/rancher/k3s/config.yaml
    install -d /var/lib/rancher/k3s/server/manifests /etc/systemd/system/k3s.service.d
    install -m 644 "$script_dir/traefik-k3s.yaml" /var/lib/rancher/k3s/server/manifests/saqz-traefik.yaml
    cat > /etc/systemd/system/k3s.service.d/docker-network.conf <<'EOF'
[Unit]
Wants=docker.service
After=docker.service
EOF
    installer="$(mktemp)"
    trap 'rm -f "$installer"' EXIT
    curl --fail --silent --show-error --location https://get.k3s.io -o "$installer"
    INSTALL_K3S_VERSION="$k3s_version" sh "$installer" server
    k wait --for=create node --all --timeout=180s
    k wait --for=condition=Ready node --all --timeout=180s
    k -n kube-system wait --for=create deployment/traefik --timeout=180s
    k -n kube-system rollout status deployment/traefik --timeout=180s
    k -n kube-system get service traefik
    echo 'k3s instalado. Valide DNS e saída dos pods antes de implantar o backend.'
    ;;
  segredos)
    [[ -x /usr/local/bin/k3s ]] || fail 'Instale o k3s primeiro.'
    python3 - "$secret_dir" <<'PY'
import json, re, stat, sys
from pathlib import Path
from urllib.parse import urlsplit
root = Path(sys.argv[1])
env = root / 'backend.env'
sa = root / 'firebase-admin.json'
for path in [env, sa]:
    if path.is_symlink() or not path.is_file() or stat.S_IMODE(path.stat().st_mode) != 0o600:
        sys.exit('Exigido arquivo regular com permissão 600: ' + path.name)
values = {}
for line in env.read_text().splitlines():
    if not line.strip() or line.lstrip().startswith('#'):
        continue
    key, sep, value = line.partition('=')
    if not sep or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key) or key in values:
        sys.exit('Linha inválida ou variável duplicada no backend.env.')
    values[key] = value
required = ['SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
            'SAQZ_MAIL_HOST', 'SAQZ_MAIL_PORT', 'SAQZ_MAIL_USERNAME', 'SAQZ_MAIL_PASSWORD',
            'SAQZ_MAIL_FROM', 'SAQZ_ASAAS_API_KEY', 'SAQZ_ASAAS_WEBHOOK_TOKEN',
            'SAQZ_PASSWORD_RESET_SECRET', 'SAQZ_SUBSCRIPTION_PURCHASE_URL']
for key in required:
    if not values.get(key):
        sys.exit('Preencha ' + key + ' antes de importar os segredos.')
if values.get('SAQZ_ASAAS_BASE_URL') != 'https://api.asaas.com/v3':
    sys.exit('Configure a URL de produção do Asaas.')
url = urlsplit(values['SAQZ_SUBSCRIPTION_PURCHASE_URL'])
if (url.scheme != 'https' or not url.hostname or url.username or url.query or url.fragment
        or url.hostname.endswith('brunoalmeida.dev')):
    sys.exit('Configure uma URL HTTPS de contratação de produção.')
if json.loads(sa.read_text()).get('project_id') != 'saquz-app':
    sys.exit('Service account não pertence ao projeto saquz-app.')
print('Arquivos de segredos verificados; valores não exibidos.')
PY
    confirm 'Criar/atualizar namespace saqz-prod e seus dois Secrets?'
    k apply -f "$repo_dir/deploy/k8s/overlays/prod/namespace.yaml"
    k -n saqz-prod create secret generic backend-env \
      --from-env-file="$secret_dir/backend.env" --dry-run=client -o yaml |
      k apply --server-side --field-manager=saqz-secrets -f -
    k -n saqz-prod create secret generic firebase-admin \
      --from-file="firebase-admin.json=$secret_dir/firebase-admin.json" --dry-run=client -o yaml |
      k apply --server-side --field-manager=saqz-secrets -f -
    echo 'Segredos importados. Pods existentes precisam de rollout restart para reler o ambiente.'
    ;;
  ponte)
    [[ -x /usr/local/bin/k3s ]] || fail 'Instale o k3s primeiro.'
    command -v patch >/dev/null || fail 'Ferramenta ausente: patch'
    [[ -f "$traefik_dir/docker-compose.yaml" ]] || fail 'Compose do Traefik não encontrado.'
    [[ ! -e "$traefik_dir/docker-compose.yaml.saqz-prod.bak" ]] ||
      fail 'Backup da ponte já existe. Inspecione a configuração antes de repetir.'
    patch --dry-run --fuzz=0 -d "$traefik_dir" -p0 < "$script_dir/traefik.compose.patch"
    # A ponte só entra depois de a API responder pelo Traefik interno.
    status="$(curl --silent --show-error --max-time 10 -o /dev/null -w '%{http_code}' \
      -H 'Host: api.saqz.app' http://172.18.0.1:30080/api/session/onboarding)"
    [[ "$status" == 401 ]] || fail "API interna retornou $status; esperado 401 sem autenticação."
    confirm 'Ativar a ponte de produção? O Traefik Docker será recriado e haverá breve interrupção dos hosts atendidos.'
    cp -p "$traefik_dir/docker-compose.yaml" "$traefik_dir/docker-compose.yaml.saqz-prod.bak"
    install -d "$traefik_dir/dynamic"
    install -m 644 "$script_dir/traefik-docker.yaml" "$traefik_dir/dynamic/saqz-prod.yaml"
    patch --fuzz=0 -d "$traefik_dir" -p0 < "$script_dir/traefik.compose.patch"
    docker compose -f "$traefik_dir/docker-compose.yaml" config --quiet
    docker compose -f "$traefik_dir/docker-compose.yaml" up -d --no-deps traefik
    echo 'Ponte ativada. Configure o hostname no túnel Cloudflare e execute os smoke tests do README.'
    ;;
esac
