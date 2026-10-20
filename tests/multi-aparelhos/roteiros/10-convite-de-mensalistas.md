# Roteiro 10 · Convite de mensalistas pelo WhatsApp

Regras do cofre cobertas: CONV-010, CONV-011 e CONV-012 (decididas em 20/10), os efeitos em NOT-004, MEM-003, SAI-005, SAI-007 e FIN-003 e, no bloco V10, o "aceitar o pedido como mensalista ou avulso" de CONV-007 (DEB-112, que entra **depois** do DEB-106: V10 só roda quando ele for entregue). Se o texto do app e a nota divergirem, vale a nota; anote a divergência no relato.

**Backend e app do DEB-106 já estão na `main`** (migração V178: tabelas `group_monthly_invitees` e `group_monthly_invite_links`, coluna de validade `expires_at`; telefone guardado como `+55DDD9XXXXXXXX`). Os rótulos vêm do protótipo (`design/convite-mensalistas/telas.html`) e das telas implementadas; as rotas de API estão em `MonthlyInviteController` (`/api/groups/{groupId}/monthly-invites`). Passos **[mock+]** dependem das duas extensões do Uazapi falso descritas em "Antes de começar"; sem elas ficam BLOQUEADO.

Papéis: **ORG** (dono), **ADM** (administrador), **ATL** (avulso que já está no grupo) e **CV1**, **CV2**, **CV3**, **CV4** (pessoas de fora que recebem o link; dividem um aparelho: para trocar de papel, Perfil → "Sair da conta" e entrar com a conta do outro). As contas são as do roteiro 05: ADM é a ATL2; CV1, CV2 e CV3 são ATL3, ATL4 e ATL5; CV4 é nova.

Use `<data>` = dia de hoje (`DD/MM`) nos nomes e `<tag>` = `DDMM` (sem barra) nos e-mails. Contas: `qa-org+<tag>@saqz.local` (ORG), `qa-atl+<tag>` (ATL), `qa-atl2+<tag>` (ADM), `qa-atl3+<tag>` (CV1), `qa-atl4+<tag>` (CV2), `qa-atl5+<tag>` (CV3), `qa-atl6+<tag>` (CV4), todas com `@saqz.local` e senha `SenhaQA2026`. Celulares `11900000001` (ORG), `11900000002` (ATL), `11900000003` (ADM), `11900000004` (CV1), `11900000005` (CV2), `11900000006` (CV3), `11900000007` (CV4). Nomes `QA Org <data>`, `QA Atl <data>`, `QA Atl2 <data>`, `QA Atl3 <data>`, `QA Atl4 <data>`, `QA Atl5 <data>`, `QA Atl6 <data>`. Grupos `QA Vôlei <data>` e `QA Vôlei B <data>`.

Pessoas sem conta, só na lista de convidados (cada número entra uma vez só por grupo; os passos usam números diferentes): `11900000010` a `11900000019`, `11900000030` a `11900000035` e `11900000040` a `11900000050`. **Pessoa B** é a CV2 convidada com **outro telefone**: a lista tem `11900000020`, e a conta de CV2 tem `11900000005`. Números especiais do Uazapi falso: `11900000090` (sem WhatsApp) e `11900000091` a `11900000099` (o envio falha).

As conferências de banco pressupõem banco limpo no começo de **cada rodada**: `stack.sh reset` + `stack.sh up` (e apagar os dados do app nos aparelhos, como no roteiro 01).

**Ordem de execução** (o estado de um bloco alimenta o seguinte): Preparação → V1 → V2 → V3 → V4 → V5 → V6 → V7 → V10 → V8 → V9. V8 e V9 mexem em relógio e em configuração e vão por último.

Etiquetas: **[CRÍTICO]** entra na rodada segura. **[push: só produção]**: o stack local não tem FCM/APNs; o passo confere a central e o log do Uazapi falso, e o push fica para `tests/producao`. **[WhatsApp: só produção]**: aqui o envio é o do Uazapi falso, que só grava; a mensagem chegar ao aparelho se confere em `tests/producao`. **[mock+]**: precisa das extensões do Uazapi falso.

## Antes de começar

O Uazapi falso e o backend com WhatsApp ligado seguem o cabeçalho de `tests/multi-aparelhos/ferramentas/uazapi-falso.mjs`. Ele grava cada chamada em `~/.saqz-multidev/logs/uazapi-mock.jsonl`; é a evidência dos envios.

**Extensões que o mock precisa ter (pendência de ferramenta, escrever junto da entrega):**

1. rota `/chat/check`, que responde `[{"query":"<número>","isInWhatsapp":<verdadeiro ou falso>}]` e dá `false` para números terminados em `90`;
2. `/send/text` que responde `500` para números terminados em `91` a `99`.

