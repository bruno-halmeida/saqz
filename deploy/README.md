# Deploy do backend

k3s em um servidor Debian amd64, com banco no Supabase e SMTP na Hostinger.
Dev/staging continua no Compose. O Actions publica a imagem e atualiza dev;
produção é implantada manualmente, escolhendo uma versão publicada.

## Releases

Cada release usa a mesma versão na tag Git e imagem: `v.0.0.1`, `v.0.0.2`, etc.
O workflow `backend-image.yml` roda após mudanças em `backend/**` na `main`.
Ele incrementa o último número da maior versão existente, publica a imagem no GHCR
e só então cria a tag Git no commit correspondente. A primeira versão é `v.0.0.1`.
Publica uma única tag de imagem, sem `main` ou `latest`.
O build roda no GitHub para `linux/amd64`, com cache GHA e o Dockerfile existente.
Antes dele, uma etapa leve compara `backend/` com a última release.
A primeira release compara com uma árvore vazia para publicar
o backend existente. Se só mobile, páginas, infraestrutura ou workflow mudaram, o
build/publicação é ignorado e a versão do backend implantado deve ser mantida.
As execuções são serializadas para não reservar a mesma versão simultaneamente;
reexecutar um commit já publicado não cria outra release.

Para a primeira publicação ou para tentar novamente uma execução que falhou, use
o disparo manual na `main`. Ele também verifica se há mudanças no backend:

```bash
gh workflow run backend-image.yml --ref main
```

Nunca mova uma tag publicada nem reutilize uma versão para outro commit. Para corrigir
uma release, faça uma nova alteração no backend. Após publicar a imagem e a tag,
o workflow chama o deploy de dev com essa versão. Se a publicação for ignorada,
não há deploy automático. Produção só muda ao executar seu workflow manual.
Se o push da imagem funcionar e o da tag Git falhar, a próxima execução interrompe
ao encontrar a imagem existente. Recupere a tag no commit original antes de continuar.
Na primeira publicação, torne o pacote `saqz-backend` **público** nas configurações
do GHCR: o repositório público não garante essa visibilidade automaticamente.
Confirme o pull anônimo antes do deploy; não há `imagePullSecret` nos manifestos.

## Deploy pelo Actions

Em **Actions**, escolha **Deploy do backend em dev** ou **Deploy do backend em produção**,
clique em **Run workflow**, selecione `main` e informe `version`, por exemplo `v.0.0.1`.
Também é possível disparar pela CLI:

```bash
gh workflow run backend-deploy-dev.yml --ref main -f version=v.0.0.1
gh workflow run backend-deploy-prod.yml --ref main -f version=v.0.0.1
```

Ambos verificam se a tag pertence ao histórico de `main` e se a imagem está pública
no GHCR antes de conectar por SSH. Os deploys de cada ambiente são serializados.
`backend-deploy.yml` contém a execução compartilhada pelos dois workflows.

- **Dev:** puxa a imagem escolhida e recria apenas `backend`, usando o `.env` em
  `/root/applications/saqz/.env` e a service account de dev já existente. O override
  `deploy/server/compose.image.yaml` desativa o build local. Aguarda o health do Compose.
- **Produção:** importa novamente `/root/.secrets/saqz-prod/backend.env` e a service
  account, aplica os manifestos no namespace `saqz-prod` e aguarda o rollout do k3s.
  Uma cópia temporária do overlay recebe a versão escolhida; o checkout do servidor
  permanece intacto. A anotação do pod força a leitura dos Secrets mesmo na mesma versão.

Para aplicar mudanças no `.env` ou repetir um deploy que falhou, execute o workflow
do ambiente com a versão desejada. Isso não faz build nem cria outra tag. Uma falha
no deploy de dev não apaga a imagem/tag já publicada. A execução falha se o backend
não ficar saudável; não há rollback automático de imagem ou de migrações.

### Configuração única do acesso

Em **Settings → Environments**, crie `dev` e `prod` e limite as branches de deploy
a `main`. Configure em cada Environment:

| Tipo | Nome | Valor |
| --- | --- | --- |
| Variable | `DEPLOY_HOST` | IP público ou hostname SSH do servidor |
| Variable | `DEPLOY_PORT` | Porta SSH; padrão `22` |
| Variable | `DEPLOY_USER` | `root`, exigido pelos scripts deste servidor |
| Secret | `DEPLOY_SSH_KEY` | Chave privada dedicada ao Actions, sem senha |
| Secret | `DEPLOY_KNOWN_HOSTS` | Linha de `known_hosts` conferida no próprio servidor |

No servidor, crie a chave dedicada uma vez, fora do repositório:

```bash
install -d -m 700 /root/.ssh
ssh-keygen -t ed25519 -N '' -C saqz-actions -f /root/.ssh/saqz-actions
printf 'restrict %s\n' "$(cat /root/.ssh/saqz-actions.pub)" >> /root/.ssh/authorized_keys
chmod 600 /root/.ssh/authorized_keys
```

Use o conteúdo de `/root/.ssh/saqz-actions` no secret `DEPLOY_SSH_KEY` de cada
Environment. Essa chave permite executar os comandos de deploy como root;
as credenciais de banco, Firebase, Asaas e SMTP continuam somente no servidor.

