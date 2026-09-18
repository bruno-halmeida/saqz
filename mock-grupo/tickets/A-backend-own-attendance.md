# A · Backend — minha presença na listagem de jogos

**Onda 1 · depende de: nada · bloqueia: C4 (chip da agenda), D · paralelo com: S, B, V, T**

> Convenções desta receita
> - `VUL-XXX` = número do ticket no Linear, informado pelo orquestrador no prompt. Substitua `XXX` antes de colar qualquer bloco.
> - Âncoras conferidas em `origin/main` = `7ecaa08b` (`perf(groups): contagem de presença e eventos de cobrança em uma consulta cada`). O **texto literal** da âncora manda; o número da linha serve só para achar rápido.
> - Texto literal de uma âncora não existe no arquivo = parar e avisar o orquestrador. Não adaptar.
> - Todos os caminhos são relativos à raiz do repositório.
> - Pré-condição, logo depois de criar a branch (item 1 do Protocolo do worker): `git merge-base --is-ancestor 7ecaa08b HEAD && echo BASE_OK` imprime `BASE_OK`. Não imprimiu = parar e avisar o orquestrador. O teste J7 depende do `counts` em consulta única que esse commit trouxe.

## Objetivo

`GET /api/groups/{groupId}/games` passa a preencher `ownAttendance` em cada jogo devolvido, com a resposta do ator autenticado naquele jogo. Hoje o campo já existe em `GameResponse` (`GameController.kt:61`) e só o read de um jogo o preenche (`GameController.kt:96`); a listagem devolve sempre `null`.

Decisões já tomadas (não reabrir):

1. **Uma consulta adicional por request**, fora da consulta que lista os jogos. Ela mora em `JdbcAttendanceCommandRepository`, o mesmo adapter que hoje entrega o `own` do read (`find`, linha 235) e o `counts` da listagem (linha 289, já em consulta única). Com ela a listagem fica em 4 statements fixos: `role`, `list`, `counts`, `ownByGame`.
2. **Porta**: método novo `ownByGame` dentro da interface existente `AttendanceDetailQuery`, com corpo padrão vazio. `GameController` já recebe essa interface (`attendance`, linha 76), então **`AccessSessionConfiguration.kt` não muda** e nenhum fake existente quebra. Precedente do corpo padrão em porta, no mesmo arquivo: `AttendanceCommandRepository.findPromotionReplay` (`AttendanceCommandPorts.kt:73`).
3. **Consulta por grupo**: uma linha por jogo do grupo em que o ator respondeu. Entra por `games` (índice `ix_games_group_start (group_id, starts_at, id)`) e sonda a PK de `game_attendance` `(game_id, member_user_id)` — o mesmo JOIN da `DETAIL` (linha 474) e da `UPCOMING_GAMES` da Home (`JdbcHomeRepository.kt:339-341`).
4. **Visibilidade**: `ListGames` já filtra `Draft` por papel (`GameQueries.kt:45`). O controller só anexa a presença, por `game.id`, aos jogos que `ListGames` devolveu. Linha do mapa sem jogo correspondente na resposta é descartada.
5. **Um único mapeador** `AttendanceRecord.toEntryResponse()` usado pela listagem e pelo read: o formato JSON fica idêntico por construção.
6. **Sem migração**: os dois índices do item 3 já existem (`V4__add_group_games.sql`, `V6__add_game_attendance.sql`).

Assinatura do método novo:

```kotlin
fun ownByGame(actorId: UUID, groupId: UUID): Map<UUID, AttendanceRecord>
```

Chave do mapa = `gameId`. Jogo sem resposta do ator fica fora do mapa.

## Fora do escopo

- Paginação, limite, filtro e ordenação da listagem: nada muda (`JdbcGameOccurrenceRepository.kt:126` fica intacto).
- Mobile: nenhum arquivo em `mobile/`.
- Migração de banco: nenhuma. Nenhum arquivo novo em `backend/features/*/src/main/resources/db/migration/`. (Última migração de `groups` em `origin/main`: `V81__index_pending_charges_by_member.sql`.)
- `AccessSessionConfiguration.kt` e qualquer outro arquivo de `backend/bootstrap/`: não tocar.
- `GameQueries.kt` (`ListGames`, `GetGame`, `GameView`, `GameQueryRepository`): não tocar.
- `counts` (`JdbcAttendanceCommandRepository.kt:289-303`, em consulta única desde `7ecaa08b`): não tocar.
- Campos de `GameResponse` e de `AttendanceEntryResponse`: nenhum removido, nenhum renomeado, nenhum acrescentado.
- Status HTTP e assinatura pública de `GameController.list`: inalterados.
- `README.md` e demais documentos: não tocar.

## Contrato

Endpoint: `GET /api/groups/{groupId}/games` → `200`, corpo = array de `GameResponse`. Status de erro inalterados (`401` sem token, `404` para quem não é do grupo).

Forma de `ownAttendance` (idêntica à do `GET /api/groups/{groupId}/games/{gameId}`):

| Campo | Tipo | Valor |
|---|---|---|
| `memberId` | string (UUID) | id do ator autenticado |
| `status` | string | `CONFIRMED`, `DECLINED`, `WAITLISTED` |
| `waitlistPosition` | number, `null` | preenchido só quando `status = WAITLISTED` (coluna `waitlist_sequence`); `null` nos demais |
| `version` | number | versão da linha de presença |

