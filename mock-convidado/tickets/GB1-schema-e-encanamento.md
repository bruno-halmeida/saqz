# GB1 · Backend: V82 + `guestSeq` encanado, comportamento idêntico (VUL-XXX)

**Onda 1 · depende de: nada · bloqueia: GB2 · paralelo com: GM1**

## Objetivo

Preparar o banco e o motor de presença para uma 2ª linha do MESMO membro no MESMO jogo (o
convidado, `guest_seq > 0`), **sem criar nenhum comportamento novo**: nenhum endpoint novo, nenhum
JSON muda, todos os testes que existem continuam verdes SEM edição. Leia `CONTRATO.md` desta pasta
(seção "Modelo de dados"). Quem cria convidado é o GB2.

`G` = `backend/features/groups/src/main/kotlin/br/com/saqz/groups`
`MIG` = `backend/features/groups/src/main/resources/db/migration`
`IT` = `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups`

## Arquivos (lista FECHADA)

- NOVO `MIG/V82__add_game_guests.sql`
- EDITAR `G/application/attendance/AttendanceCommandPorts.kt`
- EDITAR `G/application/attendance/RespondAttendance.kt`, `G/application/attendance/AttendancePromotion.kt`
- EDITAR `G/adapter/output/jdbc/attendance/JdbcAttendanceCommandRepository.kt`
- EDITAR `G/adapter/output/jdbc/attendance/JdbcAutoConfirmationRepository.kt`
- EDITAR `G/adapter/output/jdbc/attendance/share/JdbcAttendanceLinkRepository.kt`
- EDITAR `G/adapter/output/jdbc/athlete/JdbcAthleteStatsRepository.kt`, `G/adapter/output/jdbc/profile/JdbcProfileStatsRepository.kt`
- EDITAR `G/adapter/output/jdbc/communication/JdbcGroupCommunicationRepository.kt`
- EDITAR `G/adapter/output/jdbc/home/JdbcHomeRepository.kt`
- EDITAR `G/application/finance/charge/ChargeTransactions.kt`, `G/adapter/output/jdbc/finance/JdbcChargeTransactionRepository.kt`
- NOVO `IT/adapter/output/jdbc/migration/GameGuestMigrationIntegrationTest.kt`

Tocar em arquivo fora desta lista = PARE e avise o orquestrador (SendMessage para `main`), e aguarde.
Teste existente que ficar vermelho: NÃO edite o teste — PARE e relate o assert.

## Passo 0

```
git fetch origin && git switch -c vul-XXX-convidado-schema origin/main
ls backend/features/*/src/main/resources/db/migration | grep -o '^V[0-9]*' | sort -t V -k2 -n | tail -1   # tem que ser V81
```
Se o máximo não for V81, use `max+1` no nome do arquivo e avise no PR. Faça o primeiro commit logo após o passo 1.

## Passo 1 · migration `MIG/V82__add_game_guests.sql` (conteúdo inteiro)

```sql
-- Convidado de jogo (VUL-239): 0 = a resposta do próprio membro; 1..n = convidados dele.
ALTER TABLE game_attendance ADD COLUMN guest_seq smallint NOT NULL DEFAULT 0;
ALTER TABLE game_attendance ADD CONSTRAINT ck_game_attendance_guest_seq CHECK (guest_seq >= 0);
ALTER TABLE game_attendance DROP CONSTRAINT game_attendance_pkey;
ALTER TABLE game_attendance ADD PRIMARY KEY (game_id, member_user_id, guest_seq);

-- attendance_events é append-only por trigger de UPDATE/DELETE; ADD COLUMN com DEFAULT não dispara.
ALTER TABLE attendance_events ADD COLUMN guest_seq smallint NOT NULL DEFAULT 0;

ALTER TABLE group_charges ADD COLUMN guest_seq smallint NOT NULL DEFAULT 0;
ALTER TABLE group_charges ADD COLUMN guest_display_name varchar(80);
ALTER TABLE group_charges ADD CONSTRAINT ck_group_charges_guest
    CHECK ((guest_seq = 0 AND guest_display_name IS NULL) OR (guest_seq > 0 AND kind = 'GAME' AND guest_display_name IS NOT NULL));
DROP INDEX uq_group_charges_game_member;
CREATE UNIQUE INDEX uq_group_charges_game_member
    ON group_charges (group_id, game_id, member_user_id, guest_seq) WHERE kind = 'GAME';
```