Para obter a linha de `DEPLOY_KNOWN_HOSTS` da chave pública local do SSH, substitua
o endereço abaixo pelo mesmo valor de `DEPLOY_HOST`:

```bash
awk -v host='IP_OU_HOSTNAME_DO_SERVIDOR' '{print host, $1, $2}' /etc/ssh/ssh_host_ed25519_key.pub
```

Se usar outra porta, o primeiro campo deve ser `[IP_OU_HOSTNAME_DO_SERVIDOR]:PORTA`.
O runner exige a chave conhecida; não descobre nem aceita chaves remotas automaticamente.
O servidor precisa aceitar SSH dos runners do GitHub. O pacote transferido contém
somente os arquivos de implantação, sem `.env` ou service accounts. Nenhuma etapa
executa `git pull`, altera a ponte Traefik ou recria os serviços web/Mailpit.

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
`local-server-network`; API Kubernetes, kubelet e NodePort usam esse endereço privado.
O Flannel usa `host-gw`
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

Defina `SAQZ_SUBSCRIPTION_PURCHASE_URL=https://saqz.app/assinar/`, endereço no domínio
de produção aprovado para os e-mails de contratação. O backend exige o caminho
exato `/assinar/`; a raiz do site impede o boot. Ela é obrigatória no Deployment
para impedir o fallback para `/assinar` de staging. O painel web de produção ainda
está fora desta implantação; CORS administrativo fica vazio e WhatsApp desligado.
A página `/assinar/` também precisa ser publicada antes de usar a contratação por e-mail.
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

## Aplicar uma release manualmente no servidor

Como alternativa ao Actions, confirme que a publicação terminou, o pacote está público
e `newTag` no overlay contém a versão desejada. O Actions não grava sua escolha nesse
arquivo; confira a versão antes de um apply manual para evitar retornar à versão inicial.
O primeiro boot executa Flyway no banco de produção; confirme o
projeto Supabase de destino antes de aplicar. Na primeira instalação o banco deve
estar vazio; nos próximos deploys tenha backup compatível com as novas migrações.

Um projeto Supabase novo pode conter a função `public.rls_auto_enable` e o event
trigger `ensure_rls`, mesmo sem tabelas. Nesse caso, o Flyway recusa a inicialização
por considerar o schema não vazio. Após verificar que não existem tabelas, tipos
ou outras funções da aplicação nem histórico Flyway, execute uma única vez
`baseline` com `baselineVersion=0`, usando a versão Flyway da imagem e as credenciais
de produção fora do Git. A versão zero permite executar todas as migrações desde V1.
Preserve a função/event trigger de RLS; não habilite baseline automático nos boots.
Essa inicialização foi executada na primeira implantação de produção em 2026-09-26.

```bash
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml kustomize deploy/k8s/overlays/prod
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml apply --dry-run=server -k deploy/k8s/overlays/prod
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml apply -k deploy/k8s/overlays/prod
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml -n saqz-prod rollout status deployment/backend --timeout=600s
sudo k3s kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml -n saqz-prod port-forward service/backend 19090:9090
```

Em outro terminal, `curl --fail http://127.0.0.1:19090/actuator/health` deve retornar
`UP`. O Ingress também encaminha o caminho exato `/actuator/health` à porta 9090,
permitindo consultar `https://api.saqz.app/actuator/health` pelo túnel existente.
Os demais endpoints de gestão, incluindo Prometheus, continuam internos.
Readiness e liveness usam esse endpoint; como ele inclui o banco, uma indisponibilidade prolongada do
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
Se a rota `*.saqz.app` já aponta para `https://traefik:443`, ela atende a API sem
uma rota adicional. Confirme também o CNAME `*` da zona DNS apontando para
`<id-do-túnel>.cfargotunnel.com` com proxy habilitado: a rota no túnel não basta.

Depois valide a resposta 401 em `https://api.saqz.app/api/session/onboarding`, login com build
Release/TestFlight (Firebase `saquz-app`), recuperação de senha e recebimento de
eventos Asaas. Confirme no painel se a fila do webhook precisa ser retomada após
o período em que a URL ainda não estava disponível. Testes que enviam e-mail ou
efetuam cobranças precisam de destinatário/caso de teste combinado.

## Recuperação

Para a aplicação, execute o workflow do ambiente com a versão anterior, ou volte
`newTag` e aplique o overlay manualmente. Uma imagem anterior não desfaz migrações:
verifique compatibilidade do schema antes do rollback. Para atualizar segredos,
repita o deploy da mesma versão pelo Actions; manualmente, importe novamente e execute
`rollout restart deployment/backend` no namespace `saqz-prod` com o kubeconfig acima.

Para desfazer a ponte, restaure `docker-compose.yaml.saqz-prod.bak` no diretório do
Traefik e recrie esse serviço com `docker compose up -d --no-deps traefik`.
Mantenha `api.saqz.app` fora de staging. Uma falha parcial da etapa ponte exige
inspecionar o backup e o compose antes de repetir. Não desinstale o k3s para reverter
uma release; os Secrets e o estado do cluster precisam ser preservados.

Referências: [configuração k3s](https://docs.k3s.io/installation/configuration),
[Traefik empacotado](https://docs.k3s.io/networking/networking-services),
[build-push-action](https://github.com/docker/build-push-action).