Sem resposta do ator: `"ownAttendance": null`, com a chave presente. O Jackson do projeto não tem configuração de omitir nulos, e o read já se comporta assim (`bruno/Games/03 Read Game.bru:15` exige a propriedade).

ANTES (hoje) — todo item com `ownAttendance` nulo:

```json
[
  {
    "id": "5b0e6c1e-6f0e-4c58-9a0e-1f4f0a6f7c11",
    "groupId": "0d6f3a52-7a57-4a39-b3b0-2f1c8f1f6a01",
    "title": "Treino semanal",
    "venue": { "venueId": null, "name": "Arena Central", "address": "Rua das Flores 100", "court": "Quadra 2" },
    "localDate": "2026-09-23",
    "localTime": "19:30:00",
    "zoneId": "America/Sao_Paulo",
    "startsAt": "2026-09-23T22:30:00Z",
    "durationMinutes": 90,
    "capacity": 24,
    "confirmationDeadline": "2026-09-23T19:30:00Z",
    "gameFeeCents": 2500,
    "notes": null,
    "status": "PUBLISHED",
    "version": 3,
    "confirmedCount": 12,
    "availableSpots": 12,
    "waitlistCount": 0,
    "financeReviewRequired": false,
    "ownAttendance": null
  }
]
```

DEPOIS — mesmos campos; `ownAttendance` preenchido nos jogos em que o ator respondeu:

```jsonc
[
  {
    "id": "5b0e6c1e-6f0e-4c58-9a0e-1f4f0a6f7c11",
    "groupId": "0d6f3a52-7a57-4a39-b3b0-2f1c8f1f6a01",
    "title": "Treino semanal",
    "venue": { "venueId": null, "name": "Arena Central", "address": "Rua das Flores 100", "court": "Quadra 2" },
    "localDate": "2026-09-23",
    "localTime": "19:30:00",
    "zoneId": "America/Sao_Paulo",
    "startsAt": "2026-09-23T22:30:00Z",
    "durationMinutes": 90,
    "capacity": 24,
    "confirmationDeadline": "2026-09-23T19:30:00Z",
    "gameFeeCents": 2500,
    "notes": null,
    "status": "PUBLISHED",
    "version": 3,
    "confirmedCount": 12,
    "availableSpots": 12,
    "waitlistCount": 0,
    "financeReviewRequired": false,
    "ownAttendance": { "memberId": "9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f", "status": "CONFIRMED", "waitlistPosition": null, "version": 1 }
  },
  // os três itens abaixo têm os mesmos 19 campos do primeiro; só `ownAttendance` muda
  { "id": "…", "ownAttendance": { "memberId": "9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f", "status": "DECLINED", "waitlistPosition": null, "version": 3 } },
  { "id": "…", "ownAttendance": { "memberId": "9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f", "status": "WAITLISTED", "waitlistPosition": 2, "version": 2 } },
  { "id": "…", "ownAttendance": null }
]
```

Compatibilidade: a chave `ownAttendance` já vinha em todo item (sempre `null`). O cliente mobile atual decodifica a lista com `ignoreUnknownKeys = true` (`mobile/core/network/src/commonMain/kotlin/br/com/saqz/network/NetworkErrorMapper.kt:36-39`) e o `GameTransport` dele nem declara o campo: clientes antigos seguem funcionando.

Leitura do chip no mobile (ticket C4, só referência): `CONFIRMED` → "Você vai" · `null` → "Sem resposta" · `DECLINED` → "Não vai" · `WAITLISTED` → "Na espera".

Documentação de API: **não há** OpenAPI, Swagger nem `docs/api` no repositório. **Há** coleção Bruno: `bruno/Games/02 List Games.bru` — atualizada no Passo 6.

## Arquivos

Lista FECHADA. Nenhum arquivo é criado; seis são editados.

| # | Arquivo | Ação |
|---|---|---|
| 1 | `backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/attendance/AttendanceCommandPorts.kt` | editar |
| 2 | `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/attendance/JdbcAttendanceCommandRepository.kt` | editar |
| 3 | `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/attendance/JdbcAttendanceCommandRepositoryIntegrationTest.kt` | editar |
| 4 | `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/input/http/GameController.kt` | editar |
| 5 | `backend/features/groups/src/test/kotlin/br/com/saqz/groups/adapter/input/http/GameControllerTest.kt` | editar |
| 6 | `bruno/Games/02 List Games.bru` | editar |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

## Passo a passo

Ordem de execução = ordem dos commits. Cada commit compila sozinho.

### Commit 1 — `feat(games): consulta única da minha presença nos jogos do grupo (VUL-XXX)`

#### Passo 1 — porta · `AttendanceCommandPorts.kt` (linhas 110-112)

Localize o trecho literal:

```kotlin
fun interface AttendanceDetailQuery {
    fun find(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceDetail?
}
```

Substitua por:

```kotlin
fun interface AttendanceDetailQuery {
    fun find(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceDetail?

    // As respostas do próprio ator nos jogos do grupo, por id do jogo, numa consulta só.
    // Jogo sem resposta fica fora do mapa.
    // ponytail: corpo padrão vazio para os fakes de teste não mudarem; o único adapter real
    // (JdbcAttendanceCommandRepository) sobrescreve. Tirar o padrão se surgir um segundo adapter.
    fun ownByGame(actorId: UUID, groupId: UUID): Map<UUID, AttendanceRecord> = emptyMap()
}
```

