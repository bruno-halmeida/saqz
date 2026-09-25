# Backend de produção

k3s em um servidor Debian amd64, com banco no Supabase e SMTP na Hostinger.
O compose continua atendendo staging. Estes arquivos não instalam nem publicam nada
até que os comandos abaixo sejam executados.

## Releases

Cada release usa a mesma versão na tag Git, imagem e overlay: `v.0.0.1`, `v.0.0.2`, etc.
O workflow `backend-image.yml` publica apenas após push de uma tag `v.X.Y.Z`
cujo commit já esteja na `main`. Publica uma única tag de imagem, sem `main` ou `latest`.
O build roda no GitHub para `linux/amd64`, com cache GHA e o Dockerfile existente.
Antes dele, um job leve compara `backend/` com a maior versão anterior alcançável
no histórico da tag. A primeira release compara com uma árvore vazia para publicar
o backend existente. Se só mobile, páginas, infraestrutura ou workflow mudaram, o
job de build/publicação é ignorado e a versão do backend implantado deve ser mantida.
Esse filtro é feito no job porque o GitHub não aplica filtros `paths` a pushes de tags.

Após review e merge das mudanças, incluindo `newTag` no overlay de produção:

```bash
git switch main
git pull --ff-only
git tag -a v.0.0.1 -m 'Saqz v.0.0.1'
git push origin v.0.0.1
```

Nunca mova uma tag publicada nem reutilize uma versão para outro build. Para corrigir
uma release, crie outra versão. Publicar a imagem não aplica nada no servidor.
Na primeira publicação, torne o pacote `saqz-backend` **público** nas configurações
do GHCR: o repositório público não garante essa visibilidade automaticamente.
Confirme o pull anônimo antes do deploy; não há `imagePullSecret` nos manifestos.

## Instalação do servidor

Execute da raiz deste repositório. O script tem etapas independentes com confirmação:

```bash
bash deploy/server/setup.sh verificar
bash deploy/server/setup.sh instalar
```

O instalador fixa `v1.36.4+k3s1`, versão do canal stable consultada em 2026-09-25.
Desabilita ServiceLB e metrics-server. O Traefik permanece instalado, com Service
NodePort HTTP `30080`. O `HelmChartConfig` configura o componente empacotado;
os recursos da aplicação usam Kustomize, sem instalação manual de charts Helm.

As portas 80/443 continuam no Docker. O host `172.18.0.1` pertence à rede Docker
`local-server-network`; API Kubernetes e NodePort usam esse endereço privado.
O kubelet escuta em loopback, acessado pelo túnel do k3s, e o Flannel usa `host-gw`
para este cluster de um node. O serviço k3s inicia depois do Docker. Não remova essa
rede; outro servidor ou expansão do cluster exige rever os endereços e a rede.

Antes do primeiro apply, valide o node Ready, Traefik, CoreDNS e saída HTTPS/DNS dos
pods. O Docker tem política FORWARD DROP neste host: se houver falha de comunicação,
inspecione as regras CNI/Docker antes de liberar tráfego. Não altere a política global
para ACCEPT nem reinicie a stack de staging para tentar corrigir o cluster.

```bash
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml get pods -A
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml run verificar-rede \
  --image=busybox:1.37 --restart=Never --rm -i -- \
  sh -c 'nslookup kubernetes.default.svc.cluster.local && wget -q -O /dev/null https://example.com'
```

## Segredos

Mantenha os arquivos fora do Git, pertencentes ao root, com permissão `600`:

- `/root/.secrets/saqz-prod/backend.env`: linhas `CHAVE=VALOR`, sem aspas nem `export`.
- `/root/.secrets/saqz-prod/firebase-admin.json`: service account do Firebase `saquz-app`.

O `backend.env` contém os três `SPRING_DATASOURCE_*`, os campos `SAQZ_MAIL_*`,
`SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`, e, para SMTP 465,
`SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true` e `SAQZ_MAIL_STARTTLS=false`.
Inclua também `SAQZ_ASAAS_BASE_URL=https://api.asaas.com/v3`, `SAQZ_ASAAS_API_KEY`,
`SAQZ_ASAAS_WEBHOOK_TOKEN` e `SAQZ_PASSWORD_RESET_SECRET`.

**Pendente antes do deploy:** definir `SAQZ_SUBSCRIPTION_PURCHASE_URL` com a URL HTTPS
de produção aprovada para os e-mails de contratação. Ela é obrigatória no Deployment
para impedir o fallback para `/assinar` de staging. O painel web de produção ainda
está fora desta implantação; CORS administrativo fica vazio e WhatsApp desligado.
O perfil `prod`, Firebase, portas e demais configurações fixas vêm do ConfigMap.