```sh
TAG=<tag>
PSQL() { docker exec saqz-multidev-db psql -U saqz -d saqz -Atc "$1"; }
tok() { curl -s "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=qa" -H 'Content-Type: application/json' -d "{\"email\":\"qa-$1+$TAG@saqz.local\",\"password\":\"SenhaQA2026\",\"returnSecureToken\":true}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["idToken"])'; }
api() { w=$1; m=$2; p=$3; shift 3; curl -s -w '\n%{http_code}\n' -X "$m" "http://127.0.0.1:18080$p" -H "Authorization: Bearer $(tok $w)" -H 'Content-Type: application/json' "$@"; }
uid() { PSQL "select id from access_users where email='qa-$1+$TAG@saqz.local'"; }
GID=$(PSQL "select id from access_groups where name='QA Vôlei <data>' and deleted_at is null")
MOCK=~/.saqz-multidev/logs/uazapi-mock.jsonl
envios() { python3 -c 'import json,sys
for l in open(sys.argv[1]):
    d=json.loads(l)
    if d["path"]=="/send/text":
        b=d["body"]; print(d["at"], b["number"], b["text"][:70].replace("\n"," / "))' "$MOCK"; }
intervalos() { python3 -c 'import json,sys,datetime as t
a=[t.datetime.fromisoformat(json.loads(l)["at"].replace("Z","+00:00")) for l in open(sys.argv[1]) if json.loads(l)["path"]=="/send/text"]
print([round((y-x).total_seconds(),1) for x,y in zip(a,a[1:])])' "$MOCK"; }
codigo() { python3 -c 'import json,re,sys
c=None
for l in open(sys.argv[2]):
    d=json.loads(l)
    if d["path"]=="/send/text" and d["body"]["number"]=="55"+sys.argv[1]:
        m=re.search(r"saqz_invite=([A-Za-z0-9-]+)", d["body"]["text"]); c=m.group(1) if m else c
print(c)' "$1" "$MOCK"; }
```

`api <papel> <método> <caminho> [-d '<json>']` devolve o corpo e, na última linha, o código HTTP. Papéis: `org`, `atl`, `atl2` (ADM), `atl3`, `atl4`, `atl5`, `atl6`.

- **R-ZERAR**: `: > "$MOCK"` esvazia o log antes de um bloco que conta envios.
- **R-CÓDIGO-PÚBLICO**: `PSQL "select short_code from group_invites where group_id='$GID'"`. O link público é `https://links.saqz.app/?saqz_invite=<código>`.
- **R-CÓDIGO-MS**: `codigo 11900000004` devolve o código do link de mensalistas que foi no WhatsApp para esse número (lê o log do mock). Chame de `COD_MS`; depois de um código novo, `COD_MS2`.
- **R-ENTREGAR**: como no roteiro 05, com o código desejado. Android: `adb -s <id> shell am start -a android.intent.action.VIEW -d 'https://links.saqz.app/?saqz_invite=<código>' app.saqz`. iOS: `xcrun simctl openurl <udid> 'saqz://links.saqz.app/?saqz_invite=<código>'`.
- **R-PRÉVIA** `<papel> <código>`: `api <papel> POST /api/invites/preview -d '{"code":"<código>"}'` devolve o cartão (200) ou o erro do link.
- **R-SEM-TELEFONE** `<papel>`: `PSQL "update access_users set phone=null where email='qa-<papel>+$TAG@saqz.local'"` (se a conta aceitar telefone nulo; senão BLOQUEADO). O aviso de entrada vai ao telefone cadastrado, confirmado ou não (exceção à DEB-081).
- **R-ENVELHECER** `<dias>`: faz o link vigente ter vencido há `<dias>` dias: `PSQL "update group_monthly_invite_links set expires_at=now()-interval '<dias> days' where group_id='$GID'"`. **R-ENVELHECER-INVALIDADO** `<dias>` faz o mesmo com a data da invalidação .
- **R-SEM-MENSALIDADE**: `PSQL "update access_groups set monthly_fee_cents=null, monthly_due_day=null where id='$GID'"`. **R-COM-MENSALIDADE**: `PSQL "update access_groups set monthly_fee_cents=8000, monthly_due_day=10 where id='$GID'"`.
- **R-LIMITE-LIGAR** e **R-LIMITE-DESLIGAR**: as do roteiro 05 (plano com limite de 25 atletas, grupo preenchido com membros falsos). **R-VAGAS** `<n>`: `PSQL "delete from group_memberships where ctid in (select ctid from group_memberships where group_id='$GID' and user_id in (select id from access_users where firebase_subject like 'fake-%') limit <n>)"` abre `<n>` vagas.

> As receitas R-ENVELHECER, R-VAGAS e as de nomes entre `<>` **não foram executadas** por quem escreveu o roteiro (a funcionalidade ainda não existe). Se alguma falhar, registre a mensagem e marque BLOQUEADO só os passos que dependem dela.

## Preparação

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | terminal | `stack.sh reset`, `stack.sh up`, `aparelhos.sh instalar`; dados dos apps apagados; Uazapi falso no ar com as extensões e backend com WhatsApp ligado; definir as receitas acima | Backend em 18080 e Uazapi falso respondendo | `select count(*) from access_users` = 0 | ☐ | ☐ |
| P2 | ORG | Criar conta (`QA Org <data>`, celular `11900000001`) e o grupo `QA Vôlei <data>` como no roteiro 05 (P2). **Não** preencher mensalidade | `Grupo criado!` | `select monthly_fee_cents, monthly_due_day from access_groups where id='$GID'` = `\|` (os dois nulos) | ☐ | ☐ |
| P3 | ATL, ADM, CV1, CV2, CV3, CV4 | Criar as contas sem convite, com os celulares da lista (como em P3 do roteiro 05); CV1 a CV4 no mesmo aparelho, uma conta por vez | Início sem grupos, com `Tenho um convite` | `select count(*) from access_users where email like 'qa-%+<tag>@saqz.local'` = 7 | ☐ | ☐ |
| P4 | ATL, ADM, ORG | ATL e ADM entram pelo convite público (como P4 e P5 do roteiro 05); ORG torna ADM administrador (P6) | `Você entrou no QA Vôlei <data>`; ADM com `Administrador` | ATL e ADM com `membership_type='AVULSO'`; ADM com `role='ADMIN'` | ☐ | ☐ |
| P5 | terminal | R-CÓDIGO-PÚBLICO (anotar `COD_PUB`); `api org GET /api/whatsapp/availability`; R-ZERAR | `COD_PUB` com 8 caracteres; `enabled` verdadeiro; log vazio | `select count(*) from access_users where email like 'qa-%+<tag>@saqz.local' and verified_phone is not null` = `0` (ninguém confirmou o número) | ☐ | ☐ |

