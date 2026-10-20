# Rodada 20/10/2026 · Roteiro 10 (convite de mensalistas), validação enxuta

Executor: sessão Claude no worktree `roteiro-10-convite`, só registro (nada julgado nem consertado).

- **Commit testado:** `3c211a9d3` (branch `bruno-halmeida/roteiro-10-convite`).
- **Backend:** bootJar deste worktree (`bootstrap-0.1.0-SNAPSHOT.jar`, construído 20/10 17:58), Flyway na versão 179, WhatsApp ligado contra o Uazapi falso (`SAQZ_MULTIDEV_WHATSAPP_MOCK=1`); `GET /api/whatsapp/availability` = `{"enabled":true}`.
- **App:** devDebug Android `1.0.10 (26)` (rótulo do Perfil) e `SaqzIOS.app` Debug, ambos de `builds.sh` neste commit, apontando para `127.0.0.1:18080` / `10.0.2.2:18080`.
- **Aparelhos:** ORG = iPhone 17 (`BAB08A28-…`), ADM = iPhone 17e (`F22B2FF2-…`), ATL = `Saqz_QA_A`, CV1 a CV4 = `Saqz_QA_B`. Uma rodada (ORG no iPhone, ATL no Android).
- `<tag>` = `2010`, `<data>` = `20/10`. Grupo `QA Vôlei 20/10` (`f39e91bf-5fab-4d3b-805e-32d693875f7c`). Códigos: `COD_PUB` = `T9THTAZS`, `COD_MS` = `6VJQY8FX`, `COD_MS2` = `J5FEKFJS`.

## Preparação (comandos e resultado)

| Comando | Resultado |
| --- | --- |
| `adb devices; xcrun simctl list devices booted` | nenhum aparelho ligado (só depois de exportar `DEVELOPER_DIR` e o `platform-tools` no PATH) |
| `stack.sh status` antes do reset | Postgres e Mailpit de pé, backend/firebase/uazapi parados (estado parcial da tentativa anterior) |
| `SAQZ_MULTIDEV_WHATSAPP_MOCK=1 stack.sh reset` | `banco e contas apagados` |
| `SAQZ_MULTIDEV_WHATSAPP_MOCK=1 stack.sh up` | backend e firebase rodando, **uazapi: parado** — `uazapi-falso.log`: `EADDRINUSE 127.0.0.1:18443`. A porta estava com um `node uazapi-falso.mjs` de 19/10 23:15 (cwd num scratchpad de outra sessão em `~/Projetos/saqz`, nenhum aparelho ligado). **Única correção de ambiente:** `kill 96540` e `stack.sh up` de novo → `uazapi: rodando`, log em `~/.saqz-multidev/logs/uazapi-mock.jsonl` |
| `aparelhos.sh subir && builds.sh && aparelhos.sh instalar` | ok; `app.saqz instalado` nos 4 |
| dados dos apps | Android: `pm clear app.saqz`. iOS: o app reinstalado ainda abria `Não foi possível carregar sua conta.` (sessão do Firebase no Keychain); resolvido com `xcrun simctl keychain <udid> reset` nos dois simuladores |

## Tabela

