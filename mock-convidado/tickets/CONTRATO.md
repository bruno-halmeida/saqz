# Convidado de jogo · CONTRATO (VUL-239)

Canvas aprovado: https://claude.ai/artifact/MBjqNGu6L3DfL3HCHGj7kS · PNG/HTML locais em `../preview/`.
Regras gerais de receita, gates mobile, prints e protocolo do worker: valem as do
`../../_mock-grupo/tickets/CONTRATO.md` (leia as seções "Regras", "Gates" e "Protocolo do worker").

## Decisões (fechadas — não reabrir)

1. Membro convida SÓ para o jogo. Convite de grupo continua do gestor.
2. Convidado = "+1" pelo NOME: sem conta, sem link. O anfitrião responde por ele.
3. Entra SEMPRE na lista de espera (mesmo com vaga). Sobe pelos gatilhos que já existem:
   desistência (FIFO), aumento de capacidade, promoção manual do gestor. Sem job.
4. Paga a taxa do JOGO (`games.game_fee_cents`); a cobrança nasce quando ele é promovido, em nome
   do ANFITRIÃO, como cobrança separada. Jogo sem taxa = sem cobrança.
5. Sem limite de convidados por membro.
A. Só leva convidado quem tem resposta própria CONFIRMED ou WAITLISTED no jogo.
B. Anfitrião vira DECLINED → todos os convidados ativos dele viram DECLINED junto.
C. Depois do `confirmation_deadline` o membro não adiciona nem tira; só OWNER/ADMIN tira/promove.
D. "Tirar": anfitrião (antes do prazo) e OWNER/ADMIN (sempre, enquanto o jogo está PUBLISHED).
E. Convidado CONFIRMED conta em "Vão"/vagas; nunca conta em "Sem resposta" nem em "Não vão".
F. Tirar convidado CONFIRMED cancela a cobrança PENDING (não eletrônica) dele e promove o próximo (FIFO).

## Modelo de dados (V82 — numeração de migration é GLOBAL entre módulos; confira o máximo antes)

- `game_attendance.guest_seq smallint NOT NULL DEFAULT 0` — 0 = a resposta do próprio membro;
  1..n = convidados desse membro. PK vira `(game_id, member_user_id, guest_seq)`.
  `member_user_id` do convidado = ANFITRIÃO. `member_display_name` do convidado = nome digitado.
  Linha de convidado NUNCA é apagada (remoção = status DECLINED) → `guest_seq` novo = max+1.
- `attendance_events.guest_seq smallint NOT NULL DEFAULT 0`.
- `group_charges.guest_seq smallint NOT NULL DEFAULT 0` + `guest_display_name varchar(80) NULL`;
  `uq_group_charges_game_member` vira `(group_id, game_id, member_user_id, guest_seq) WHERE kind='GAME'`.

Regra de leitura: toda consulta que quer "a resposta DO MEMBRO" filtra `guest_seq = 0`; toda
consulta que quer "gente no jogo" (contagens CONFIRMED/WAITLISTED, roster, fila) NÃO filtra.
`declined_count` e estatística de atleta filtram `guest_seq = 0`.

## API (o mobile pode codar contra isto antes do backend mergear)

Roster `GET /api/groups/{g}/games/{id}/attendance/roster` — cada entrada ganha:
`"guestSeq": 0` (int) e `"hostDisplayName": null` (string|null; nome do anfitrião quando `guestSeq > 0`).
Para convidado, `memberId` = id do anfitrião e `displayName` = nome do convidado.

`POST /api/groups/{g}/games/{id}/attendance/guests` body `{"requestId": uuid, "displayName": "Rafa Moreira"}`
→ 201 `{"memberId": hostId, "guestSeq": 1, "displayName": "...", "status": "WAITLISTED", "waitlistPosition": 7}`.
Nome: trim, 2..80 code points, sem caractere de controle → senão 422 campo `displayName`.
Negações (mesmo formato de problema das negações de presença de hoje; códigos `ATTENDANCE_HOST_NOT_GOING`, `ATTENDANCE_DEADLINE_PASSED`, `ATTENDANCE_FROZEN`): `HOST_NOT_GOING`,
`DEADLINE_PASSED`, `FROZEN`, `NOT_PUBLISHED`. Idempotente por `requestId`.

`DELETE /api/groups/{g}/games/{id}/attendance/guests/{hostId}/{guestSeq}` → 204.
Anfitrião: só antes do prazo (`DEADLINE_PASSED` depois). OWNER/ADMIN: sempre com jogo PUBLISHED.
Outro membro: 404 (Hidden), como o resto da presença.

`POST .../attendance/promote` — body ganha `"guestSeq": 0` opcional.

`GET /api/groups/{g}/charges` e as cobranças próprias do membro — cada cobrança ganha
`"guestDisplayName": null` (string|null).

## Ondas e donos

| Onda | Ticket | Dono de |
|---|---|---|
| 1 | **GB1** backend: V82 + `guestSeq` encanado, comportamento idêntico | `backend/**` |
| 1 | **GM1** mobile contrato: domain + data + fakes (roster, gateway add/remove, charge) | `mobile/features/groups/{domain,data}/**`, fakes de teste |
| 2 | **GB2** backend: adicionar/tirar convidado, cascata B, cancelamento F, promote com `guestSeq`, shapes | `backend/**` |
| 2 | **GM2** mobile estado: `GameDetailContract/ViewModel` + strings `strings_game_guest.xml` | `presentation/gamedetail/**`, strings |
| 3 | **GM3** UI tela do jogo (botão, folha, linhas, tirar) | `presentation/ui/gamedetail/**` |
| 3 | **GM4** acerto + "Minhas cobranças" com linha de convidado | `ui/finance/settlement/**`, `details/` mappers de cobrança |
| 4 | **GD** fecho: e2e novo `game-guest` + suíte tocada | `mobile/android-app/src/e2e/**`, `tests/e2e/**` |