Nenhum import novo: `UUID` e `AttendanceRecord` já estão no arquivo.

#### Passo 2 — consulta · `JdbcAttendanceCommandRepository.kt`

**2a.** Localize a linha literal (linha 264, única no arquivo):

```kotlin
    override fun roster(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceRoster? {
```

Substitua por (o método novo entra logo antes dela; a linha do `roster` permanece):

```kotlin
    override fun ownByGame(actorId: UUID, groupId: UUID): Map<UUID, AttendanceRecord> =
        jdbc.sql(OWN_BY_GAME)
            .param("actor", actorId)
            .param("group", groupId)
            .query { rs, _ ->
                AttendanceRecord(
                    rs.getObject("game_id", UUID::class.java), groupId, actorId,
                    AttendanceStatus.valueOf(rs.getString("status")),
                    rs.getObject("waitlist_sequence", Long::class.javaObjectType),
                    rs.getTimestamp("responded_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getLong("version"),
                )
            }
            .list()
            .associateBy(AttendanceRecord::gameId)

    override fun roster(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceRoster? {
```

**2b.** No `companion object` do mesmo arquivo, localize a linha literal (linha 478 antes do 2a, única no arquivo):

```kotlin
        const val ROSTER = """
```

Substitua por (a constante nova entra logo antes dela; a linha do `ROSTER` permanece):

```kotlin
        // ponytail: traz as respostas do ator em todos os jogos do grupo (uma linha por jogo
        // respondido), não só nos jogos visíveis; a listagem não pagina. Se paginar, filtrar
        // por a.game_id IN (:games).
        const val OWN_BY_GAME = """
            SELECT a.game_id,a.status,a.waitlist_sequence,a.responded_at,a.updated_at,a.version
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
            JOIN game_attendance a ON a.game_id=g.id AND a.member_user_id=:actor
            WHERE g.group_id=:group
        """
        const val ROSTER = """
```

Nenhum import novo: `AttendanceRecord`, `AttendanceStatus`, `UUID` e `JdbcClient` já estão no arquivo.

#### Passo 3 — testes de integração · `JdbcAttendanceCommandRepositoryIntegrationTest.kt`

Aplique os blocos **3a a 3d** da seção **Testes › Integração (JDBC)**.

Rode o gate G2 (seção Gates). Verde → commit 1 com os arquivos 1, 2 e 3.

### Commit 2 — `feat(games): listagem de jogos devolve ownAttendance do ator (VUL-XXX)`

#### Passo 4 — controller · `GameController.kt`

**4a.** Localize a linha literal (linha 12):

```kotlin
import br.com.saqz.groups.application.attendance.AttendanceDetailQuery
```

Substitua por:

```kotlin
import br.com.saqz.groups.application.attendance.AttendanceDetailQuery
import br.com.saqz.groups.application.attendance.AttendanceRecord
```

**4b.** Localize o trecho literal (linhas 84-89):

```kotlin
    @GetMapping("/api/groups/{groupId}/games")
    fun list(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String): List<GameResponse> =
        when (val result = listGames.execute(actors.resolve(identity), uuid(groupId))) {
            is GameListResult.Success -> result.games.map(GameView::toResponse)
            GameListResult.GroupNotFound -> throw GameNotFoundException()
        }
```

Substitua por:

```kotlin
    @GetMapping("/api/groups/{groupId}/games")
    fun list(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String): List<GameResponse> {
        val actor = actors.resolve(identity); val group = uuid(groupId)
        return when (val result = listGames.execute(actor, group)) {
            is GameListResult.Success -> {
                val own = attendance?.ownByGame(actor, group).orEmpty()
                result.games.map { it.toResponse().copy(ownAttendance = own[it.game.id]?.toEntryResponse()) }
            }
            GameListResult.GroupNotFound -> throw GameNotFoundException()
        }
    }
```

A consulta de presença fica dentro do ramo `Success`: quem não é do grupo cai em `GroupNotFound` antes de qualquer leitura de presença.

**4c.** Localize a linha literal (linha 96 antes do 4b):

```kotlin
                result.game.toResponse().copy(ownAttendance = attendance?.find(actor, group, game)?.own?.let { AttendanceEntryResponse(it.memberId, it.status.name, it.waitlistSequence, it.version) }),
```

Substitua por:

```kotlin
                result.game.toResponse().copy(ownAttendance = attendance?.find(actor, group, game)?.own?.toEntryResponse()),
```

**4d.** Acrescente ao **final do arquivo**, logo depois da última linha (a que começa com `private fun GameView.toResponse(): GameResponse {`):

```kotlin
private fun AttendanceRecord.toEntryResponse() = AttendanceEntryResponse(memberId, status.name, waitlistSequence, version)
```

#### Passo 5 — testes do controller · `GameControllerTest.kt`

Aplique os blocos **5a a 5d** da seção **Testes › Unidade (controller)**.

Rode o gate G1. Verde → commit 2 com os arquivos 4 e 5.

### Commit 3 — `docs(bruno): List Games confere ownAttendance por item (VUL-XXX)`

#### Passo 6 — coleção Bruno · `bruno/Games/02 List Games.bru`

Substitua o **conteúdo inteiro** do arquivo por:

```
meta {
  name: List Games
  type: http
  seq: 2
}
get {
  url: {{baseUrl}}/api/groups/{{groupId}}/games
  body: none
  auth: bearer
}
auth:bearer {
  token: {{idToken}}
}
tests {
  test("bounded game list", function () { expect(res.status).to.equal(200); expect(res.body).to.be.an("array"); });
  test("each game carries the callers own attendance in the read shape", function () {
    res.body.forEach(function (game) {
      expect(game).to.have.property("ownAttendance");
      if (game.ownAttendance !== null) {
        expect(game.ownAttendance.memberId).to.be.a("string");
        expect(game.ownAttendance.status).to.be.oneOf(["CONFIRMED", "DECLINED", "WAITLISTED"]);
        expect(game.ownAttendance).to.have.property("waitlistPosition");
        expect(game.ownAttendance.version).to.be.a("number");
      }
    });
  });
}
```

Commit 3 com o arquivo 6. Depois rode G3 e G4.

## Testes

Arquivos de teste existentes que servem de molde (estrutura seguida à risca):

- Controller: `backend/features/groups/src/test/kotlin/br/com/saqz/groups/adapter/input/http/GameControllerTest.kt` — controller real + `MemoryRepository` em memória; o read de presença já é coberto ali por `read includes only callers own attendance` (linha 41).
- Repositório JDBC de presença: `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/attendance/JdbcAttendanceCommandRepositoryIntegrationTest.kt` — Postgres embutido via `TestPostgres.migrated(...)`, banco novo por teste.
- Contagem de statements: `CountingDataSource` de `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/group/read/JdbcGroupReadRepositoryIntegrationTest.kt:382-403`, usado em `one read executes exactly one bounded SQL statement` (linha 148). Ele é `private` naquela classe; este ticket leva uma cópia enxuta para a classe de teste de presença (bloco 3d).
- `JdbcGameOccurrenceRepositoryIntegrationTest.kt` **não** recebe teste novo: a consulta nova não mora em `JdbcGameOccurrenceRepository`.

Cobertura exigida × teste:

| Exigência | Teste |
|---|---|
| (1) lista devolve CONFIRMED / DECLINED / WAITLISTED + `waitlistPosition` | C1, J1 |
| (2) jogo sem resposta → `null`, igual ao read | C2, C3, J2 |
| (3) presença de outro membro não vaza | J3, J4, C1 (ator e grupo passados à consulta) |
| (4) nº de consultas não cresce com o nº de jogos | J7 (listagem inteira: mesmo custo com 1 e com 6 jogos), J6 (1 statement por chamada de `ownByGame`), C6 (1 chamada por request) |
| Draft oculto não expõe presença | C4 |
| Não-membro: `404` sem ler presença | C5 |
| Grupo apagado | J5 |

### Integração (JDBC) — `JdbcAttendanceCommandRepositoryIntegrationTest.kt`

Helpers EXISTENTES usados, pelo nome real: `fixture(subject)` (linha 367; devolve `Fixture(owner, member, group, game, service)` com 1 jogo `PUBLISHED`), `attendance(f, member, status)` (linha 396), `waitlist(f, member, sequence)` (linha 397), `member(group, subject)` (linha 400), `execute(sql)` (linha 405), `Fixture.copy(game = …)` (data class, linha 413). Helper novo: `extraGame(f, days)` (bloco 3c).

**3a.** Imports — quatro substituições. Localize o trecho literal (linhas 4-5):

```kotlin
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
```

Substitua por:

```kotlin
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
```

Localize a linha literal (linha 7 antes da edição acima):

```kotlin
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
```

Substitua por:

```kotlin
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.game.ListGames
```

Localize o trecho literal (linhas 14-15 antes das edições acima):

```kotlin
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
```

Substitua por:

```kotlin
import org.springframework.jdbc.datasource.AbstractDataSource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.lang.reflect.Proxy
import java.sql.Connection
```

Localize o trecho literal (linhas 20-21 antes das edições acima):

```kotlin
import java.util.concurrent.Executors
import kotlin.test.assertEquals
```

Substitua por:

```kotlin
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.assertEquals
```

**3b.** Testes. Localize a linha literal (linha 85, única no arquivo):

```kotlin
    // --- VUL-152: ordenação da reserva por faixa + FIFO e colapso pós-prazo ---
```

Substitua por (os sete testes novos entram logo antes dela; a linha do VUL-152 permanece):