Antes de salvar, confira a definição atual do índice em `MIG/V41__convert_finance_enums.sql:188`
(`grep -n -A3 "CREATE UNIQUE INDEX uq_group_charges_game_member" MIG/V41*`): se o `WHERE` lá usar
cast de enum (ex.: `kind = 'GAME'::charge_kind`), copie o MESMO predicado. Se o nome da PK não for
`game_attendance_pkey` (rode a migration; o erro diz), PARE e relate.

## Passo 2 · tipos (`AttendanceCommandPorts.kt`)

Acrescente `val guestSeq: Int = 0,` como ÚLTIMO parâmetro (depois dos que já têm default) em:
`AttendanceAggregate`, `AttendanceRecord`, `AttendanceEvent`. Em `AttendanceAggregate` acrescente
também, depois dele, `val guestName: String? = null,`.

Na interface `AttendanceCommandRepository` troque a assinatura do `lock` por:

```kotlin
    fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int = 0): AttendanceAggregate?
```

Em `AttendanceRosterMember` acrescente no fim `val guestSeq: Int = 0,` e `val hostDisplayName: String? = null,`.

Se algum fake de teste implementar `lock` com 4 parâmetros e deixar de compilar, isso é arquivo fora
da lista: PARE e relate quais (não edite).

## Passo 3 · motor (`RespondAttendance.kt`, `AttendancePromotion.kt`)

`RespondAttendance.apply`: o `AttendanceRecord(...)` ganha `guestSeq = aggregate.guestSeq` (argumento
nomeado, no fim) e o `AttendanceEvent(...)` ganha `guestSeq = aggregate.guestSeq`.

`RespondAttendance.promoteOne`: no `aggregate.copy(...)` acrescente `guestSeq = waiting.guestSeq,`.

`AttendancePromotion.promoteAttendance`: o `AttendanceEvent(...)` ganha `guestSeq = promoted.guestSeq`
(o `promoted` já herda o `guestSeq` pelo `current.copy`).

Nada mais muda nesses dois arquivos.

## Passo 4 · `JdbcAttendanceCommandRepository.kt`

4a. `lock(...)`: nova assinatura com `guestSeq: Int` (sem default no override) e `.param("guest", guestSeq)` na consulta `AGGREGATE`.

4b. `AGGREGATE`: troque o LEFT JOIN da presença por
`LEFT JOIN game_attendance a ON a.game_id=g.id AND a.member_user_id=:member AND a.guest_seq=:guest`
e acrescente ao SELECT `:guest::smallint AS guest_seq, a.member_display_name AS guest_name`.
No `aggregate(rs, row)`: o `AttendanceRecord(...)` ganha `guestSeq = rs.getInt("guest_seq")`; o
`AttendanceAggregate(...)` ganha `guestSeq = rs.getInt("guest_seq")` e
`guestName = rs.getString("guest_name").takeIf { rs.getInt("guest_seq") > 0 }`.

4c. `SAVE`: a lista de colunas ganha `guest_seq`; o SELECT ganha `:guest`; o nome vira
`CASE WHEN :guest > 0 THEN :guestName ELSE (SELECT coalesce(nickname, display_name) FROM access_users WHERE id=:member) END`;
o `ON CONFLICT (game_id,member_user_id)` vira `ON CONFLICT (game_id,member_user_id,guest_seq)`.
`save(record)`: acrescente `.param("guest", record.guestSeq)` e `.param("guestName", null as String?, java.sql.Types.VARCHAR)`.
(O GB2 é quem passa o nome de verdade; aqui só existe `guest_seq = 0`.)

4d. `APPEND`: coluna `guest_seq` + valor `:guest`; `append(event)`: `.param("guest", event.guestSeq)`.

