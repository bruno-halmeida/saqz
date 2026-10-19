# Achados dos atores por API

Divergências entre o cofre (`Saqz/Regras`) e o que o backend faz, achadas rodando `node tests/atores/run.mjs --todos`
contra o stack local em 18/10/2026. **Nada foi corrigido aqui** (fora do escopo do executor): cada item tem o cenário que
reprova, os passos e a resposta do servidor. Os cenários seguem reprovando até o backend mudar; o campo `achado` do cenário
rotula a falha no relatório.

## 1. ACE-002 · "Vou" de conta sem e-mail nem telefone confirmados é aceito (já é o DEB-082)

- **Cenário:** `ACE-002` (rotulado `DEB-082`).
- **Regra:** "Para confirmar presença ('Vou', inclusive de convidado) a conta precisa estar confirmada por e-mail e/ou
  telefone. O servidor é quem barra."
- **Passos:** ORG cria o grupo e publica o jogo; ATL1 cria conta por e-mail e **não** confirma; ATL1 toca em "Vou"
  (`PUT /api/groups/{g}/games/{j}/attendance` com `intent: CONFIRM`).
- **Esperado:** 403 com erro claro, nenhuma linha em `game_attendance`.
- **Veio:** `HTTP 200 {"attendance":{"memberId":"…","status":"CONFIRMED","version":1,…}}`; a presença foi gravada como
  `CONFIRMED`. O código `EMAIL_NOT_VERIFIED` existe em `ErrorCode`, mas a rota não o usa.
- **Efeito nos outros cenários:** os atores dos cenários nascem com **e-mail confirmado** (via API de administração do
  emulador) justamente para não quebrarem quando o DEB-082 entrar; só o `ACE-002` usa conta sem confirmar.
- Os demais passos do `ACE-002` passam: "Não vou" sem confirmar funciona; o e-mail chega ao Mailpit; abrir o link confirma;
  reenvio antes de 60 s dá 429 com `Retry-After`; o 9º pedido em 10 minutos dá 429; telefone confirmado vale sozinho;
  trocar o telefone zera a confirmação.

## 2. NOT-001 · Desligar "Avisos dos grupos" tira o aviso da central (já é o DEB-064)

- **Cenário:** `NOT-001` (rotulado `DEB-064`).
- **Regra:** "Preferências controlam só push e WhatsApp: a central sempre mostra tudo."
- **Passos:** ATL1 desliga `notices` e `push.notices` em `PUT /api/me/notification-preferences`; ORG publica um aviso no mural
  (`POST /api/groups/{g}/messages?channel=NOTICE`); ATL1 abre `GET /api/me/notifications`.
- **Esperado:** o aviso novo aparece (4 avisos do mural).
- **Veio:** a central de ATL1 segue com 3 avisos; o novo não chegou (provavelmente o gatilho `notification_in_app_preference` marca `in_app = false` para a chave desligada).

## 3. NOT-004 · Aprovar o primeiro atleta não avisa o dono ("A galera começou a chegar")

- **Cenário:** `NOT-004` (rotulado `FIRST_MEMBER-NA-APROVAÇÃO`). **Achado novo**, não está nos débitos.
- **Regra (NOT-004):** "Primeiro atleta entra → dono e admins recebem '<Nome> entrou no <Grupo>. A galera começou a
  chegar.', uma vez só."
- **Passos:** ORG cria grupo com `entryRequiresApproval: true`; ADM e depois ATL1/ATL2 usam o convite (viram pedido
  pendente, `status: PENDING`); ORG/ADM aprovam (`POST /api/groups/{g}/entry-requests/{u}/approve`, 200).
- **Esperado:** um aviso `FIRST_MEMBER` na central do dono depois da primeira aprovação.
- **Veio:** nenhuma linha `FIRST_MEMBER` em `group_messages` do grupo (só `ENTRY_REQUEST` e `AUTO_CONFIRMATION` "Você
  entrou no …"). Na **entrada direta** (grupo sem aprovação) o aviso sai e não repete, como manda a regra: o
  `FirstMemberNotice` só é chamado por `RedeemInvite` (linha 77), não por `ApproveEntryRequest`.

## 4. PROM-009 / NOT-003 · "Avisar" manual depois do prazo e antes do início é recusado

- **Cenário:** `PROM-009` (rotulado `AVISAR-APÓS-PRAZO`). **Achado novo, a decidir** (pode ser desenho).
- **Regra:** PROM-009 diz que o corpo do lembrete mostra "Confirmações até …" **ou "Prazo encerrado"**, que o "Avisar" manual
  "passa a usar o mesmo corpo do automático", e NOT-003 que o lembrete vale "do jogo publicado até o início", inclusive
  depois do prazo (o automático faz isso). O organizador "pode tocar em Avisar quando quiser".
- **Passos:** jogo publicado com início futuro; o prazo de confirmação vence (`update games set confirmation_deadline=…`);
  ORG toca em Avisar (`POST /api/groups/{g}/games/{j}/notify-pending`).
- **Esperado:** 200 com o corpo contendo "Prazo encerrado".
- **Veio:** `HTTP 422 {"code":"VALIDATION_FAILED","fieldErrors":{"message":["is invalid"]}}`. A consulta
  `reminderGame` (`JdbcGroupCommunicationRepository`) exige `confirmation_deadline > now()`; o ramo "Prazo encerrado" do
  corpo só é alcançável pelo lembrete automático. Mensagem de erro genérica também não ajuda o app a explicar o motivo.
- **Resolvido (DEB-093, 18/10):** `reminderGame` passou a exigir só jogo publicado com início futuro; o cenário `PROM-009` passa.

## Divergências que **não** são bugs do backend (registradas para não virarem falso alarme)

- `JOGO-002`: a API exige `durationMinutes` (400 "is required"); o padrão de duração do grupo é aplicado pelo **app**.
- `JOGO-007`: "se o grupo ainda não tem quadra, a primeira informada vira a do grupo" é feito pelo **app** (o backend não
  grava a quadra do grupo ao criar jogo).
- `MEM-001`: `GET /memberships` (lista de papéis) é só da gestão (403 para atleta); o atleta vê os membros por
  `GET /athletes`, como a regra descreve.
- `SAI-003` (admin remove outro admin → 403) e `SAI-007` (quem saiu ou foi removido volta como pedido pendente) **passam**:
  as notas dizem "exige mudança" (DEB-024, DEB-027), mas o backend já se comporta como a regra manda; vale conferir se os
  dois débitos podem ser baixados em `Débitos.md`.