```kotlin
    // --- VUL-XXX: minha presença na listagem de jogos ---
    @Test fun `own by game returns the actors status waitlist position and version for each answered game`() {
        val f = fixture()
        val declined = f.copy(game = extraGame(f, 3))
        val waitlisted = f.copy(game = extraGame(f, 4))
        attendance(f, f.member, "CONFIRMED")
        attendance(declined, f.member, "DECLINED")
        execute("UPDATE game_attendance SET version=5 WHERE game_id='${declined.game}' AND member_user_id='${f.member}'")
        waitlist(waitlisted, member(f.group, "ahead"), 1)
        waitlist(waitlisted, f.member, 2)

        val own = JdbcAttendanceCommandRepository(dataSource).ownByGame(f.member, f.group)

        assertEquals(setOf(f.game, declined.game, waitlisted.game), own.keys)
        assertEquals(AttendanceStatus.CONFIRMED, own.getValue(f.game).status)
        assertNull(own.getValue(f.game).waitlistSequence)
        assertEquals(1L, own.getValue(f.game).version)
        assertEquals(AttendanceStatus.DECLINED, own.getValue(declined.game).status)
        assertNull(own.getValue(declined.game).waitlistSequence)
        assertEquals(5L, own.getValue(declined.game).version)
        assertEquals(AttendanceStatus.WAITLISTED, own.getValue(waitlisted.game).status)
        assertEquals(2L, own.getValue(waitlisted.game).waitlistSequence)
        assertEquals(listOf(f.member), own.values.map { it.memberId }.distinct())
        assertEquals(listOf(f.group), own.values.map { it.groupId }.distinct())
    }

    @Test fun `own by game leaves out the games the actor has not answered`() {
        val f = fixture()
        val unanswered = extraGame(f, 3)
        attendance(f, f.member, "CONFIRMED")

        val own = JdbcAttendanceCommandRepository(dataSource).ownByGame(f.member, f.group)

        assertEquals(setOf(f.game), own.keys)
        assertNull(own[unanswered])
    }

    @Test fun `own by game never returns another members answer`() {
        val f = fixture()
        attendance(f, member(f.group, "other"), "CONFIRMED")
        waitlist(f, member(f.group, "queued"), 1)

        assertEquals(emptyMap(), JdbcAttendanceCommandRepository(dataSource).ownByGame(f.member, f.group))
    }

    @Test fun `own by game is scoped to the requested group`() {
        val f = fixture()
        val other = fixture("other")
        attendance(other, f.member, "CONFIRMED")
        val repository = JdbcAttendanceCommandRepository(dataSource)

        assertEquals(emptyMap(), repository.ownByGame(f.member, f.group))
        assertEquals(setOf(other.game), repository.ownByGame(f.member, other.group).keys)
    }

    @Test fun `own by game is empty once the group is deleted`() {
        val f = fixture()
        attendance(f, f.member, "CONFIRMED")
        execute("UPDATE access_groups SET deleted_at=now() WHERE id='${f.group}'")

        assertEquals(emptyMap(), JdbcAttendanceCommandRepository(dataSource).ownByGame(f.member, f.group))
    }

    @Test fun `own by game runs a single statement no matter how many games the group has`() {
        val f = fixture()
        attendance(f, f.member, "CONFIRMED")
        val counting = CountingDataSource(dataSource)
        val repository = JdbcAttendanceCommandRepository(counting)

        val withOneGame = repository.ownByGame(f.member, f.group)
        assertEquals(1, counting.preparedStatements.get())
        (3..7).forEach { attendance(f.copy(game = extraGame(f, it)), f.member, "CONFIRMED") }
        val withSixGames = repository.ownByGame(f.member, f.group)

        assertEquals(1, withOneGame.size)
        assertEquals(6, withSixGames.size)
        assertEquals(2, counting.preparedStatements.get())
    }

    @Test fun `listing games with own attendance costs the same statements for one game and for six`() {
        val f = fixture()
        attendance(f, f.member, "CONFIRMED")
        val counting = CountingDataSource(dataSource)
        val attendanceRepository = JdbcAttendanceCommandRepository(counting)
        val listGames = ListGames(JdbcGameOccurrenceRepository(counting), attendanceRepository)
        fun cost(): Int {
            val before = counting.preparedStatements.get()
            listGames.execute(f.member, f.group)
            attendanceRepository.ownByGame(f.member, f.group)
            return counting.preparedStatements.get() - before
        }

        val withOneGame = cost()
        (3..7).forEach { attendance(f.copy(game = extraGame(f, it)), f.member, "CONFIRMED") }

        // role + list + counts + ownByGame
        assertEquals(4, withOneGame)
        assertEquals(withOneGame, cost())
    }

    // --- VUL-152: ordenação da reserva por faixa + FIFO e colapso pós-prazo ---
```

**3c + 3d.** Helper e contador. Localize a linha literal (linha 412, única no arquivo):

```kotlin
    private fun connection(): Connection = dataSource.connection
```

Substitua por (as novidades entram logo depois dela):

```kotlin
    private fun connection(): Connection = dataSource.connection
    // Jogo PUBLISHED a mais no grupo do fixture. `days` >= 3 e distinto por chamada: o fixture ocupa
    // now() + 2 dias e games_schedule_start_unique proíbe dois jogos ativos no mesmo início.
    private fun extraGame(f: Fixture, days: Int): UUID { val id = UUID.randomUUID(); execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$id','${f.group}','Treino $days',(CURRENT_DATE + $days),TIME '19:30','America/Sao_Paulo',now() + interval '$days days',90,now() + interval '${days - 1} days','Arena','Rua Central 100',2,2500,'PUBLISHED',now(),now())"); return id }
    // ponytail: cópia enxuta do CountingDataSource de JdbcGroupReadRepositoryIntegrationTest (lá ele é
    // private). Extrair para br.com.saqz.groups.testing quando um terceiro teste precisar contar statements.
    private class CountingDataSource(private val delegate: DataSource) : AbstractDataSource() {
        val preparedStatements = AtomicInteger()
        override fun getConnection(): Connection = wrap(delegate.connection)
        override fun getConnection(username: String, password: String): Connection = wrap(delegate.getConnection(username, password))
        private fun wrap(connection: Connection): Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, arguments ->
            if (method.name == "prepareStatement") preparedStatements.incrementAndGet()
            method.invoke(connection, *(arguments ?: emptyArray()))
        } as Connection
    }
```

Ficha de cada teste de integração:

