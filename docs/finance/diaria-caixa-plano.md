# Plano técnico · valor por jogo obrigatório, diária retroativa, caixa novo e pagamento antecipado

Entrega única, **sem flag**, decidida pelo dono em 28 e 29/10/2026. Débitos: DEB-146, DEB-147, DEB-148 e DEB-150.
Este arquivo é o **contrato entre o backend e o app**: as sessões trabalham em paralelo contra a seção 1. Ninguém muda nome
de rota, campo ou código de erro sem combinar com a sessão principal.

## 0. Fontes

- **Regras (vencem este plano se divergirem):** `~/Documents/Obsidian Vault/Saqz/Regras/`
  - `Jogos/JOGO-008.md` (valor por jogo obrigatório e aviso aos grupos sem valor);
  - `Financeiro/FIN-004.md`, `FIN-006.md` (caixa), `FIN-007.md` (acerto novo), `FIN-008.md` (Pix na página do grupo),
    `FIN-016.md` (diária retroativa);
  - `Presença e lista de espera/PROM-010.md` (pagamento antecipado), com os ponteiros em `PROM-004.md` e `PROM-008.md`;
  - linhas DEB-146, DEB-147, DEB-148 e DEB-150 de `Débitos.md`.
- **Telas aprovadas:** canvas https://claude.ai/artifact/9SGq9SxGvzAbeHCaGEtM1F. O gerador está no repositório:
  `python3 design/financeiro/gerar.py "$(cat design/financeiro/alturas.json)"` cria `design/financeiro/previa/<Prancha>.html`
  para abrir no navegador. Textos, medidas e cores de cada tela estão nas funções `board_*` do `gerar.py` (valores tirados do
  `SaqzTheme`; no app use os tokens, nunca hex nem dp crus).
- **Roteiros de teste:** `tests/roteiros/jogos.md` (JOGO-008), `financeiro.md` (FIN-004 a FIN-008 e FIN-016),
  `presenca-e-lista-de-espera.md` (PROM-004, PROM-008 e PROM-010); jornadas `tests/producao` PROD-FI09 a FI12, JG14 e JG15.

## 1. Contrato HTTP

Regras gerais:
- Tudo **aditivo**. Os apps nas lojas continuam funcionando: campo novo opcional na entrada e ignorado por eles na saída.
- Erro de validação: `InvalidGroupRequestException` (400) com o campo em `fieldErrors`. Conflito de versão: 409. Gestor é
  dono ou admin; atleta recebe 403; quem não é do grupo, 404.
- Migração: próximo número livre somando todos os módulos (hoje o maior é **V200**). Confira no começo e antes do relato.

### 1.1 Valor por jogo obrigatório (DEB-146)

"Sem diária" fica guardado como **valor vazio + marca**. Vazio **sem** marca é "não definido" (só app antigo e grupo legado).
O obrigatório fica no **app**: o servidor não distingue o app novo do antigo.

| Onde | Campo novo | Regra |
| --- | --- | --- |
| `POST /api/groups` e `PUT /api/groups/{groupId}` (`UpdateGroupProfileRequest`) | `defaultNoGameFee: Boolean?` | Criar: ausente = `false`. Atualizar: ausente = não muda. `true` com `defaultGameFeeCents` → 400 em `defaultNoGameFee`. `true` grava valor vazio e marca; valor grava a marca `false`. App antigo que manda `defaultGameFeeCents` vazio num grupo com a marca: a marca fica. |
| `GET /api/groups/{groupId}` → `financeDefaults` | `defaultNoGameFee: Boolean`, `gameFeeUndefined: Boolean` | `gameFeeUndefined` = valor vazio e sem marca. É o que mostra o cartão azul ao gestor. |
| `POST /api/groups/{groupId}/games`, `PUT .../games/{gameId}` (`GameWriteRequest`) | `noGameFee: Boolean?` | `true` com `gameFeeCents` → 400 em `noGameFee`. Sem valor e sem marca (app antigo): ao **criar**, o jogo leva o padrão do grupo (valor ou marca; sem padrão, não definido); ao **editar**, jogo com a marca continua com ela, e o resto leva o padrão do grupo. `useDefaultGameFee` segue ignorado. |
| `GameResponse` (detalhe, listas, Início, jogos anteriores) | `noGameFee: Boolean` | App antigo lê `gameFeeCents` vazio como sem valor, como hoje. |
| `POST /api/groups/{groupId}/game-series` e `.../boundaries` (`WeeklySlotRequest`) | `noGameFee: Boolean?` | Mesmas regras do jogo. `WeeklySlotResponse.noGameFee`. Os jogos que a série cria copiam valor e marca. |