## V1 · CONV-010 · Bloqueio, mensalidade e cadastro [CRÍTICO] [MULTI] [WhatsApp: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V1.1 | ORG | Grupo → "Membros e permissões" (ninguém convidado ainda) | Cartão `Mensalistas pelo WhatsApp` com `Cadastre quem paga mensalidade e mande o convite de uma vez. Quem aceitar já entra como mensalista.` e o botão `Convidar mensalista`; os chips não têm `Convidados` | — | ☐ | ☐ |
| V1.2 | ATL | Abrir "Membros e permissões" (se o app o mostra a atletas) e procurar `Convidar mensalista`. Pela API: `api atl GET /api/groups/$GID/monthly-invites` | Sem o botão, sem seção `Convidados` e sem chip; a API devolve `403`  | — | ☐ | ☐ |
| V1.3 | ORG | Tocar "Convidar mensalista" (grupo sem mensalidade padrão) | Tela bloqueada: `Defina o valor e o dia da mensalidade do grupo` e `Quem entra como mensalista paga esse valor. Configure uma vez e volte para convidar.`, botão `Configurar mensalidade`; não existe `Adicionar pessoa` | — | ☐ | ☐ |
| V1.4 | ORG | `Configurar mensalidade` → em Editar grupo, cartão `Mensalidade` (`Vale para quem entra como mensalista. Dá pra ajustar por pessoa depois.`): `Valor por mês` `80,00`, `Dia do vencimento` 10 → "Salvar" | Salva e volta | `select monthly_fee_cents, monthly_due_day from access_groups where id='$GID'` = `8000\|10` | ☐ | ☐ |
| V1.5 | ORG | Editar grupo → `Dia do vencimento`: levar até 28 e tocar `+` de novo; depois levar até 1 e tocar `−` de novo; sair **sem salvar** | O contador para em 28 e em 1; o banco segue em 10 | `monthly_due_day` = `10` | ☐ | ☐ |
| V1.6 | ORG | Voltar a "Convidar mensalista" | Tela liberada: `Quem vai receber`, `Ninguém na lista ainda`, `Adicione o nome e o WhatsApp de cada mensalista. Dá pra colocar várias pessoas antes de enviar.`, cartão `R$ 80,00 por mês` com `Vence todo dia 10, para todos que entrarem.`; botão `Enviar convites` desligado com `Adicione pelo menos uma pessoa para enviar.` | — | ☐ | ☐ |
| V1.7 | ADM | Grupo → "Membros e permissões" → `Convidar mensalista` | A mesma tela liberada (admin também cadastra); voltar sem adicionar ninguém | — | ☐ | ☐ |
| V1.8 | ORG | `Adicionar pessoa` → folha `Nova pessoa`: `Nome` `A`, `WhatsApp` `(11) 9000` | `Digite o nome da pessoa.` e `Faltam dígitos. Use o DDD e os 9 dígitos, por exemplo (11) 98765-4321.`; a pessoa não entra na lista | — | ☐ | ☐ |
| V1.9 | ORG | Mesma folha: `WhatsApp` `(11) 88888-7777`; depois `(10) 99999-9999` | Nos dois: `Esse número não parece um celular do Brasil. Confira o DDD e o 9 do começo.` | — | ☐ | ☐ |
| V1.10 | ORG | Mesma folha: nome `ADM`, `WhatsApp` `(11) 90000-0003` (o celular de ADM, que é avulso) | Aviso na folha: `<nome de ADM> já está no grupo como avulso. Se promover, vira mensalista agora e não recebe mensagem.`; o botão principal vira `Promover a mensalista`; não há `Adicionar` | — | ☐ | ☐ |
| V1.11 | ORG | Tocar `Promover a mensalista` | Toast `<nome> agora é mensalista. Nenhuma mensagem foi enviada.`; a lista de destinatários segue vazia | `select membership_type, role from group_memberships where user_id='$(uid atl2)' and group_id='$GID'` = `MENSALISTA\|ADMIN`; `envios` sem mensagem para `5511900000003` | ☐ | ☐ |
| V1.12 | ORG | `Adicionar pessoa` com o celular de ADM de novo | `<nome> já é mensalista do grupo. Não precisa de convite.`; sem `Adicionar` | — | ☐ | ☐ |
| V1.13 | ORG | Montar o lote: `QA Atl3 <data>` `(11) 90000-0004` (CV1) e `Pessoa B` `(11) 90000-0020`, cada uma com `Adicionar e cadastrar outra`; depois `Pessoa C` `(11) 90000-0010` com `Adicionar`; depois `Adicionar outra pessoa` com `Pessoa D` `(11) 90000-0011` | A folha fica aberta depois de "Adicionar e cadastrar outra" e fecha em "Adicionar"; em `Quem vai receber` há 4 pessoas e o botão é `Enviar 4 convites`; abaixo, `Os convites saem um por vez, com uns segundos entre eles.` e o tempo estimado | — | ☐ | ☐ |
| V1.14 | ORG | `Adicionar outra pessoa` com `Pessoa C2` e o mesmo número de Pessoa C (`(11) 90000-0010`) | `Esse número já está na lista (Pessoa C).` e nada é adicionado | — | ☐ | ☐ |
| V1.15 | ORG | Remover `Pessoa D` pelo botão da linha; depois tocar `Desfazer` no toast | Toast `... saiu da lista.` com `Desfazer`; ao desfazer, `Pessoa D` volta na mesma posição; botão segue `Enviar 4 convites` | — | ☐ | ☐ |
| V1.16 | ORG | `Ver a mensagem que vai sair` | O texto traz `E aí,` + o nome, `🏐`, `O QA Org <data> te colocou na lista de mensalistas do QA Vôlei <data> aqui no Saqz.`, `Entra por aqui e confirma presença nos jogos direto pelo app:` e o link, e termina com `Se não era pra você, é só ignorar.` | — | ☐ | ☐ |
| V1.17 | ORG | R-ZERAR. Tocar `Enviar 4 convites` **duas vezes seguidas** (toque duplo) | Uma remessa só; a tela volta para Membros | `select count(*) from group_monthly_invitees where group_id='$GID'` = `4`; `envios` nunca passa de 4 mensagens para os 4 números | ☐ | ☐ |