| Id | Nome exato | Arranjo | Asserções |
|---|---|---|---|
| J1 | `own by game returns the actors status waitlist position and version for each answered game` | `fixture()`; 2 × `extraGame`; `attendance(f, f.member, "CONFIRMED")`; `attendance(declined, f.member, "DECLINED")` + `UPDATE … version=5`; `waitlist(waitlisted, member(f.group, "ahead"), 1)`; `waitlist(waitlisted, f.member, 2)` | chaves = os 3 jogos; CONFIRMED com `waitlistSequence` nulo e `version` 1; DECLINED com `waitlistSequence` nulo e `version` 5; WAITLISTED com `waitlistSequence` 2 (o 1 é de outro membro); todo `memberId` = `f.member`; todo `groupId` = `f.group` |
| J2 | `own by game leaves out the games the actor has not answered` | `fixture()`; `extraGame(f, 3)` sem resposta; `attendance(f, f.member, "CONFIRMED")` | chaves = `setOf(f.game)`; `own[unanswered]` nulo |
| J3 | `own by game never returns another members answer` | `fixture()`; `attendance(f, member(f.group, "other"), "CONFIRMED")`; `waitlist(f, member(f.group, "queued"), 1)`; o ator não responde | resultado = `emptyMap()` |
| J4 | `own by game is scoped to the requested group` | `fixture()` + `fixture("other")`; `attendance(other, f.member, "CONFIRMED")` | grupo `f.group` → `emptyMap()`; grupo `other.group` → chaves = `setOf(other.game)` |
| J5 | `own by game is empty once the group is deleted` | `fixture()`; `attendance(f, f.member, "CONFIRMED")`; `UPDATE access_groups SET deleted_at=now()` | resultado = `emptyMap()` |
| J6 | `own by game runs a single statement no matter how many games the group has` | `fixture()`; `JdbcAttendanceCommandRepository(CountingDataSource(dataSource))`; 1ª chamada com 1 jogo; `(3..7)` → 5 × `extraGame` respondidos; 2ª chamada com 6 jogos | contador = 1 após a 1ª chamada; tamanhos 1 e 6; contador = 2 após a 2ª chamada |
| J7 | `listing games with own attendance costs the same statements for one game and for six` | `fixture()`; um `CountingDataSource` compartilhado por `JdbcGameOccurrenceRepository` e `JdbcAttendanceCommandRepository`; `ListGames` real; `cost()` = `listGames.execute(f.member, f.group)` + `ownByGame(f.member, f.group)`, que é o que `GameController.list` executa; mede com 1 jogo, cria 5 × `extraGame` respondidos, mede de novo | custo com 1 jogo = 4 (`role` + `list` + `counts` + `ownByGame`); custo com 6 jogos = custo com 1 jogo |

### Unidade (controller) — `GameControllerTest.kt`

Helpers EXISTENTES usados, pelo nome real: `seed(status)` (linha 56), `MemoryRepository.role` (linha 61), `controller`, `ID`, `START`, `actor`, `group`. Novidades: propriedade `attendance`, helper `answer(...)`, fake `FakeAttendance` (blocos 5a, 5b, 5d).

**5a.** Localize a linha literal (linha 20):

```kotlin
    private lateinit var effects: RecordingEffects; private lateinit var controller: GameController
```

Substitua por:

```kotlin
    private lateinit var effects: RecordingEffects; private lateinit var controller: GameController
    private lateinit var attendance: FakeAttendance
```

**5b.** Localize a linha literal (linha 26):

```kotlin
        val attendance = AttendanceDetailQuery { _, _, gameId -> AttendanceDetail(AttendanceRecord(gameId, group, actor, AttendanceStatus.WAITLISTED, 4, START, START, 2), 3, 21, 2, 24, 1) }
```

Substitua por:

```kotlin
        attendance = FakeAttendance()
```

A linha 27 (`controller = GameController(… , attendance)`) fica como está: `attendance` passa a ser a propriedade da classe.

**5c.** Testes. Localize a linha literal (linha 42):

```kotlin
    @Test fun `athlete draft read is hidden`() { val game=seed(); repository.role=GroupRole.ATHLETE; assertFailsWith<GameNotFoundException>{controller.read(ID,"$group","${game.id}")} }
```

Substitua por (os seis testes novos entram logo antes dela; a linha do `athlete draft read is hidden` permanece):