**Grupo que deixa de estar "não definido"** (atualização com valor ou marca): na mesma transação, os horários da série e os
jogos que **não começaram** e estão "não definidos" passam a ter o valor ou a marca do grupo. Nenhuma diária nasce sozinha:
quem já estava confirmado vai para a revisão da FIN-016.

### 1.2 Aviso aos grupos sem valor (DEB-146)

- Liga com a propriedade `saqz.groups.game-fee-notice.enabled` (variável `SAQZ_GROUPS_GAME_FEE_NOTICE_ENABLED`, padrão
  **desligado**). Fica desligado até o app novo estar nas lojas; a sessão principal liga em produção.
- Ligado, um job manda **uma vez por grupo** com `gameFeeUndefined`, a dono e admins, pelo canal **`FIRST_MEMBER`** (aviso de
  sistema só de organizadores, V97/V98) com `event_kind = 'GAME_FEE_UNDEFINED'` e `request_id` determinístico por grupo.
  Título: `"<nome do grupo>: defina o valor por jogo"`. Corpo: `"Hoje o avulso joga sem diária. Toque para definir."`.
- **`eventKind: String?`** novo na mensagem de `GET /api/me/notifications` e no dado do push. O app novo leva
  `GAME_FEE_UNDEFINED` ao "Valor por jogo" do grupo; o app antigo abre o grupo, como em todo `FIRST_MEMBER`.
- O cartão azul do grupo não depende do aviso: sai de `financeDefaults.gameFeeUndefined`.

### 1.3 Diária retroativa (DEB-147)

Entra na revisão quem está **confirmado** num jogo **sem diária** (nenhuma cobrança daquele jogo, em qualquer estado) e
**não é mensalista** na hora da leitura. Convidado entra sempre, no nome do anfitrião. Ficam de fora: jogo cancelado,
rascunho e jogo com `noGameFee`. Jogo com valor ou "não definido", realizado ou aberto, entra. Sem limite de data.

**`GET /api/groups/{groupId}/finance/uncharged`** (gestor): o resumo para o caixa. Conta só quem ainda não foi decidido.

```json
{ "peopleCount": 6,
  "games": [ { "gameId": "…", "title": "Vôlei de terça", "localDate": "2026-10-20", "localTime": "19:30",
               "venueName": "CERET", "peopleCount": 4 } ] }
```

Jogos do mais recente ao mais antigo; só os que têm alguém a decidir.

**`GET /api/groups/{groupId}/games/{gameId}/uncharged`** (gestor): a lista da revisão e da seção "Sem cobrança" do acerto.

```json
{ "gameId": "…", "suggestedAmountCents": 2000,
  "people": [
    { "memberId": "…", "guestSeq": 0, "displayName": "Caio Lima", "hostDisplayName": null,
      "kind": "AVULSO", "noShow": false, "leftGroup": false, "decision": null },
    { "memberId": "<anfitrião>", "guestSeq": 1, "displayName": "Júlia Rocha", "hostDisplayName": "Bia Souza",
      "kind": "GUEST", "noShow": false, "leftGroup": false, "decision": "SKIP" } ] }
```

- `suggestedAmountCents`: o valor do jogo; sem ele, o padrão do grupo; sem os dois, `null` (o gestor digita).
- `decision`: `null` (a decidir) ou `"SKIP"` (Não cobrar já decidido; continua na lista para poder ser reaberto).
- O app escolhe o estado inicial: `AVULSO` e `GUEST` em Cobrar; `noShow` ou `leftGroup` em Não cobrar; `SKIP` em Não cobrar.

**`POST /api/groups/{groupId}/games/{gameId}/uncharged/review`** (gestor):

```json
{ "requestId": "…", "amountCents": 1000,
  "people": [ { "memberId": "…", "guestSeq": 0, "decision": "CHARGE" },
              { "memberId": "…", "guestSeq": 0, "decision": "PAID", "paidMethod": "CASH" },
              { "memberId": "…", "guestSeq": 1, "decision": "SKIP" } ] }
```