## V2 · CONV-012 · A fila de envio [MULTI] [WhatsApp: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V2.1 | ORG | Logo depois de V1.17, abrir "Membros e permissões" | Chip `Convidados · 4` ao lado de `Pendentes`; seção `Convidados` com as linhas `Na fila` passando a `Aguardando` à medida que saem; o número de `Todos` é o mesmo de antes do envio | — | ☐ | ☐ |
| V2.2 | terminal | Rodar `envios` a cada 5 s durante cerca de 1 minuto; no fim, `intervalos` | 4 mensagens, uma por vez, na ordem `5511900000004`, `5511900000020`, `5511900000010`, `5511900000011`; cada texto traz o nome da pessoa, `QA Org <data>`, `QA Vôlei <data>`, `?saqz_invite=` e a frase final; cada intervalo entre 5 e 15 segundos | `intervalos` com 3 valores, todos entre `5.0` e `15.0` | ☐ | ☐ |
| V2.3 | ORG | Reabrir Membros → Convidados | 4 linhas `Aguardando`; texto `O link de mensalistas vale até <hoje + 7 dias>. Cada envio renova o prazo.`; botões `Reenviar para todos` e `Invalidar link` | — | ☐ | ☐ |
| V2.4 | ADM, ATL | ADM: Membros. ATL: Membros | ADM vê `Convidados · 4` com as mesmas 4 linhas e estados; ATL não vê a seção nem o chip | — | ☐ | ☐ |
| V2.5 | ORG | Tocar numa linha `Aguardando` | Folha com `Reenviar convite`, `Copiar link` e `Remover da lista` | — | ☐ | ☐ |

## V3 · CONV-011 · O link [CRÍTICO] [MULTI]

Anotar `COD_MS` com R-CÓDIGO-MS (`codigo 11900000004`).

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V3.1 | CV1 | Entregar o link com `COD_MS` (R-ENTREGAR) | Cartão de `QA Vôlei <data>` dizendo que entra **como mensalista**, sem `precisa de aprovação`, e o botão `Entrar no grupo` | R-PRÉVIA mostra o campo do tipo do convite (nome a fechar) | ☐ | ☐ |
| V3.2 | CV1 | `Entrar no grupo` → cadastro de atleta com apelido `Atl3` → "Salvar meu perfil" | `Você entrou no QA Vôlei <data>` e depois o grupo | `select role, membership_type, active, mensalista_since::date = current_date from group_memberships where user_id='$(uid atl3)' and group_id='$GID'` = `ATHLETE\|MENSALISTA\|t\|t` | ☐ | ☐ |
| V3.3 | ORG | Reabrir Membros | `QA Atl3 <data>` em `Todos` como mensalista; a linha dele sumiu de Convidados; chip `Convidados · 3` | `select count(*) from group_monthly_invitees where group_id='$GID' and phone like '%900000004'` = `0` (a linha foi apagada, não só escondida) | ☐ | ☐ |
| V3.4 | CV2 | Entregar o link (mesmo `COD_MS`) à conta de CV2, cujo celular é `11900000005` | Entra como mensalista, como CV1 | `membership_type` de CV2 = `MENSALISTA` | ☐ | ☐ |
| V3.5 | ORG | Reabrir Membros | `Pessoa B` segue `Aguardando` (o telefone da lista é outro); `Convidados · 3` | `group_monthly_invitees` mantém a linha de `%900000020` | ☐ | ☐ |
| V3.6 | ORG | Tocar `Pessoa B` → `Remover da lista` | A linha some; `Convidados · 2`; o link segue valendo | `group_monthly_invitees` sem `%900000020`; R-PRÉVIA do `COD_MS` = `200` | ☐ | ☐ |
| V3.7 | ATL | Entregar o link (ATL é avulso do grupo) | Abre o grupo; sem cadastro novo | `select role, membership_type, mensalista_since::date = current_date from group_memberships where user_id='$(uid atl)' and group_id='$GID'` = `ATHLETE\|MENSALISTA\|t` | ☐ | ☐ |
| V3.8 | CV1 | Entregar o link outra vez (já é mensalista) | Abre o grupo, sem tela de erro e sem novo cadastro | linha de CV1 inalterada; `mensalista_since` igual ao de V3.2 | ☐ | ☐ |
| V3.9 | ORG, CV3 | ORG liga "Aprovar pedidos para entrar" (Convidar por link). CV3 (sem grupo e que **ninguém convidou**) abre o link | Entra **direto como mensalista**, sem pedido, mesmo com a aprovação ligada | `membership_type='MENSALISTA'`; `api org GET /api/groups/$GID/entry-requests` sem pedido de CV3 | ☐ | ☐ |
| V3.10 | CV3 | Grupo → "Sair do grupo". Entregar o **convite público** (`COD_PUB`); cancelar o pedido; depois entregar o link de mensalistas | Convite público: `aguardando aprovação` (quem saiu só volta com pedido). Link de mensalistas: entra direto como mensalista, sem pedido | `membership_type='MENSALISTA'`, `active` = `t`; sem pedido pendente | ☐ | ☐ |
| V3.11 | ORG | Desligar "Aprovar pedidos para entrar" | — | — | ☐ | ☐ |
| V3.12 | ORG, terminal | ORG troca o código do convite público (Convidar por link → "Trocar código"); anotar o novo `COD_PUB2` (R-CÓDIGO-PÚBLICO). R-PRÉVIA `atl` com o `COD_PUB` antigo e com `COD_MS` | O antigo: `Este link foi substituído`. `COD_MS`: `200` (o link de mensalistas segue valendo) | — | ☐ | ☐ |
| V3.13 | ORG, terminal | ORG desativa o convite público (Convidar por link → "Desativar" → confirmar). R-PRÉVIA do `COD_MS`. Depois "Gerar código"; anotar `COD_PUB3` | `COD_MS` segue `200` com o convite público desativado; o público volta com código novo | — | ☐ | ☐ |