```bash
bash deploy/server/setup.sh segredos
```

O script usa `kubectl create secret generic --from-env-file` e `--from-file`, seguido
de server-side apply. São dois Secrets: `backend-env` para variáveis e `firebase-admin`
para o arquivo montado em `/run/secrets/firebase-admin.json`. O JSON não vira variável
de ambiente. Não execute com `bash -x` nem imprima os Secrets em logs ou no terminal.
Atualizações do arquivo local só chegam ao cluster após repetir a importação; depois
reinicie os pods para reler as variáveis.

O webhook de assinaturas foi cadastrado pelo usuário no Asaas. Verifique no painel
URL `https://api.saqz.app/webhooks/asaas`, token igual ao arquivo e eventos tratados
em `ProcessAsaasWebhook`. Não crie um segundo webhook. Confirme a autenticação da API
Asaas com uma consulta de leitura antes de ativar pagamentos reais.

## Aplicar uma release

Confirme que o workflow terminou, o pacote está público e `newTag` no overlay contém
a versão desejada. O primeiro boot executa Flyway no banco de produção; confirme o
projeto Supabase de destino antes de aplicar. Na primeira instalação o banco deve
estar vazio; nos próximos deploys tenha backup compatível com as novas migrações.

```bash
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml kustomize deploy/k8s/overlays/prod
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml apply --dry-run=server -k deploy/k8s/overlays/prod
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml apply -k deploy/k8s/overlays/prod
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml -n saqz-prod rollout status deployment/backend --timeout=600s
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml -n saqz-prod port-forward service/backend 19090:9090
```

Em outro terminal, `curl --fail http://127.0.0.1:19090/actuator/health` deve retornar
`UP`. A gestão 9090 só aparece no Service interno, sem Ingress. Readiness e liveness
usam esse endpoint; como ele inclui o banco, uma indisponibilidade prolongada do
Supabase também pode reiniciar o pod. A startup probe permite até cinco minutos.
O rollout pode manter temporariamente duas réplicas de até 1 GiB cada.

## Ponte e Cloudflare

```bash
bash deploy/server/setup.sh ponte
```

Essa etapa exige a API respondendo 401 sem autenticação em `/api/session/onboarding`
pelo NodePort. Faz backup do compose do Traefik externo, aplica o pequeno patch de
file provider e monta `dynamic/saqz-prod.yaml`. Recria apenas o Traefik Docker, causando
breve interrupção dos hosts atendidos. Os containers de staging não são recriados.
A mudança em `compose.server.yaml` impede que um futuro deploy de staging reivindique
`api.saqz.app`; não execute `deploy.sh` como parte desta implantação.
O router de produção tem prioridade explícita sobre a regra antiga dos dois hosts,
mesmo antes de recriar o backend de staging. Seu middleware remove o header
`Forwarded` enviado pelo cliente antes de encaminhar ao k3s; o Spring usa os
headers `X-Forwarded-*` tratados pelos proxies para resolver a origem da requisição.

No túnel Cloudflare existente, adicione o hostname `api.saqz.app`, copiando o serviço
de origem HTTPS e as opções TLS usados para chegar ao Traefik Docker. Preserve o
Host `api.saqz.app`; não copie um override de Host/SNI de staging sem revisar.
O router externo usa o mesmo modelo TLS dos hosts que já passam pelo túnel.

Depois valide a resposta 401 em `https://api.saqz.app/api/session/onboarding`, login com build
Release/TestFlight (Firebase `saquz-app`), recuperação de senha e recebimento de
eventos Asaas. Confirme no painel se a fila do webhook precisa ser retomada após
o período em que a URL ainda não estava disponível. Testes que enviam e-mail ou
efetuam cobranças precisam de destinatário/caso de teste combinado.

## Recuperação

Para a aplicação, volte `newTag` à versão anterior e aplique o overlay. Uma imagem
anterior não desfaz migrações: verifique compatibilidade do schema antes do rollback.
Para atualização de segredos, importe novamente e execute `rollout restart deployment/backend`
no namespace `saqz-prod` com o kubeconfig acima.

Para desfazer a ponte, restaure `docker-compose.yaml.saqz-prod.bak` no diretório do
Traefik e recrie esse serviço com `docker compose up -d --no-deps traefik`.
Mantenha `api.saqz.app` fora de staging. Uma falha parcial da etapa ponte exige
inspecionar o backup e o compose antes de repetir. Não desinstale o k3s para reverter
uma release; os Secrets e o estado do cluster precisam ser preservados.

Referências: [configuração k3s](https://docs.k3s.io/installation/configuration),
[Traefik empacotado](https://docs.k3s.io/networking/networking-services),
[build-push-action](https://github.com/docker/build-push-action).