4e. `PROMOTION_REPLAY` e `RESPONSE_REPLAY`: no JOIN, acrescente `AND attendance.guest_seq=event.guest_seq`;
no SELECT, `event.guest_seq`. Nos dois mapeadores, `AttendanceRecord(...)` e `AttendanceEvent(...)`
ganham `guestSeq = rs.getInt("guest_seq")`.

4f. `EARLIEST_WAITLISTED`: SELECT ganha `attendance.guest_seq`; o `AttendanceRecord(...)` do mapeador
ganha `guestSeq = rs.getInt("guest_seq")`. (Convidado já entra no bucket 1 do ORDER BY porque o
`membership_type` dele é o do anfitrião — se o anfitrião for MENSALISTA o convidado furaria a fila:
troque a condição do `CASE` para `... AND membership.membership_type='MENSALISTA' AND attendance.guest_seq=0 THEN 0`.)

4g. `DETAIL`: o LEFT JOIN `a` ganha `AND a.guest_seq=0`; o subselect de `declined_count` ganha
`AND d.guest_seq=0`. `confirmed_count`, `waitlist_count` e `pending_count` NÃO mudam.

4h. `OWN_BY_GAME`: o JOIN ganha `AND a.guest_seq=0`.

4i. `ROSTER`: SELECT ganha `a.guest_seq` e
`(SELECT coalesce(u.nickname, u.display_name) FROM access_users u WHERE u.id=a.member_user_id AND a.guest_seq>0) AS host_display_name`;
nas duas condições `m.membership_type='MENSALISTA'` do ORDER BY acrescente `AND a.guest_seq=0`, e na
condição do bucket 2 troque `(m.membership_type IS NULL OR m.membership_type<>'MENSALISTA')` por
`(m.membership_type IS NULL OR m.membership_type<>'MENSALISTA' OR a.guest_seq>0)`; no fim do ORDER BY
acrescente `,a.guest_seq`. `RosterRow` ganha `guestSeq: Int` e `hostDisplayName: String?`;
`member()` passa os dois para `AttendanceRosterMember`. **O controller NÃO muda neste ticket**
(o JSON novo é do GB2).

4j. `AttendanceChargeAdapter.charge`: o `GameChargeInput(...)` ganha, no fim,
`guestSeq = aggregate.guestSeq`.

## Passo 5 · cobrança

`ChargeTransactions.kt`: `GameChargeInput` ganha no fim `val guestSeq:Int=0` (o nome NÃO vem por parâmetro: na promoção FIFO o motor não o tem).

`JdbcChargeTransactionRepository.kt`:
- `INSERT_GAME`: colunas ganham `guest_seq,guest_display_name`; valores `:guest,(SELECT ga.member_display_name FROM game_attendance ga WHERE ga.game_id=:game AND ga.member_user_id=:member AND ga.guest_seq=:guest AND :guest>0)`; o
  `ON CONFLICT (...)` ganha `guest_seq` na lista (mantenha o mesmo `WHERE kind='GAME'`).
- `createGameCharge`: `.param("guest",input.guestSeq)`
  e a releitura vira `findGame(input.groupId,input.gameId,input.memberId,input.guestSeq)`.
- `findGame(group,game,member,guest:Int)`: a cláusula ganha `AND c.guest_seq=:guest` + `.param("guest",guest)`.
- `SELECT`/`map`/`Charge`: NÃO mudam neste ticket.

## Passo 6 · leitores que querem "a resposta DO MEMBRO" (`guest_seq = 0`)

- `JdbcAutoConfirmationRepository.kt`: no LEFT JOIN dos candidatos, `AND a.guest_seq=0`; no `SAVE`,
  colunas/valores ganham `guest_seq`/`0` e o conflito vira `ON CONFLICT (game_id,member_user_id,guest_seq)`.