## V4 · NOT-004 · Aviso a dono e admins [MULTI] [push: só produção] [WhatsApp: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V4.1 | ORG, ADM | Abrir a central de avisos | Avisos de entrada, um por pessoa: `QA Atl3 <data> entrou no QA Vôlei <data> como mensalista.` (V3.2), o de CV2 (V3.4), o de CV3 (V3.9 e de novo em V3.10), e `QA Atl <data> virou mensalista no QA Vôlei <data>.` (V3.7). Nenhum para V3.8 (CV1 já era mensalista) | — | ☐ | ☐ |
| V4.2 | CV1, CV2, CV3 | Abrir a central de avisos de cada um | Sem aviso da própria entrada | — | ☐ | ☐ |
| V4.3 | terminal | `envios \| grep 5511900000001` e `envios \| grep 5511900000003` | Para ORG **e** para ADM (números cadastrados, nenhum confirmado): uma mensagem por entrada ou promoção, com o mesmo texto do aviso. A entrada de V3.8 não gera mensagem | contagem de linhas para ORG = para ADM = número de avisos de V4.1 | ☐ | ☐ |
| V4.4 | ORG, CV4 | R-SEM-MENSALIDADE (com o link vivo). CV4 (conta sem grupo) abre o link | CV4 **entra como avulso**, sem recusa; ORG e ADM recebem `QA Atl6 <data> entrou no QA Vôlei <data> como avulso, porque o grupo está sem mensalidade padrão.`; `envios` mostra o mesmo texto para ORG | `membership_type='AVULSO'` para CV4 | ☐ | ☐ |
| V4.5 | CV4 | Abrir o link de novo (já é avulso, grupo ainda sem mensalidade padrão) | Abre o grupo; continua avulso | `membership_type='AVULSO'`; central de ORG sem aviso novo | ☐ | ☐ |
| V4.6 | terminal | R-COM-MENSALIDADE | — | `monthly_fee_cents` = `8000` | ☐ | ☐ |
| V4.7 | ORG, CV3 | ORG: nas preferências de notificação do Perfil, desligar "Avisos dos grupos". CV3: "Sair do grupo" e abrir o link de mensalistas de novo | CV3 entra como mensalista; na central de ORG o aviso aparece; `envios` **sem** mensagem nova para ORG (a chave vale também para o WhatsApp deste aviso) | — | ☐ | ☐ |
| V4.8 | ORG | Religar "Avisos dos grupos" | — | — | ☐ | ☐ |
| V4.9 | banco, CV3 | R-ZERAR. R-SEM-TELEFONE `atl2` (ADM sem telefone cadastrado). CV3: "Sair do grupo" e abrir o link de mensalistas de novo | CV3 entra como mensalista; ORG e ADM recebem o aviso na central; `envios` mostra a mensagem para ORG (`5511900000001`) e **nenhuma** para ADM | `envios \| grep 5511900000003` vazio | ☐ | ☐ |