| # | Resultado | O que apareceu | Evidência |
| --- | --- | --- | --- |
| P1 | PASSOU | backend 18080, Uazapi falso respondendo (`/instance/status` conectado), `select count(*) from access_users` = `0` (depois da correção acima) | — |
| P2 | PASSOU | `Grupo criado!`; `monthly_fee_cents, monthly_due_day` = `\|` | — |
| P3 | PASSOU | 7 contas pelo "Criar conta ›", Início com `Tenho um convite`; contagem = `7` | — |
| P4 | PASSOU | ATL e ADM pelo link público: `Você entrou no QA Vôlei 20/10` (o link leva direto ao cadastro de atleta, sem cartão de prévia); ADM com `Administrador`; banco `AVULSO\|ATHLETE`, `AVULSO\|ADMIN` | — |
| P5 | PASSOU | `COD_PUB` = `T9THTAZS` (8); `{"enabled":true}`; log zerado; `verified_phone` não nulo = `0` | — |
| V1.1 | PASSOU | cartão `Mensalistas pelo WhatsApp` com o texto esperado e `Convidar mensalista`; chips `Todos · 3`, `Administradores · 1`, `Pendentes · 0`, sem `Convidados` | — |
| V1.2 | PASSOU | ATL vê "Membros e permissões" só com `Todos · 0` e `Administradores · 0`, sem botão, seção ou chip; API `403` (`ACCESS_FORBIDDEN`). Obs.: `Todos · 0` para o atleta | — |
| V1.3 | PASSOU | `Defina o valor e o dia da mensalidade do grupo`, `Quem entra como mensalista paga esse valor. Configure uma vez e volte para convidar.`, `Configurar mensalidade`; sem `Adicionar pessoa` | — |
| V1.4 | PASSOU | cartão `Mensalidade` com o texto esperado; o botão é `Salvar alterações` (não "Salvar"); salvou e voltou para Convidar mensalista; banco `8000\|10`. Obs.: Editar grupo abre no topo, o cartão fica no fim da tela; o dia já vinha em `10` | — |
| V1.5 | PASSOU | contador parou em `28` (`Dia seguinte` desligado) e em `1` (`Dia anterior` desligado); saiu sem salvar; banco `10` | — |
| V1.6 | PASSOU | `Quem vai receber`, `Ninguém na lista ainda`, texto de apoio, `R$ 80,00 por mês`, `Vence todo dia 10, para todos que entrarem.`, `Enviar convites` desligado com `Adicione pelo menos uma pessoa para enviar.` | 2026-10-20-convite-V1.6.png |
| V1.7 | PASSOU | ADM vê a mesma tela liberada | — |
| V1.8 | PASSOU | `Digite o nome da pessoa.` e `Faltam dígitos. Use o DDD e os 9 dígitos, por exemplo (11) 98765-4321.` | — |
| V1.9 | PASSOU | `(11) 88888-7777` e `(10) 99999-9999`: `Esse número não parece um celular do Brasil. Confira o DDD e o 9 do começo.` | — |
| V1.10 | PASSOU | `QA Atl2 20/10 já está no grupo como avulso. Se promover, vira mensalista agora e não recebe mensagem.`; botão `Promover a mensalista`, sem `Adicionar` | — |
| V1.11 | PASSOU | toast `QA agora é mensalista. Nenhuma mensagem foi enviada.` (primeiro nome "QA", não o nome completo); lista vazia; banco `MENSALISTA\|ADMIN`; `envios` vazio | — |
| V1.12 | FALHOU | aviso certo (`QA Atl2 20/10 já é mensalista do grupo. Não precisa de convite.`), mas o botão existe: `Button label="Adicionar" name="monthly-invite-sheet-add" … disabled` | 2026-10-20-convite-V1.12.png |
| V1.13 | PASSOU | folha fica aberta em "Adicionar e cadastrar outra" e fecha em "Adicionar"; 4 pessoas, `Enviar 4 convites`, `Os convites saem um por vez, com uns segundos entre eles. 4 pessoas levam menos de 1 min.` Obs.: um toque meu fora da folha a fechou depois de CV1; reabri por "Adicionar outra pessoa" para Pessoa B | 2026-10-20-convite-V1.13.png |
| V1.14 | PASSOU | `Esse número já está na lista (Pessoa C).`; `Adicionar` e `Adicionar e cadastrar outra` desligados | — |
| V1.15 | FALHOU | remover Pessoa D: toast `Pessoa saiu da lista.` (primeiro nome) com `Desfazer`, botão vira `Enviar 3 convites`. O toque em `Desfazer` (264,758 106×52) **enviou o lote**: a tela foi para `Ninguém na lista ainda` + `3 convites na fila. Acompanhe em Membros.`; Pessoa D não voltou. O toast ocupa o mesmo lugar do botão `Enviar 3 convites` (16,776 370×52) | 2026-10-20-convite-V1.15.png, mock-envio-acidental.jsonl |
| V1.16 | PASSOU | (feito depois, com Pessoa D sozinha na lista) `E aí, Pessoa! 🏐 O QA Org 20/10 te colocou na lista de mensalistas do QA Vôlei 20/10 aqui no Saqz. Entra por aqui e confirma presença nos jogos direto pelo app: https://links.saqz.app/?saqz_invite=6VJQY8FX` / `Se não era pra você, é só ignorar.` Obs.: a prévia usa o primeiro nome ("Pessoa"); as mensagens enviadas usam o nome inteiro ("Pessoa D") | — |
| V1.17 | FALHOU | R-ZERAR e toque duplo em `Enviar 1 convite` (Pessoa D): uma remessa só (`envios` = 1 mensagem para `5511900000011`), `group_monthly_invitees` = `4`. Mas a tela **não volta para Membros**: fica em Convidar mensalista com `1 convite na fila. Acompanhe em Membros.` | — |
| V2.1 | PASSOU | `Convidados · 4` ao lado de `Pendentes · 0`; seção `Convidados 4` com 4 linhas `Aguardando`; `Todos · 3` igual a antes. `Na fila` não chegou a ser visto (o único envio saiu antes de abrir) | 2026-10-20-convite-V2.1.png |
| V2.2 | PASSOU | ordem `5511900000004`, `5511900000020`, `5511900000010` (lote de 3, envio acidental do V1.15) e `5511900000011` (lote do V1.17); textos com nome, `QA Org 20/10`, `QA Vôlei 20/10`, `?saqz_invite=6VJQY8FX` e a frase final; `intervalos` no lote de 3 = `[9.2, 10.7]`. Não houve lote único de 4 | mock-envio-acidental.jsonl |
| V2.3 | PASSOU | 4 `Aguardando`; `O link de mensalistas vale até 27 de out. Cada envio renova o prazo.`; `Reenviar para todos` e `Invalidar link`; `expires_at` = `2026-10-27 21:26:40` | — |
| V2.4 | PASSOU | ADM vê `Convidados · 4` com as mesmas linhas; ATL só `Todos · 0` / `Administradores · 0` | — |
| V2.5 | PASSOU | folha da linha (Pessoa C): `Reenviar convite`, `Copiar link`, `Remover da lista` | — |
| V3.1 | FALHOU | sem cartão nem `Entrar no grupo`: o link levou direto a `Cadastro de atleta` / `Você entrou no QA Vôlei 20/10` (já como membro). R-PRÉVIA: `200`, campo `"inviteKind":"MONTHLY"`, `"entryRequiresApproval":false` | 2026-10-20-convite-V3.1.png |
| V3.2 | PASSOU | apelido `Atl3` → "Salvar meu perfil" → grupo; banco `ATHLETE\|MENSALISTA\|t\|t` | — |
| V3.3 | PASSOU | `Todos · 4`, `Convidados · 3`, linha de CV1 fora de Convidados; contagem `%900000004` = `0` | 2026-10-20-convite-V3.3.png |
| V3.4 | PASSOU | CV2 (celular `11900000005`) entrou; `MENSALISTA` | — |
| V3.5 | PASSOU | `Pessoa B` segue `Aguardando`; `Convidados · 3`; linha `%900000020` no banco | — |
| V3.6 | PASSOU | `Remover da lista` → linha some, `Convidados · 2`, toast `Pessoa saiu da lista.`; banco sem `%900000020`; R-PRÉVIA `200` | — |
| V3.7 | FALHOU | banco `ATHLETE\|MENSALISTA\|t` (certo), mas a tela não abre o grupo: mostra `Cadastro de atleta` / `Você entrou no QA Vôlei 20/10` com o apelido `Atl` preenchido | 2026-10-20-convite-V3.7.png |
| V3.8 | FALHOU | banco inalterado (`mensalista_since` = `2026-10-20`, `updated_at` do V3.2), sem erro, mas de novo `Cadastro de atleta` / `Você entrou no QA Vôlei 20/10` (apelido `Atl3`) em vez do grupo | 2026-10-20-convite-V3.8.png |
| V4.1 | FALHOU | ORG (tela) e ADM (banco): `QA Atl3 20/10 entrou no QA Vôlei 20/10 como mensalista.`, `QA Atl4 20/10 entrou no QA Vôlei 20/10 como mensalista.` e **`Atl virou mensalista no QA Vôlei 20/10.`** (apelido do grupo, não `QA Atl 20/10`); nada para V3.8. Avisos de CV3 não se aplicam (V3.9/V3.10 fora do escopo) | 2026-10-20-convite-V4.1.png |
| V4.2 | PASSOU | CV1: `Nenhuma notificação.`; CV2: nenhuma linha em `group_notifications` | — |
| V4.3 | PASSOU | 3 mensagens para `5511900000001` e 3 para `5511900000003`, texto `Saqz · QA Vôlei 20/10 / <aviso>`; nada para V3.8 | — |
| V4.4 | PASSOU | CV4 entrou como avulso sem recusa; ORG e ADM: `QA Atl6 20/10 entrou no QA Vôlei 20/10 como avulso, porque o grupo está sem mensalidade padrão.`; o mesmo em `envios` para ORG e ADM; `AVULSO` | — |
| V4.5 | FALHOU | banco `AVULSO`, central de ORG sem aviso novo (5 = 5), mas a tela mostra `Cadastro de atleta` / `Você entrou no QA Vôlei 20/10` em vez de abrir o grupo | — |
| V4.6 | PASSOU | `monthly_fee_cents` = `8000` | — |
| V6.1 | PASSOU | CV3: `Este convite venceu`, `O link do grupo valeu até 19/10/2026. Peça um novo pra quem te convidou.`, `Pedir novo convite`; R-PRÉVIA `410 INVITE_EXPIRED` | — |
| V6.2 | PASSOU | `O link venceu`, `Ninguém mais entra com ele. Reenvie para gerar um link novo e mandar de novo.`, `Reenviar para todos`; linhas `...10` e `...11` como `Venceu` (as de `...14`/`...15` não existem: V5 fora do escopo) | 2026-10-20-convite-V6.2.png |
| V6.3 | PASSOU | linhas viram `Na fila` e `Aguardando`, toast `2 convites de volta na fila.`; 2 mensagens com código novo `J5FEKFJS`; R-PRÉVIA do `6VJQY8FX` = `404 INVITE_INVALID` | — |
| V6.4 | PASSOU | CV3 com `J5FEKFJS`: entrou, `MENSALISTA` | — |
| V6.5 | PASSOU | Pessoa G recebeu `J5FEKFJS`; `expires_at` = `2026-10-27 21:37:50` (agora + 7 dias) | — |
| V6.6 | PASSOU | folha `Invalidar o link?` com `Quem ainda não entrou precisará receber o link de novo. Quem já entrou continua no grupo.` → `Cancelar`; R-PRÉVIA `200` | — |
| V6.7 | FALHOU | tela: `Link invalidado.`, 3 linhas `Aguardando`, membros intactos. R-PRÉVIA do `J5FEKFJS` = `{"code":"INVITE_INVALID",…,"status":404}` (esperado `Este link foi substituído`); R-PRÉVIA do público `T9THTAZS` = `200` (COD_PUB, porque V3.13 não rodou) | 2026-10-20-convite-V6.7.png |
| V6.8 | PASSOU | CV4 saiu do grupo; link `J5FEKFJS`: `Este link foi substituído` e `Peça um novo ao organizador.`; sem membership ativa | — |