```kotlin
    @Test fun `list includes the callers own attendance for every status`() {
        val confirmed=seed(GameStatus.PUBLISHED); val declined=seed(GameStatus.PUBLISHED); val waitlisted=seed(GameStatus.PUBLISHED)
        attendance.own=mapOf(confirmed.id to answer(confirmed,AttendanceStatus.CONFIRMED,null,1), declined.id to answer(declined,AttendanceStatus.DECLINED,null,3), waitlisted.id to answer(waitlisted,AttendanceStatus.WAITLISTED,4,2))
        val listed=controller.list(ID,"$group").associateBy { it.id }
        assertEquals(AttendanceEntryResponse(actor,"CONFIRMED",null,1),listed.getValue(confirmed.id).ownAttendance)
        assertEquals(AttendanceEntryResponse(actor,"DECLINED",null,3),listed.getValue(declined.id).ownAttendance)
        assertEquals(AttendanceEntryResponse(actor,"WAITLISTED",4,2),listed.getValue(waitlisted.id).ownAttendance)
        assertEquals(actor to group,attendance.lastOwnKey)
    }
    @Test fun `list leaves own attendance null for a game the caller has not answered`() { val answered=seed(GameStatus.PUBLISHED); val unanswered=seed(GameStatus.PUBLISHED); attendance.own=mapOf(answered.id to answer(answered,AttendanceStatus.CONFIRMED,null,1)); val listed=controller.list(ID,"$group").associateBy { it.id }; assertNotNull(listed.getValue(answered.id).ownAttendance); assertNull(listed.getValue(unanswered.id).ownAttendance) }
    @Test fun `list own attendance equals what the read returns for the same game`() { val game=seed(GameStatus.PUBLISHED); attendance.own=mapOf(game.id to answer(game,AttendanceStatus.WAITLISTED,4,2)); val fromRead=controller.read(ID,"$group","${game.id}").body!!.ownAttendance; assertNotNull(fromRead); assertEquals(fromRead,controller.list(ID,"$group").single().ownAttendance) }
    @Test fun `athlete list never exposes own attendance of a hidden draft`() { val draft=seed(); val published=seed(GameStatus.PUBLISHED); attendance.own=mapOf(draft.id to answer(draft,AttendanceStatus.CONFIRMED,null,1)); repository.role=GroupRole.ATHLETE; val listed=controller.list(ID,"$group"); assertEquals(listOf(published.id),listed.map { it.id }); assertNull(listed.single().ownAttendance) }
    @Test fun `nonmember list never reads own attendance`() { repository.role=null; assertFailsWith<GameNotFoundException>{controller.list(ID,"$group")}; assertEquals(0,attendance.ownCalls) }
    @Test fun `list reads own attendance once no matter how many games it returns`() { repeat(5){ seed(GameStatus.PUBLISHED) }; assertEquals(5,controller.list(ID,"$group").size); assertEquals(1,attendance.ownCalls) }
    @Test fun `athlete draft read is hidden`() { val game=seed(); repository.role=GroupRole.ATHLETE; assertFailsWith<GameNotFoundException>{controller.read(ID,"$group","${game.id}")} }
```

**5d.** Helper e fake. Localize a linha literal (linha 72):

```kotlin
    private companion object { val ID=RequestIdentity("subject",emailVerified=true,displayName="Player"); val DATE=LocalDate.of(2026,8,12); val START=Instant.parse("2026-08-12T22:30:00Z") }
```

Substitua por (helper e fake entram logo antes dela; a linha do `companion object` permanece):

```kotlin
    private fun answer(game: Game, status: AttendanceStatus, waitlist: Long?, version: Long) = AttendanceRecord(game.id, group, actor, status, waitlist, START, START, version)
    private inner class FakeAttendance : AttendanceDetailQuery {
        var own: Map<UUID, AttendanceRecord> = emptyMap(); var ownCalls = 0; var lastOwnKey: Pair<UUID, UUID>? = null
        override fun find(actorId: UUID, groupId: UUID, gameId: UUID) = AttendanceDetail(AttendanceRecord(gameId, group, actor, AttendanceStatus.WAITLISTED, 4, START, START, 2), 3, 21, 2, 24, 1)
        override fun ownByGame(actorId: UUID, groupId: UUID): Map<UUID, AttendanceRecord> { ownCalls++; lastOwnKey = actorId to groupId; return own }
    }
    private companion object { val ID=RequestIdentity("subject",emailVerified=true,displayName="Player"); val DATE=LocalDate.of(2026,8,12); val START=Instant.parse("2026-08-12T22:30:00Z") }
```

Nenhum import novo: o arquivo já importa `br.com.saqz.groups.application.attendance.*`, `AttendanceStatus`, `br.com.saqz.groups.domain.game.*` e `kotlin.test.*`; `AttendanceEntryResponse` é do mesmo pacote do teste.

Ficha de cada teste do controller:

| Id | Nome exato | Arranjo | Asserções |
|---|---|---|---|
| C1 | `list includes the callers own attendance for every status` | 3 × `seed(GameStatus.PUBLISHED)`; `attendance.own` com CONFIRMED (v1), DECLINED (v3), WAITLISTED posição 4 (v2) | cada item = `AttendanceEntryResponse(actor, "<STATUS>", <posição>, <versão>)` exato; `attendance.lastOwnKey == actor to group` (o controller só pede a presença do ator resolvido, no grupo da URL) |
| C2 | `list leaves own attendance null for a game the caller has not answered` | 2 × `seed(GameStatus.PUBLISHED)`; resposta só no primeiro | primeiro não nulo; segundo `ownAttendance` nulo |
| C3 | `list own attendance equals what the read returns for the same game` | 1 × `seed(GameStatus.PUBLISHED)`; `attendance.own` = WAITLISTED posição 4 v2 (mesmos valores que `FakeAttendance.find` entrega ao read) | `ownAttendance` do read não nulo; `ownAttendance` da lista `==` ao do read |
| C4 | `athlete list never exposes own attendance of a hidden draft` | `seed()` (DRAFT) com resposta em `attendance.own`; `seed(GameStatus.PUBLISHED)` sem resposta; `repository.role=GroupRole.ATHLETE` | lista = só o publicado; `ownAttendance` dele nulo |
| C5 | `nonmember list never reads own attendance` | `repository.role=null` | `GameNotFoundException`; `attendance.ownCalls == 0` |
| C6 | `list reads own attendance once no matter how many games it returns` | 5 × `seed(GameStatus.PUBLISHED)` | 5 itens; `attendance.ownCalls == 1` |