## V5 · SAI-005 · Limite do plano [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V5.0 | CV1, CV2, CV3 | "Sair do grupo", os três | Início sem grupo nos três | `active` = `f` para os três | ☐ | ☐ |
| V5.1 | banco + ORG | R-LIMITE-LIGAR (plano com limite de 25 atletas e grupo cheio). R-ZERAR. ORG: "Convidar mensalista" → adicionar `Pessoa F` `(11) 90000-0013` | Aviso `Seu grupo chegou ao limite de pessoas` com `O plano atual comporta 25 atletas e você já tem 25. Para colocar mais gente, faça o upgrade do plano.`; botão `Ver planos`; `Enviar` desligado com `Faça o upgrade ou libere vagas para enviar.` | `envios` vazio | ☐ | ☐ |
| V5.2 | ORG | Tocar `Ver planos` | Abre a escolha de plano | — | ☐ | ☐ |
| V5.3 | banco + ORG | R-VAGAS 2 (23 atletas). ORG: lote de 4 pessoas (`11900000014` a `11900000017`) | `Faltam vagas para esse lote`: `O plano atual comporta mais 2 atletas e você está convidando 4. Para colocar mais gente, faça o upgrade do plano.`; `Tire 2 pessoas da lista ou faça o upgrade para enviar.`; nada é enviado | `envios` vazio | ☐ | ☐ |
| V5.4 | ORG | Tirar `11900000016` e `11900000017` da lista e enviar as 2 que sobram | `Enviar 2 convites` liberado; as 2 mensagens saem | `envios` com 2 linhas | ☐ | ☐ |
| V5.5 | CV1, CV2, CV3 | Com 2 vagas livres e os convidados `Aguardando` sem reservar vaga: CV1 entrega o link; depois CV2; depois CV3 | CV1 e CV2 entram como mensalistas. CV3 vê `O grupo atingiu o limite de participantes` e `Este grupo atingiu o limite de atletas do plano. Não há fila de espera.`; nada novo na central de ORG por causa do bloqueio | `select count(*) from group_memberships where group_id='$GID' and active` = `25` | ☐ | ☐ |
| V5.6 | banco | R-LIMITE-DESLIGAR | — | `select count(*) from subscriptions` = 0 | ☐ | ☐ |

## V6 · CONV-011 · Validade, invalidar e reenviar [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V6.1 | banco + CV3 | R-ENVELHECER 1. CV3 (sem grupo) entrega o link (`COD_MS`) | `Este convite venceu`, com a data; não entra | R-PRÉVIA `atl` do `COD_MS` = erro de link vencido | ☐ | ☐ |
| V6.2 | ORG | Membros → Convidados | Aviso `O link venceu` com `Ninguém mais entra com ele. Reenvie para gerar um link novo e mandar de novo.` e o botão `Reenviar para todos`; as 4 linhas de quem não entrou (`11900000010`, `11900000011`, `11900000014`, `11900000015`) aparecem como `Venceu` | — | ☐ | ☐ |
| V6.3 | ORG | R-ZERAR. Tocar `Reenviar para todos` | As 4 linhas voltam a `Na fila` e depois `Aguardando`; saem 4 mensagens, com um **código novo** | `codigo 11900000010` = `COD_MS2`, diferente de `COD_MS`; R-PRÉVIA do `COD_MS` = erro (o velho morreu) | ☐ | ☐ |
| V6.4 | CV3 | Entregar o link novo (`COD_MS2`) | Entra como mensalista | `membership_type='MENSALISTA'` | ☐ | ☐ |
| V6.5 | ORG | Convidar `Pessoa G` `(11) 90000-0018`, com o link ainda vivo | A mensagem leva o **mesmo** código `COD_MS2`; a validade volta a 7 dias | `expires_at` aproximadamente agora + 7 dias; `codigo 11900000018` = `COD_MS2` | ☐ | ☐ |
| V6.6 | ORG | Tocar `Invalidar link` → na folha `Invalidar o link?` (`Quem ainda não entrou precisará receber o link de novo. Quem já entrou continua no grupo.`) tocar `Cancelar` | Nada muda | R-PRÉVIA do `COD_MS2` = `200` | ☐ | ☐ |
| V6.7 | ORG | `Invalidar link` → confirmar | Link velho morto na hora; quem ainda não entrou fica `Aguardando`; quem já entrou segue no grupo; o convite público segue valendo | R-PRÉVIA do `COD_MS2` = `Este link foi substituído`; R-PRÉVIA do `COD_PUB3` = `200` | ☐ | ☐ |
| V6.8 | CV4 | "Sair do grupo" e entregar o link `COD_MS2` | `Este link foi substituído` e `Peça um novo ao organizador.`; não entra | CV4 sem membership ativa | ☐ | ☐ |
| V6.9 | ORG | R-ZERAR. `Reenviar para todos` | Código novo `COD_MS3`; as 5 linhas (`...10`, `...11`, `...14`, `...15`, `...18`) voltam à fila e saem com o link novo | `codigo` de cada uma = `COD_MS3` | ☐ | ☐ |
| V6.10 | ORG | **[mock+]** R-ZERAR. Convidar `Pessoa Sem` `(11) 90000-0090` | A linha fica `Sem WhatsApp` e **não** aparece em `envios`; tocar nela mostra `Copiar link` e `Remover da lista` | `envios` vazio | ☐ | ☐ |
| V6.11 | ORG | **[mock+]** Convidar `Pessoa J` `(11) 90000-0019` e `Pessoa K` `(11) 90000-0040`; com a segunda ainda `Na fila`, tocar `Reenviar para todos` | Entram só as linhas `Aguardando`, `Falhou`, `Pausado` e `Venceu`; `Pessoa K` (`Na fila`) e `Pessoa Sem` (`Sem WhatsApp`) ficam de fora; nenhuma mensagem em dobro | `envios` com no máximo 1 mensagem por número para `...19` e `...40`; nada para `...90` | ☐ | ☐ |
| V6.12 | ORG | Remover uma linha `Aguardando` | A linha some; o link segue valendo | R-PRÉVIA do `COD_MS3` = `200` | ☐ | ☐ |
| V6.13 | ORG | **[mock+]** Remover as linhas `Aguardando` até sobrar só `Pessoa Sem` (`Sem WhatsApp`) | O botão `Reenviar para todos` não aparece | — | ☐ | ☐ |