Resposta 200: `{ "charged": 1, "paid": 1, "skipped": 1, "ignored": 0, "charges": [ <ChargeResponse>, … ] }`.

- `CHARGE`: diária `PENDING` que **vence hoje** (fuso do grupo).
- `PAID`: diária criada e recebida na mesma transação, com `paidMethod` (`PIX` se ausente; `PIX`, `CASH` ou `OTHER`) e
  **data de recebimento = início do jogo**. Entra no caixa e no extrato nessa data.
- `SKIP`: guarda a marca por jogo e pessoa (quem decidiu e quando), sem cobrança. `CHARGE` ou `PAID` de quem tinha a marca
  apaga a marca.
- `amountCents` de 1 a 99.999.999, obrigatório se houver `CHARGE` ou `PAID` (400 em `amountCents`).
- Pessoa que já tem diária daquele jogo, deixou de estar confirmada ou virou mensalista: conta em `ignored`, sem erro.
- `requestId` repetido devolve o mesmo resultado sem segundo efeito; dois gestores ao mesmo tempo não duplicam (uma diária por
  pessoa e jogo).
- Cria pela mesma porta das diárias, para valer o valor do atleta com recebimento ativo (FIN-010, `payable_cents`).
- **Nada vai ao atleta** (nem central, nem push, nem WhatsApp).

### 1.4 Caixa (DEB-148)

**Saldo inicial:**
- `GET /api/groups/{groupId}/finance/opening-balance` → `{ "amountCents": 25000, "version": 2 }`. `amountCents: null` =
  ninguém respondeu (é o que mostra a pergunta do caixa zerado).
- `PUT` no mesmo caminho, com `If-Match` (a primeira vez, a versão do GET), corpo `{ "amountCents": 25000 }` → 200 com o
  estado novo e `ETag`. `0` é o "Começar do zero". Negativo ou acima de 99.999.999 → 400 em `amountCents`. Cada mudança fica
  no histórico (quem, quando, de quanto para quanto). Gestor só.
- `GET .../finance/statement` → `summary.openingBalanceCents: Long?` novo; `accumulatedBalanceCents` passa a **somar** o
  saldo inicial (o app antigo mostra o saldo certo sem mudar). O saldo do grupo no caixa geral (`GET /api/me/finance/overview`)
  também soma.

**Categorias:** `ExpenseCategory` ganha `SPONSORSHIP` (Patrocínio) e `SALE` (Venda), **só com `direction = IN`** (com `OUT`
→ 400 em `category`). As categorias antigas continuam aceitas nos dois sentidos (app antigo) e `RACHA` continua sendo mostrada.
O app novo usa: entrada `SPONSORSHIP`, `SALE`, `OTHER`; saída `VENUE`, `EQUIPMENT`, `REFEREE`, `OTHER`. Os apps nas lojas
leem categoria desconhecida como "Outros" (`KtorFinanceGateways`, desde 08/2026).

### 1.5 Pagamento antecipado (DEB-150)

| Onde | Campo novo | Regra |
| --- | --- | --- |
| `POST /api/groups`, `PUT /api/groups/{groupId}` | `advancePaymentRequired: Boolean?` | Criar: ausente = `false`. Atualizar: ausente = não muda. `true` exige `promotionMode` `MANUAL` no resultado (400 em `advancePaymentRequired`). Atualizar para `FIFO` desliga na mesma transação. |
| `GET /api/groups/{groupId}` → `gameConfig` | `advancePaymentRequired: Boolean` | |
| `GameResponse` | `advancePaymentRequired: Boolean` | O valor do grupo na hora da leitura (a tela do jogo e o link de presença mostram a caixa de pagamento). |
| `GET .../attendance/roster` → itens de `waitlisted` | `charge: { "id", "status", "amountCents", "version" }?` | Só para o gestor; `null` sem diária daquele jogo e sempre `null` para o atleta. É o que dá "Pagou R$ 20" / "Falta pagar R$ 20" e o Recebi (rota de status de sempre, com a `version`). |
| `POST .../attendance/promote` (`AttendancePromotionRequest`) | `markPaid: Boolean?`, `paidMethod: String?` | `markPaid=true`: promove e, na mesma transação, dá baixa na diária do jogo (`PIX` se `paidMethod` ausente); sem diária, cria e dá baixa. Diária já recebida: só promove. `requestId` repetido: mesmo resultado. |
| `ChargeResponse` (lista do gestor) | `waitlisted: Boolean` | `true` quando a pessoa da diária (membro e `guestSeq`) está na espera daquele jogo. O app mostra "Na espera" antes do início e "Não liberado" depois. "Desistiu" continua vindo de `withdrew`, que passa a valer também para a diária **recebida** (presença `DECLINED`); Dispensar continua só na pendente. |