Testes existentes que continuam verdes sem edição: `owner list includes drafts and derived counts`, `athlete list hides drafts`, `nonmember list is privacy not found`, `read includes only callers own attendance` (o `FakeAttendance.find` devolve o mesmo valor do lambda antigo), `AttendanceControllerTest` (o `MemoryRepository` dele herda o corpo padrão de `ownByGame`) e `GroupCommunicationEndpointIntegrationTest` (constrói `GameController` sem `attendance`; `attendance?.ownByGame(...).orEmpty()` cobre o nulo).

## Gates

Todos os comandos rodam a partir da **raiz do repositório**, numa linha só (o `cd backend` faz parte do comando). O Postgres dos testes é embutido (zonky, módulo `backend/postgres-testing`) e sobe dentro da JVM do teste: `DOCKER_HOST` e `TESTCONTAINERS_RYUK_DISABLED` são o prefixo padrão desta máquina e ficam inertes aqui — **Docker parado não é bloqueio**. `JAVA_HOME` com JDK 21 é obrigatório (`backend/build.gradle.kts:5-7` aborta fora do 21).

**G1 — unidade do controller (rápido, após o Passo 5):**

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew :features:groups:test --tests 'br.com.saqz.groups.adapter.input.http.GameControllerTest'
```

**G2 — integração do repositório de presença (rápido, após o Passo 3):**

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew :features:groups:integrationTest --tests 'br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepositoryIntegrationTest'
```

**G3 — módulo inteiro (unidade + integração de `:features:groups`):**

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew :features:groups:test :features:groups:integrationTest
```

**G4 — check do backend (todos os módulos, `architecture-tests` e os testes HTTP do `bootstrap`; ~10 min, não interromper):**

```bash
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew check
```

**G5 — lista fechada e tamanho do diff:**

```bash
git diff --name-only origin/main...HEAD
git diff --shortstat origin/main...HEAD
```

Saída esperada do primeiro comando, exatamente estas seis linhas:

```
backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/attendance/JdbcAttendanceCommandRepositoryIntegrationTest.kt
backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/input/http/GameController.kt
backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/attendance/JdbcAttendanceCommandRepository.kt
backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/attendance/AttendanceCommandPorts.kt
backend/features/groups/src/test/kotlin/br/com/saqz/groups/adapter/input/http/GameControllerTest.kt
bruno/Games/02 List Games.bru
```

Saída esperada do segundo: por volta de 200 linhas somando adições e remoções (teto do PR: 2000).

Não há detekt nem ktlint no backend (`JvmBackendConventionPlugin.kt` só aplica `kotlin.jvm`): nenhum gate de lint a rodar. Nenhum gate de `mobile/` entra neste ticket.

Falha em gate:
- Teste desta receita vermelho → corrigir dentro dos seis arquivos da lista.
- Correção que pede arquivo fora da lista → parar e avisar o orquestrador.
- Teste vermelho que não pertence a nenhum arquivo desta receita → parar e avisar o orquestrador com o nome do teste e a saída. Não investigar.

## Critérios de aceite

- [ ] `GET /api/groups/{groupId}/games` devolve, em cada item, `ownAttendance` = `{memberId, status, waitlistPosition, version}` quando o ator respondeu àquele jogo, com `status` em `CONFIRMED` / `DECLINED` / `WAITLISTED` e `waitlistPosition` preenchido só em `WAITLISTED` (C1, J1 verdes).
- [ ] Jogo sem resposta do ator → `"ownAttendance": null`, igual ao read (C2, J2 verdes).
- [ ] O `ownAttendance` da lista é igual ao do read para o mesmo jogo, e os dois passam pelo mesmo `AttendanceRecord.toEntryResponse()` (C3 verde; `GameController.kt` sem nenhuma outra construção de `AttendanceEntryResponse`).
- [ ] Resposta de outro membro nunca aparece; resposta do ator em outro grupo nunca aparece (J3, J4 verdes).
- [ ] A presença custa **uma** consulta por request, qualquer que seja o número de jogos: uma chamada a `ownByGame` por `list` (C6) e um `prepareStatement` por chamada (J6).
- [ ] A listagem inteira custa 4 statements com 1 jogo e os mesmos 4 com 6 jogos (J7).
- [ ] Atleta não recebe presença de `Draft` oculto (C4); quem não é do grupo recebe `404` sem nenhuma leitura de presença (C5); grupo apagado → mapa vazio (J5).
- [ ] Nenhum campo removido, renomeado nem acrescentado em `GameResponse` e `AttendanceEntryResponse`; nenhum status HTTP alterado; assinatura de `GameController.list` inalterada.
- [ ] Nenhuma migração nova; `AccessSessionConfiguration.kt` e `GameQueries.kt` intactos.
- [ ] `bruno/Games/02 List Games.bru` com o teste `each game carries the callers own attendance in the read shape`.
- [ ] G1, G2, G3 e G4 verdes; G5 lista exatamente os seis arquivos e fica abaixo de 2000 linhas.
- [ ] Três commits, com as mensagens desta receita.

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-games-list-own-attendance origin/main` (XXX = número do ticket no Linear).
2. Commits pequenos em PT-BR no padrão do repo (`feat(games): ...`).
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo do PR.
4. PR contra `main`, aberto como ready (não draft), título `feat(games): minha presença na listagem de jogos do grupo (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