## V7 · CONV-012 · A fila, falhas e pausa [MULTI] [WhatsApp: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V7.1 | ORG, ADM | R-ZERAR. ORG cria `QA Vôlei B <data>` com mensalidade `80,00` e dia 10 (se não existir) e envia 3 pessoas (`11900000030` a `11900000032`); em menos de 10 s, ADM envia 3 pessoas (`11900000033` a `11900000035`) no grupo principal | 6 mensagens, nunca duas ao mesmo tempo, na ordem em que os lotes chegaram; cada intervalo entre 5 e 15 segundos, mesmo entre grupos diferentes | `intervalos` com 5 valores entre `5.0` e `15.0` | ☐ | ☐ |
| V7.2 | ORG | **[mock+]** R-ZERAR. Convidar `Pessoa H` `(11) 90000-0091` | O log mostra **3 tentativas** para `5511900000091`, com intervalo crescente; depois a linha fica `Falhou` | `envios` com 3 linhas para esse número | ☐ | ☐ |
| V7.3 | ORG | **[mock+]** Tocar na linha `Falhou` | Folha com `Reenviar convite`, `Copiar link` e `Remover da lista`; o texto da folha diz `não saiu depois de 3 tentativas` | — | ☐ | ☐ |
| V7.4 | ORG | **[mock+]** R-ZERAR. Convidar, nesta ordem, `11900000092`, `11900000093`, `11900000094`, `11900000095` e `Pessoa I` `(11) 90000-0041` | Depois da terceira falha a fila **pausa**: `...95` e `Pessoa I` ficam `Pausado`; aparece `Envio pausado` com `O WhatsApp do Saqz falhou 3 vezes seguidas. Tentamos de novo às <hora>. Se preferir, copie o link e mande você mesmo.` e o botão `Copiar link`. Registrar se a pausa veio depois de 3 mensagens ou de 3 tentativas | — | ☐ | ☐ |
| V7.5 | ORG | **[mock+]** Com a fila pausada, enviar `Pessoa L` `(11) 90000-0044` no `QA Vôlei B <data>` | A linha fica `Pausado` também: a pausa vale para todos os grupos | — | ☐ | ☐ |
| V7.6 | terminal | **[mock+]** Esperar 10 minutos desde a pausa | A fila tenta de novo com **um** envio; como o pendente (`...95`) falha, pausa por mais 10 minutos; `Pessoa I` e `Pessoa L` seguem `Pausado`. Registrar o estado da linha `...95` | `envios` mostra a tentativa de teste | ☐ | ☐ |
| V7.7 | terminal | **[mock+]** Esperar mais 10 minutos | Nova tentativa; o próximo pendente passa (`Pessoa I`), a fila volta ao normal e `Pessoa L` sai depois | `envios` com `5511900000041` e `5511900000044` | ☐ | ☐ |
| V7.8 | ORG | R-ZERAR. Lote de 4 pessoas novas (`11900000045` a `11900000048`). Assim que a primeira sair, reiniciar só o backend (`stack.sh`, sem tocar no banco) | Ao subir, os `Na fila` voltam sozinhos à fila e saem; a que já estava `Aguardando` continua assim, **sem** segunda mensagem | `envios` com 1 mensagem por número (4 no total) | ☐ | ☐ |

## V10 · CONV-007 · Aceitar o pedido como mensalista ou avulso (DEB-112, só depois da entrega) [CRÍTICO] [MULTI] [push: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V10.1 | terminal, ORG, CV1, CV2, CV3 | R-COM-MENSALIDADE. ORG liga "Aprovar pedidos para entrar" (Convidar por link). CV1, CV2 e CV3: "Sair do grupo" (CV4 já está fora desde V6.8) | Início sem grupo nos quatro | `monthly_fee_cents` = `8000`; `active` = `f` para CV1, CV2 e CV3 | ☐ | ☐ |
| V10.2 | CV1, CV2, CV3, CV4 | Entregar o convite público (`COD_PUB3`) a cada um e "Pedir pra entrar" (cadastro de atleta se pedir) | Pedido pendente nos quatro | `select count(*) from group_entry_requests where group_id='$GID'` = `4` | ☐ | ☐ |
| V10.3 | ORG | Membros → chip `Pendentes · 4` → `Aceitar` no pedido de CV1 | Abre a folha `Aceitar como` com `Avulso` marcado e `Mensalista` | — | ☐ | ☐ |
| V10.4 | ORG, terminal | Fechar a folha sem confirmar. Em seguida: `api atl POST /api/groups/$GID/entry-requests/$(uid atl5)/approve -d '{"membershipType":"MENSALISTA"}'` | Nada muda na lista (4 pedidos); a API devolve `403` | `select count(*) from group_entry_requests where group_id='$GID'` = `4` | ☐ | ☐ |
| V10.5 | ORG | `Aceitar` em CV1 → `Mensalista` → confirmar | O pedido sai de Pendentes; CV1 aparece em Membros como mensalista | `select role, membership_type, active, mensalista_since::date = current_date from group_memberships where user_id='$(uid atl3)' and group_id='$GID'` = `ATHLETE\|MENSALISTA\|t\|t` | ☐ | ☐ |
| V10.6 | CV1 | Central de avisos | [push: só produção] aviso `Você entrou no QA Vôlei <data> como mensalista.` | `select m.body from group_notifications n join group_messages m on m.id=n.message_id where n.recipient_id='$(uid atl3)' order by n.sequence desc limit 1` = o texto acima | ☐ | ☐ |
| V10.7 | ORG | `Aceitar` em CV2 → deixar `Avulso` marcado → confirmar | CV2 entra como avulso | `membership_type='AVULSO'` para CV2 | ☐ | ☐ |
| V10.8 | CV2 | Central de avisos | [push: só produção] aviso `Você entrou no QA Vôlei <data>.` (sem "como mensalista") | `body` da última notificação de CV2 sem "como mensalista" | ☐ | ☐ |
| V10.9 | ORG, banco | Abrir a folha `Aceitar como` em CV4 e, sem fechá-la, R-SEM-MENSALIDADE; escolher `Mensalista` e confirmar | Recusado com `Defina o valor e o dia da mensalidade do grupo`; o pedido continua na lista | pedido de CV4 = 1 linha; CV4 sem membership ativa | ☐ | ☐ |
| V10.10 | ORG | Com o grupo ainda sem mensalidade padrão, `Aceitar` em CV4 | Aceita direto, sem folha, como avulso | `membership_type='AVULSO'` para CV4 | ☐ | ☐ |
| V10.11 | terminal | R-COM-MENSALIDADE. `api org POST /api/groups/$GID/entry-requests/$(uid atl5)/approve` **sem corpo** (como os apps 1.0.8 e 1.0.9) | Sucesso; CV3 entra como avulso | `membership_type='AVULSO'` para CV3 | ☐ | ☐ |