- **Diária no "Vou":** com a opção ligada, `ChargeTransactions.attendance` cria a diária também em `WAITLISTED`, para avulso e
  para convidado (no nome do anfitrião, inclusive ao adicionar o convidado), em jogo com valor. Vence na data do jogo.
  Mensalista nunca. Jogo Sem diária ou "não definido": nada.
- Quem já estava na espera quando a opção foi ligada não ganha diária retroativa: a diária nasce ao Colocar, como hoje.
- Ninguém é colocado sozinho e nada é desfeito sozinho: "Não vou" depois de pagar, ficar na espera até o início ou ser
  colocado sem pagar mantêm a diária como está. Jogo cancelado segue a regra de sempre (pendentes canceladas, recebidas com
  "revisar").
- App antigo: o avulso vê a espera de sempre e a cobrança em Minhas cobranças; o gestor não vê a linha do pagamento e coloca
  como hoje.

## 2. App: o que cada sessão faz

Duas sessões em paralelo, em `mobile/`, sem mexer no backend. Strings novas em arquivos próprios de cada sessão
(`strings_valor_jogo.xml`, `strings_pagamento_antecipado.xml`; `strings_caixa_novo.xml`, `strings_retroativa.xml`).
A página do grupo (`GroupDetailsScreen`) é tocada pelas duas, em blocos diferentes.

### 2.1 Sessão "app-jogo": valor por jogo e pagamento antecipado (DEB-146, DEB-150)

- **Bloco "Valor por jogo"** (prancha `Valor-A`, funções `fee_states("a")` e `board_valor`): segmentado de largura igual
  **Cobrar diária · Sem diária**, nada marcado; Cobrar diária abre o campo de valor com a sugestão no apoio
  ("Sugestão: mensalidade ÷ 4 (R$ 80 ÷ 4).", só com mensalidade); Sem diária mostra "O avulso joga sem pagar por jogo."; sem
  escolha o editor de jogo não salva ("Escolha o valor ou Sem diária." no campo e o aviso geral no topo). Um componente só,
  usado no editor de jogo (inclusive "Repetir toda semana"), em Criar grupo e em Editar grupo. **Ajuste do dono em 29/10:**
  em Criar e Editar grupo o bloco é opcional (o grupo pode ficar sem valor); no grupo sem valor, a escolha do jogo novo
  vira a do grupo.
- **Cartão do grupo sem valor** (prancha `Aviso-grupo`): na página do grupo, só para dono e admins, enquanto
  `financeDefaults.gameFeeUndefined`; abre o "Valor por jogo" de Editar grupo com a sugestão preenchida (ou vazio sem mensalidade).
- **Aviso `GAME_FEE_UNDEFINED`:** o toque na central e no push leva ao mesmo lugar (`compose-app`, `SaqzNavHost`).
- **Linha da diária** (prancha `Avulso-diaria`): "Diária de R$ 20, cobrada ao confirmar" na tela do jogo e na tela do link de
  presença, antes do Vou, só para quem paga diária (avulso). Sem a linha em jogo Sem diária ou sem valor.
- **Pagamento antecipado:**
  - Configuração (prancha `Antecipado-config`): em "Como preencher as vagas", a chave "Vaga só com pagamento antecipado" logo
    abaixo de Escolha manual, só nesse modo; trocar para Ordem de chegada desliga, com o aviso da prancha.
  - Avulso na espera (prancha `Antecipado-avulso`): a caixa "Pague R$ 20 para entrar" / "A vaga é liberada pelo organizador
    depois do pagamento." com Copiar o Pix do grupo (com recebimento pelo app ativo, o "Pagar" que já existe no lugar do Pix).
    Depois do Recebi: "Pagamento recebido. O organizador libera a vaga." (texto novo, sem prancha).
  - Espera do organizador (prancha `Antecipado-espera`): a linha do pagamento de cada pessoa pelo `charge` do roster:
    "Pagou R$ 20" (verde) com Colocar; "Falta pagar R$ 20" com Recebi e Colocar; convidado "<anfitrião> não pagou R$ 20".
  - Folha de colocar quem não pagou (prancha `Antecipado-folha`), **sem supor gênero**: "Rafa ainda não pagou",
    "Diária de R$ 20. Como você quer colocar Rafa?", **"Colocar, já pagou"** (apoio "Marca os R$ 20 como recebidos."),
    "Colocar e cobrar depois" (apoio "A diária fica pendente, como hoje.") e "Voltar".