**Contagem:** 40 PASSOU, 9 FALHOU, 0 BLOQUEADO (49 passos).

## NÃO EXECUTADOS

V3.9 a V3.13, V4.7 a V4.9, V5 (V5.0 a V5.6), V6.9 a V6.13, V7 (V7.1 a V7.8), V8 (V8.1 a V8.4), V9 (V9.1 a V9.3) e V10 (V10.1 a V10.11), conforme o brief. Consequências no escopo: sem avisos de CV3 em V4.1, sem as linhas `...14`/`...15` em V6.2 e `COD_PUB3` trocado por `COD_PUB` em V6.7.

## Achados

### Do app ou do backend

1. **Desfazer do toast cai no botão de enviar** — V1.15. Esperado: `Desfazer` devolve Pessoa D à lista e o botão segue `Enviar 4 convites`. Obtido: o toque em `Desfazer` disparou o envio de 3 convites (`3 convites na fila. Acompanhe em Membros.`), e 3 mensagens saíram no mock. O toast (`monthly-invite-toast` 16,744 370×80, `Desfazer` 264,758) fica em cima de `monthly-invite-send` (16,776 370×52). Evidência: `2026-10-20-convite-V1.15.png`, `mock-envio-acidental.jsonl`.
2. **Link para quem já é membro mostra o cadastro de atleta** — V3.7, V3.8, V4.5. Esperado: abre o grupo, sem cadastro. Obtido: `Cadastro de atleta` / `Você entrou no QA Vôlei 20/10` toda vez, com o apelido já salvo; o banco fica certo (promoção em V3.7, nada muda em V3.8/V4.5). Evidência: `2026-10-20-convite-V3.7.png`, `…-V3.8.png`.
3. **Sem cartão de prévia nem "Entrar no grupo" no link de mensalistas** — V3.1 (e o mesmo no link público em P4). Esperado: cartão dizendo que entra como mensalista e o botão `Entrar no grupo`. Obtido: entra na hora e cai no cadastro de atleta. Evidência: `2026-10-20-convite-V3.1.png`.
4. **Aviso de promoção usa o apelido** — V4.1. Esperado: `QA Atl <data> virou mensalista no QA Vôlei <data>.` Obtido: `Atl virou mensalista no QA Vôlei 20/10.`, na central e no WhatsApp de ORG e ADM (os avisos de entrada usam o nome completo). Evidência: `2026-10-20-convite-V4.1.png`.
5. **Prévia de link invalidado devolve INVITE_INVALID** — V6.7. Esperado: `Este link foi substituído`. Obtido pela API: `404 INVITE_INVALID`; o app, porém, mostra `Este link foi substituído` em V6.8. Mesmo código no link velho depois do reenvio (V6.3, onde o roteiro só pedia "erro").
6. **Depois de enviar, a tela não volta para Membros** — V1.17. Esperado: volta para Membros. Obtido: fica em Convidar mensalista vazia com `1 convite na fila. Acompanhe em Membros.`
7. **Botão "Adicionar" aparece (desligado) para quem já é mensalista** — V1.12. Esperado: sem `Adicionar`. Obtido: `Adicionar` desligado; `Adicionar e cadastrar outra` some.