## V8 · CONV-010 · Dados dos convidados [MULTI]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V8.1 | banco + ORG | R-ENVELHECER 29 (link vencido há 29 dias) e rodar o job de retenção (o job diário; se não der para esperar, chamar a rotina de retenção ou registrar BLOQUEADO) | As linhas de quem não entrou **seguem** (menos de 30 dias): ORG as vê como `Venceu` | `select count(*) from group_monthly_invitees where group_id='$GID'` > 0 | ☐ | ☐ |
| V8.2 | banco + ORG | R-ENVELHECER 31 e rodar o job de retenção | As linhas de quem não entrou são apagadas; ORG vê o cartão `Mensalistas pelo WhatsApp` no lugar da seção | `select count(*) from group_monthly_invitees where group_id='$GID'` = `0` | ☐ | ☐ |
| V8.3 | ORG, banco | Convidar 2 pessoas (`11900000049` e `11900000050`); `Invalidar link`; R-ENVELHECER-INVALIDADO 31 e rodar o job | Linhas apagadas, como em V8.2 | `group_monthly_invitees` = `0` | ☐ | ☐ |
| V8.4 | ORG | Excluir o `QA Vôlei B <data>` (que ainda tem os convidados de V7.1): Editar grupo → lixeira → "Excluir grupo" | O grupo some | `group_monthly_invitees` sem linhas do grupo B; R-PRÉVIA do código vivo dele = `Grupo excluído` ou `Convite inválido` | ☐ | ☐ |

## V9 · CONV-010 · WhatsApp indisponível [WhatsApp: só produção]

| # | Aparelho | Faça | Espere | Banco | iOS | Android |
| --- | --- | --- | --- | --- | --- | --- |
| V9.1 | terminal | Subir o backend com `SAQZ_NOTIFICATIONS_WHATSAPP_ENABLED=false`; R-ZERAR | `api org GET /api/whatsapp/availability` com `enabled` falso | — | ☐ | ☐ |
| V9.2 | ORG | Num grupo com mensalidade padrão, "Convidar mensalista" → adicionar 2 pessoas | Aviso `Não dá para enviar pelo Saqz agora` com `O WhatsApp do Saqz está indisponível. Copie a mensagem e mande você mesmo, no privado de cada pessoa.`; cartões `Mensagem` e `Quem precisa receber`; botões `Copiar mensagem` e `Copiar só o link`; não há `Enviar` | `envios` vazio; nenhuma linha na fila | ☐ | ☐ |
| V9.3 | ORG | Tocar `Copiar só o link` | Toast de confirmação; o link copiado é o de mensalistas (`?saqz_invite=`) | R-PRÉVIA do código copiado = `200` | ☐ | ☐ |

## Rodadas

Rodar o roteiro inteiro duas vezes, cada uma com `stack.sh reset` e apps limpos. Marcar o resultado na coluna da plataforma do aparelho do papel que age na linha (se a linha tiver dois, a do primeiro citado).

| Papel | Rodada 1: ORG no iPhone, ATL no Android | Rodada 2: ORG no Android, ATL no iPhone |
| --- | --- | --- |
| ORG | iPhone 17 (`BAB08A28-4E8C-4AA3-BF6A-54904566BF0F`) | `Saqz_QA_A` |
| ATL | `Saqz_QA_A` | iPhone 17 |
| ADM | iPhone 17e (`F22B2FF2-494E-4CF3-BD18-FE48ECE5A85B`) | `Saqz_QA_B` |
| CV1, CV2, CV3 e CV4 | `Saqz_QA_B` | iPhone 17e |

Link para cada plataforma: iPhone com `xcrun simctl openurl <udid> 'saqz://links.saqz.app/?saqz_invite=<código>'`; Android com `adb -s <id> shell am start -a android.intent.action.VIEW -d 'https://links.saqz.app/?saqz_invite=<código>' app.saqz`.

Relate também o tempo total, a divergência entre o protótipo e o app e qualquer passo em que precisou usar adb/simctl no lugar do mobile-mcp.