- `JdbcAthleteStatsRepository.kt`: no LEFT JOIN, `AND attendance.guest_seq = 0`.
- `JdbcProfileStatsRepository.kt`: no LEFT JOIN, `AND attendance.guest_seq = 0`.
- `JdbcHomeRepository.kt`: nos DOIS `LEFT JOIN game_attendance own` acrescente `AND own.guest_seq = 0`;
  no `EXISTS (... FROM game_attendance own ...)` de `own_played` acrescente `AND own.guest_seq = 0`;
  no subselect de `declined_count` acrescente `AND attendance.guest_seq = 0`. Contagens
  CONFIRMED/WAITLISTED, `pending_count` e o roster de prévia NÃO mudam.
- `JdbcGroupCommunicationRepository.kt`: na consulta dos buckets (a que seleciona
  `attendance.member_display_name AS name`), acrescente ao WHERE
  `AND (attendance.guest_seq = 0 OR attendance.status <> 'DECLINED')`. O `NOT EXISTS` dos destinatários NÃO muda.
- `JdbcAttendanceLinkRepository.kt`: na consulta que lê `user_account.display_name` com
  `LEFT JOIN game_attendance attendance`, troque a coluna por
  `CASE WHEN attendance.guest_seq > 0 THEN attendance.member_display_name ELSE user_account.display_name END AS display_name`
  e acrescente ao `ON` do join `AND (attendance.guest_seq = 0 OR attendance.status <> 'DECLINED')`.

Ficam SEM mudança (já contam "gente no jogo" ou usam DISTINCT): `JdbcAdminGroupDirectoryRepository`,
`JdbcInviteRedemptionRepository`, `JdbcOwnerPlanUsageLookup`, `counts(...)`, `CAPACITY_AGGREGATE`.

## Passo 7 · teste novo `IT/adapter/output/jdbc/migration/GameGuestMigrationIntegrationTest.kt`

Copie a armação (anotações, datasource, helpers `int(...)`/`exec(...)`) do vizinho
`FinanceEnumMigrationIntegrationTest.kt` — mesmo pacote. Três testes, com os inserts mínimos que o
vizinho `JdbcAttendanceCommandRepositoryIntegrationTest` já usa como fixture (copie de lá):

1. `primaryKeyAllowsGuestRowsForTheSameMember` — insere presença `(game, member, guest_seq 0)` e
   `(game, member, guest_seq 1, member_display_name 'Rafa')`; `count(*)` = 2; um 3º insert repetindo
   `guest_seq 1` falha com violação de unicidade.
2. `gameChargeIsUniquePerGuest` — duas cobranças GAME do mesmo `(group, game, member)` com
   `guest_seq` 0 e 1 (`guest_display_name 'Rafa'` na segunda) entram; repetir `guest_seq 1` falha.
3. `guestNameIsRequiredOnlyForGuests` — cobrança com `guest_seq 1` sem `guest_display_name` viola
   `ck_group_charges_guest`; com `guest_seq 0` e nome preenchido também viola.

## Gates (da RAIZ do repositório, uma linha cada; JDK 21 obrigatório; Postgres é embutido — Docker parado não bloqueia)

```
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew :features:groups:integrationTest --tests '*GameGuestMigrationIntegrationTest' --tests '*JdbcAttendanceCommandRepositoryIntegrationTest'
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew :features:groups:test :features:groups:integrationTest
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew check
```

## Critérios de aceite

- [ ] `git diff --stat origin/main...HEAD` só lista arquivos da lista fechada; nenhum teste existente editado.
- [ ] `git grep -n "ON CONFLICT (game_id,member_user_id)" -- backend` só devolve ocorrências seguidas de `,guest_seq)`.
- [ ] `./gradlew check` verde.
- [ ] Nenhum endpoint, DTO ou JSON mudou (`git diff origin/main...HEAD -- '*Controller.kt'` vazio).

## PR

Título: `feat(attendance): schema e encanamento do convidado de jogo (VUL-XXX)`. Corpo: o que a V82 faz,
a regra "membro = guest_seq 0", a lista de leitores ajustados, resultado dos gates. Commits pequenos em
PT-BR terminando com `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`; PR ready com
`GH_TOKEN=$(gh auth token --user bruno-halmeida)`, corpo terminando com
`🤖 Generated with [Claude Code](https://claude.com/claude-code)`. PARE depois do PR; depois só `CORRECAO:`.