Divergências menores, anotadas nos PASSOU: toast de promoção e de remoção com o primeiro nome (`QA agora é mensalista…`, `Pessoa saiu da lista.`) e prévia da mensagem com `E aí, Pessoa!` enquanto a enviada traz o nome completo; botão `Salvar alterações` em vez de "Salvar"; ATL vê `Todos · 0` em Membros e permissões.

### De ambiente ou de execução

- Uazapi falso de outra sessão (19/10) segurando a porta 18443; encerrado (única correção de ambiente).
- iOS guardava a sessão do Firebase no Keychain mesmo reinstalando o app: `simctl keychain reset`.
- iOS: a folha `Nova pessoa` muda de posição enquanto o teclado anima; dois toques meus caíram no fundo e fecharam a folha (V1.10 e V1.13). Precisei listar duas vezes antes de cada toque.
- Android: `mobile_swipe` sobre o teclado aberto digitou texto no campo (deslizar para digitar); usei `adb shell input swipe` e `adb input tap` para rolar e salvar o cadastro de atleta. Troca de conta no `Saqz_QA_B` com `pm clear app.saqz` (o login guardava o e-mail anterior e o apagar parcial gerava e-mail inválido); um toque cego abriu a Política de privacidade no Chrome. `uiautomator dump` mostrou um diálogo antigo de "Process system isn't responding" que não estava na tela.
- Lotes do V1.17/V2.2 ficaram divididos (3 + 1) por causa do achado 1, então não houve um lote único de 4 para medir os 3 intervalos.