### 2.2 Sessão "app-caixa": caixa novo, acerto, revisão e Pix (DEB-147, DEB-148)

- **Caixa** (pranchas `Caixa-A`, `Caixa-A-saiu`, `Caixa-A-menu`, `Caixa-zerado`; funções `caixa_a_hero`, `board_caixa_*`):
  cabeçalho azul com "Saldo do caixa", o saldo (soma o saldo inicial; "Inclui o saldo inicial de R$ 250" quando houver), o
  mês do placar, **Entrada** (botão branco) e **Saída** (contorno vermelho claro), a pendência "N jogaram sem cobrança ·
  Revisar" (do resumo da 1.3; um jogo só abre a revisão dele, mais de um abre a lista de jogos) e o placar **Entrou · Saiu ·
  Falta receber** como abas (Saiu em vermelho claro). Falta receber lista os devedores por grupo (mensalidades do mês,
  diárias) com **Recebi** e o ⋮ (Cobrar, Dispensar, Cancelar); "Cobrar os N que faltam" no rodapé. O ⋯ do topo tem
  **Saldo inicial** e **Criar cobranças do mês**. Sai o cartão do Pix; o aviso de grupo sem chave Pix (DEB-058) continua.
  Caixa zerado (saldo inicial `null` e nada lançado): o cartão "Quanto o grupo já tem em caixa?".
- **Saldo inicial:** fixo no topo do extrato com Editar; editar abre uma folha com o valor e Salvar.
- **Entrada e Saída** (pranchas `Entrada`, `Saida`): duas telas no lugar do "Novo lançamento". Valor primeiro e grande;
  Entrada com Patrocínio, Venda e Outros, sem atalhos; Saída com os atalhos R$ 80/120/160 e Quadra, Material, Árbitro e
  Outros; Data com hoje e o calendário no toque. Corrigir e anular (DEB-056) continuam.
- **Extrato** (prancha `Extrato`): Tudo · Entradas · Saídas, o resumo do mês com o "saiu" em vermelho, o saldo inicial fixo,
  os dias, valores de saída em vermelho e Entrada/Saída embaixo.
- **Acerto** (pranchas `Acerto`, `Acerto-retro`, `Antecipado-acerto`): a tela nova da FIN-007 (cabeçalho do jogo, placar
  Jogaram · Pagaram · Falta receber, Diárias, Mensalistas numa linha, Sem cobrança com a faixa e Revisar, Não jogaram com
  "Não liberado"/"Desistiu", Saídas do jogo e Cobrar no rodapé). A linha da quadra diz **"Lançar"** e abre a Saída com Quadra,
  "Aluguel da quadra" e a data do jogo, **sem valor**: o "R$ 160" do canvas é só exemplo, o Saqz não guarda o preço da quadra
  e o acerto não mostra custo por jogo (FIN-007, 18/10).
- **Revisão** (pranchas `Revisao-B-lista` e `Revisao-B`): um valor para o lote, uma linha por pessoa com o estado num botão
  que abre o menu (Cobrar · Já pagou · Não cobrar), a linha "Entra no caixa em 20/10 · Pix" no Já pagou (o toque troca o meio),
  o resumo e Confirmar.
- **Pix do grupo na página do grupo** (prancha `Grupo-pix`): a seção "Pix do grupo" depois de Minhas cobranças, com a chave,
  o tipo ("Chave Pix · E-mail", como no cartão da cobrança) e Copiar; para quem nunca teve cobrança, depois da Galera. Some com
  cobrança em aberto, com recebimento ativo e sem chave.

## 3. Ordem

1. Backend na `main` e em dev; a sessão principal revisa e faz o deploy de produção (manual) antes da RC do app.
2. Apps na `main`; capturas Roborazzi revisadas; RC.
3. Com o app nas lojas: ligar `SAQZ_GROUPS_GAME_FEE_NOTICE_ENABLED` em produção.