Passos em que usei adb/simctl no lugar do mobile-mcp: entrega de links (`am start`, `simctl openurl`), abrir o app (`simctl launch`, `monkey`), rolar e salvar o cadastro de atleta no Android, `pm clear` para trocar de conta.

## Tempo

Das 17:58 (reset do stack) às 18:40: cerca de 42 minutos de execução, mais o tempo de builds rodando em segundo plano.

## Análise da sessão principal (20/10)

Das 9 falhas, **2 são defeitos do app/backend** e as outras 7 são expectativa do roteiro que não bate com o comportamento (já corrigidas no roteiro 10) ou comportamento que já existe no convite público.

| Passo | Classificação | Decisão |
| --- | --- | --- |
| V1.15 | **Defeito, grave.** O toast "Desfazer" fica por cima do botão "Enviar N convites"; quando o toast some, o toque cai no botão de baixo e envia o lote pelo WhatsApp | Corrigir antes da 1.0.10 (C1 do brief de correções); retestar na segunda passada |
| V4.1 | **Defeito menor.** O aviso "virou mensalista" usa o apelido do grupo ("Atl") e os outros dois usam o nome da conta | Corrigir (C2) |
| V1.12 | Roteiro: o protótipo mantém "Adicionar" desligado | Roteiro ajustado |
| V1.17 | Roteiro: a tela fica em "Convidar mensalista" com "N convites na fila. Acompanhe em Membros." | Roteiro ajustado; comportamento aceito |
| V3.1 | Roteiro: conta logada vai direto ao cadastro, também no convite público; a prévia vem pela API (`inviteKind: MONTHLY`, conferido) | Roteiro ajustado |
| V3.7, V3.8, V4.5 | Quem já é membro e abre o link cai em "Cadastro de atleta". O resgate devolve só JOINED ou PENDING, então o convite público faz o mesmo | Roteiro ajustado; confirmar com C5.3 na segunda passada; melhoria possível, fora desta entrega |
| V6.7 | Roteiro: a API responde `INVITE_INVALID` (404); o texto "Este link foi substituído" é do app e foi visto em V6.8 | Roteiro ajustado |

Ficaram **NÃO EXECUTADOS**: V3.9 a V3.13, V4.7 a V4.9, V5, V6.9 a V6.13, V7, V8, V9 e V10 (DEB-112, ainda sem código nos aparelhos naquele momento). Pontos de ambiente: o Uazapi falso de uma sessão anterior ocupava a porta 18443; o Keychain do simulador iOS precisou de `simctl keychain reset`.

