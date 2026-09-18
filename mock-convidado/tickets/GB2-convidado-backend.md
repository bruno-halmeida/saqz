# GB2 · Backend: adicionar/tirar convidado, cascata, cancelamento e shapes (VUL-XXX)

**Onda 2 · depende de: GB1 (na main) · bloqueia: GD · paralelo com: GM2**

## Objetivo

O comportamento do convidado de jogo no backend, em cima do encanamento do GB1. Leia `CONTRATO.md`
(Decisões A–F e seção API) — é a especificação. Nada de tabela nova: convidado é uma linha de
`game_attendance` com `guest_seq > 0` cujo `member_user_id` é o ANFITRIÃO.

`G` = `backend/features/groups/src/main/kotlin/br/com/saqz/groups`
`T` = `backend/features/groups/src/test/kotlin/br/com/saqz/groups`
`IT` = `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups`

## Arquivos (lista FECHADA)

- EDITAR `G/domain/attendance/Attendance.kt`
- EDITAR `G/application/attendance/AttendanceCommandPorts.kt`, `RespondAttendance.kt`, `AdjustGameCapacity.kt`
- NOVO `G/application/attendance/GameGuests.kt`
- EDITAR `G/adapter/output/jdbc/attendance/JdbcAttendanceCommandRepository.kt`
- EDITAR `G/application/finance/charge/ChargeTransactions.kt`, `G/adapter/output/jdbc/finance/JdbcChargeTransactionRepository.kt`
- EDITAR `G/domain/finance/charge/Charge.kt`, `G/adapter/output/jdbc/finance/JdbcChargeManagementRepository.kt`
- EDITAR `G/adapter/input/http/AttendanceController.kt`, `G/adapter/input/http/ChargeController.kt`
- EDITAR o handler de exceções HTTP que hoje mapeia `AttendanceDeadlinePassedException` (ache com
  `git grep -n "AttendanceDeadlinePassedException" -- backend`; é 1 arquivo de advice)
- EDITAR `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/AccessSessionConfiguration.kt` (1 bean novo + controller)
- EDITAR testes: `T/domain/attendance/AttendanceTransitionPolicyTest.kt`, `T/adapter/input/http/AttendanceControllerTest.kt`
- NOVO `T/application/attendance/GameGuestsTest.kt`, NOVO `IT/adapter/output/jdbc/attendance/JdbcGameGuestIntegrationTest.kt`

Fora da lista = PARE, relate por SendMessage para `main` e aguarde. Fake de teste existente que deixar
de compilar por causa de método novo de interface: os métodos novos têm corpo padrão justamente para
isso não acontecer; se acontecer, PARE e relate.

## Passo 0

```
git fetch origin && git switch -c vul-XXX-convidado-backend origin/main
git grep -c "guest_seq" -- backend/features/groups/src/main/resources/db/migration   # >= 1 (GB1 na main); senão PARE
```
Primeiro commit logo após o passo 1.

## Passo 1 · política (`G/domain/attendance/Attendance.kt`)

1a. `AttendanceDecisionContext` ganha no fim `val guest: Boolean = false,`.
1b. `AttendanceDenial` ganha `HOST_NOT_GOING,`.
1c. Em `confirmationTarget`, no ramo `AttendanceStatus.DECLINED, null -> when {`, acrescente como
PRIMEIRA condição: `context.guest -> AttendanceStatus.WAITLISTED` com o comentário
`// Convidado entra sempre pela lista de espera (VUL-239), mesmo com vaga.`

Teste (em `AttendanceTransitionPolicyTest`, no estilo dos vizinhos): `guestAlwaysJoinsTheWaitlistEvenWithRoom`
(capacity 12, confirmed 3, `guest = true`, CONFIRM → WAITLISTED, `allocateWaitlistSequence`, sem cobrança) e
`guestPromotionCreatesTheCharge` (currentStatus WAITLISTED, `guest = true`, PROMOTE → CONFIRMED, `createGameCharge`).

## Passo 2 · portas (`AttendanceCommandPorts.kt`)

Em `AttendanceCommandRepository`, acrescente (todos com corpo padrão — fakes existentes não mudam):

```kotlin
    /** Próximo número de convidado do anfitrião neste jogo (linhas nunca são apagadas → max+1). */
    fun nextGuestSeq(gameId: UUID, hostId: UUID): Int = 1
    /** Convidados do anfitrião ainda no jogo (CONFIRMED ou WAITLISTED), travados para escrita. */
    fun activeGuests(groupId: UUID, gameId: UUID, hostId: UUID): List<AttendanceRecord> = emptyList()
    /** Como [save], mas gravando o nome digitado do convidado. */
    fun saveGuest(record: AttendanceRecord, guestName: String) = save(record)
```

Em `AttendanceChargePort` (continua `fun interface`), acrescente:
`fun guestRemoved(aggregate: AttendanceAggregate, actorId: UUID) {}`.

## Passo 3 · repositório JDBC (`JdbcAttendanceCommandRepository.kt`)

- `nextGuestSeq`: `SELECT coalesce(max(guest_seq),0)+1 FROM game_attendance WHERE game_id=:game AND member_user_id=:host` → `Int`.
- `activeGuests`: `SELECT game_id,group_id,member_user_id,guest_seq,status,waitlist_sequence,responded_at,updated_at,version FROM game_attendance WHERE group_id=:group AND game_id=:game AND member_user_id=:host AND guest_seq>0 AND status IN ('CONFIRMED','WAITLISTED') ORDER BY guest_seq FOR UPDATE` → `AttendanceRecord(..., guestSeq = rs.getInt("guest_seq"))`.
- `saveGuest(record, guestName)`: mesmo corpo do `save`, trocando o parâmetro `guestName` de `null` para o valor. Extraia um `private fun write(record, guestName: String?)` e faça `save` e `saveGuest` delegarem.
- `AttendanceChargeAdapter`: `override fun guestRemoved(aggregate, actorId) = charges.cancelGuest(aggregate.groupId, aggregate.gameId, aggregate.memberId, aggregate.guestSeq, actorId)`.

## Passo 4 · cobrança

`ChargeTransactionRepository` ganha `fun cancelGuestCharge(groupId:UUID,gameId:UUID,memberId:UUID,guestSeq:Int,actorId:UUID,now:Instant){}` (corpo padrão vazio).
`ChargeTransactions` ganha:

```kotlin
    fun cancelGuest(groupId:UUID,gameId:UUID,memberId:UUID,guestSeq:Int,actorId:UUID)=transaction.inTransaction{
        require(guestSeq>0);writeAccess.requireWrite(groupId);repository.cancelGuestCharge(groupId,gameId,memberId,guestSeq,actorId,now())
    }
```

`JdbcChargeTransactionRepository.cancelGuestCharge`: copie a mecânica de `reconcileGameCancellation` restrita a UMA
cobrança — `requireActiveGroup`; selecione o `id` com `WHERE group_id=:group AND game_id=:game AND member_user_id=:member
AND guest_seq=:guest AND kind='GAME' AND status='PENDING' AND electronic_order_id IS NULL FOR UPDATE`; se não houver, retorne;
`UPDATE ... SET status='CANCELLED',changed_by_user_id=:actor,version=version+1,updated_at=:now WHERE id=:id`; e
`event(find(id)!!, ChargeStatus.PENDING, ChargeStatus.CANCELLED, actorId, now)`. Cobrança PAID/WAIVED ou com pedido
eletrônico NÃO é tocada (o gestor resolve no caixa).

`Charge` (domínio) ganha no fim `val guestDisplayName:String?=null`. Em `JdbcChargeManagementRepository`: o `SELECT_CHARGE`
ganha `c.guest_display_name` e o `mapCharge` passa `guestDisplayName=rs.getString("guest_display_name")`.
`ChargeController`: `ChargeResponse` ganha no fim `val guestDisplayName:String?=null` e `ChargeWithEvents.response()` passa `charge.guestDisplayName`.

## Passo 5 · `RespondAttendance.kt` — cascata B e cancelamento F

5a. `apply(...)`: troque o `repository.save(record)` por
`if (aggregate.guestSeq > 0 && aggregate.current == null) repository.saveGuest(record, requireNotNull(aggregate.guestName)) else repository.save(record)`.

5b. Ainda em `apply`, substitua o bloco `val promoted = if (...) promoteOne(aggregate, timestamp) else null` +
`return ...` por:

```kotlin
        val leaving = decision.newStatus == AttendanceStatus.DECLINED
        val wasConfirmed = decision.oldStatus == AttendanceStatus.CONFIRMED
        if (leaving && wasConfirmed && aggregate.guestSeq > 0) charges.guestRemoved(aggregate, aggregate.actorId)
        // Anfitrião saiu: os convidados dele saem ANTES de qualquer promoção, senão a fila
        // promoveria um convidado que cai no passo seguinte.
        val freedByGuests = if (leaving && aggregate.guestSeq == 0) dropGuests(aggregate, timestamp) else 0
        val freed = freedByGuests + if (leaving && wasConfirmed) 1 else 0
        val promoted = if (aggregate.promotionMode == PromotionMode.FIFO) promoteFreed(aggregate, freed, timestamp) else emptyList()
        return AttendanceCommandResult.Success(record, promoted, event)
```

5c. Substitua `promoteOne` por:

```kotlin
    /** Promove até [freed] pessoas da fila; [aggregate].confirmedCount é a contagem ANTES das saídas. */
    private fun promoteFreed(aggregate: AttendanceAggregate, freed: Int, timestamp: Instant): List<AttendanceRecord> {
        var confirmed = (aggregate.confirmedCount - freed).coerceAtLeast(0)
        return buildList {
            repeat(freed) {
                val waiting = repository.earliestWaitlisted(aggregate.groupId, aggregate.gameId) ?: return@buildList
                val target = aggregate.copy(memberId = waiting.memberId, guestSeq = waiting.guestSeq, guestName = null, current = waiting, confirmedCount = confirmed)
                val result = promoteAttendance(target, AttendanceSource.SYSTEM, reason = null, repository = repository, charges = charges, timestamp = timestamp, ids = ids)
                if (result !is AttendancePromotionResult.Success) return@buildList
                add(result.attendance); confirmed++
            }
        }
    }

    /** Derruba os convidados ativos do anfitrião; devolve quantas vagas CONFIRMADAS eles liberaram. */
    private fun dropGuests(host: AttendanceAggregate, timestamp: Instant): Int =
        repository.activeGuests(host.groupId, host.gameId, host.memberId).count { guest ->
            val dropped = guest.copy(status = AttendanceStatus.DECLINED, waitlistSequence = null, updatedAt = maxOf(timestamp, guest.respondedAt), version = guest.version + 1)
            repository.save(dropped)
            repository.append(AttendanceEvent(ids(), host.gameId, host.groupId, host.memberId, host.actorId, AttendanceSource.SYSTEM, guest.status, AttendanceStatus.DECLINED, null, timestamp, guestSeq = guest.guestSeq))
            val confirmed = guest.status == AttendanceStatus.CONFIRMED
            if (confirmed) charges.guestRemoved(host.copy(guestSeq = guest.guestSeq, current = guest), host.actorId)
            confirmed
        }
```

5d. `promote(...)`: ganha o parâmetro `guestSeq: Int = 0` como ÚLTIMO (depois de `reason`, para os call sites posicionais do IT não quebrarem) e passa para `repository.lock(groupId, gameId, memberId, actorId, guestSeq)`.

5e. Torne `internal` (hoje `private`) para o `GameGuests` reusar: `apply`, `authorized`, `denied`. Nada mais.

`AdjustGameCapacity.forMember(record)`: acrescente os argumentos nomeados `guestSeq = record.guestSeq` no fim do
`AttendanceAggregate(...)` — sem isso a promoção por capacidade de um convidado cobraria a linha errada.

## Passo 6 · `G/application/attendance/GameGuests.kt` (arquivo inteiro)

```kotlin
package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.attendance.AttendanceDecision
import br.com.saqz.groups.domain.attendance.AttendanceDecisionContext
import br.com.saqz.groups.domain.attendance.AttendanceDenial
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceTransitionPolicy
import br.com.saqz.sharedkernel.group.GroupRole
import java.time.Instant
import java.util.UUID

/** Convidado de jogo (VUL-239): "+1" pelo nome, linha de presença do ANFITRIÃO com `guestSeq > 0`. */
class GameGuests(
    private val transaction: TransactionRunner,
    private val repository: AttendanceCommandRepository,
    private val responses: RespondAttendance,
    private val now: () -> Instant,
) {
    fun add(actorId: UUID, groupId: UUID, gameId: UUID, rawName: String?, requestId: UUID): AttendanceCommandResult =
        transaction.inTransaction {
            val name = guestName(rawName) ?: return@inTransaction AttendanceCommandResult.Denied(AttendanceDenial.REASON_INVALID)
            repository.findResponseReplay(groupId, gameId, actorId, requestId)?.let {
                return@inTransaction AttendanceCommandResult.Success(it.attendance, event = it.event)
            }
            val host = repository.lock(groupId, gameId, actorId, actorId)
                ?: return@inTransaction AttendanceCommandResult.Hidden
            if (host.actorRole == null) return@inTransaction AttendanceCommandResult.Hidden
            if (host.current?.status !in setOf(AttendanceStatus.CONFIRMED, AttendanceStatus.WAITLISTED)) {
                return@inTransaction AttendanceCommandResult.Denied(AttendanceDenial.HOST_NOT_GOING)
            }
            val guest = host.copy(current = null, guestSeq = repository.nextGuestSeq(gameId, actorId), guestName = name)
            decide(guest, AttendanceIntent.CONFIRM, AttendanceSource.SELF, null, requestId)
        }

    fun remove(actorId: UUID, groupId: UUID, gameId: UUID, hostId: UUID, guestSeq: Int): AttendanceCommandResult =
        transaction.inTransaction {
            if (guestSeq <= 0) return@inTransaction AttendanceCommandResult.Hidden
            val guest = repository.lock(groupId, gameId, hostId, actorId, guestSeq)
                ?: return@inTransaction AttendanceCommandResult.Hidden
            val current = guest.current ?: return@inTransaction AttendanceCommandResult.Hidden
            val organizer = guest.actorRole == GroupRole.OWNER || guest.actorRole == GroupRole.ADMIN
            val source = when {
                actorId == hostId -> AttendanceSource.SELF
                organizer -> AttendanceSource.ORGANIZER
                else -> return@inTransaction AttendanceCommandResult.Hidden
            }
            if (current.status == AttendanceStatus.DECLINED) return@inTransaction AttendanceCommandResult.Success(current)
            decide(guest, AttendanceIntent.DECLINE, source, ORGANIZER_REASON.takeIf { source == AttendanceSource.ORGANIZER }, null)
        }

    private fun decide(
        aggregate: AttendanceAggregate,
        intent: AttendanceIntent,
        source: AttendanceSource,
        reason: String?,
        requestId: UUID?,
    ): AttendanceCommandResult = when (val decision = AttendanceTransitionPolicy.decide(
        AttendanceDecisionContext(
            aggregate.gameStatus, aggregate.confirmationDeadline, now(), aggregate.capacity, aggregate.confirmedCount,
            aggregate.current?.status, source, reason, aggregate.membershipType, aggregate.mensalistaPriority, guest = true,
        ),
        intent,
    )) {
        is AttendanceDecision.Denied -> AttendanceCommandResult.Denied(decision.reason)
        is AttendanceDecision.Transition -> responses.apply(aggregate, decision, requestId)
    }

    private fun guestName(raw: String?): String? {
        val name = raw?.trim()?.takeUnless(String::isBlank) ?: return null
        if (name.codePointCount(0, name.length) !in 2..80) return null
        if (name.codePoints().anyMatch(Character::isISOControl)) return null
        return name
    }

    private companion object {
        const val ORGANIZER_REASON = "Convidado removido pelo gestor"
    }
}
```

Confira o import real de `GroupRole` e `TransactionRunner` copiando os de `RespondAttendance.kt`. O replay de `add`
reusa `findResponseReplay` (source SELF + requestId) — depois do GB1 ele já devolve a linha com o `guestSeq` certo.
`transaction.inTransaction` aninhado com o de `RespondAttendance` não existe aqui: `apply` não abre transação.

## Passo 7 · HTTP (`AttendanceController.kt` + advice + bean)

7a. `AttendanceRosterMemberResponse` ganha `val guestSeq: Int,` e `val hostDisplayName: String?,`; o
`AttendanceRosterMember.response()` passa os dois. `AttendancePromotionRequest` ganha
`@JsonProperty("guestSeq") val guestSeq: Int? = null,` e o `promote` chama `responses.promote(actor, uuid(groupId), uuid(gameId), member, requestId, request.reason, guestSeq = request.guestSeq ?: 0)`.

7b. Novos tipos: `data class GuestRequest @JsonCreator constructor(@JsonProperty("requestId") val requestId: UUID?, @JsonProperty("displayName") val displayName: String?)`
e `data class GuestResponse(val memberId: UUID, val guestSeq: Int, val displayName: String, val status: String, val waitlistPosition: Long?)`;
`class AttendanceHostNotGoingException : RuntimeException()`. O controller ganha a dependência `private val guests: GameGuests`.

7c. Endpoints:

```kotlin
    @PostMapping("/api/groups/{groupId}/games/{gameId}/attendance/guests")
    fun addGuest(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
        @RequestBody request: GuestRequest,
    ): ResponseEntity<GuestResponse> {
        val requestId = required(request.requestId, "requestId")
        val name = required(request.displayName, "displayName")
        return when (val result = guests.add(actors.resolve(identity), uuid(groupId), uuid(gameId), name, requestId)) {
            is AttendanceCommandResult.Success -> ResponseEntity.status(HttpStatus.CREATED).body(
                GuestResponse(result.attendance.memberId, result.attendance.guestSeq, name.trim(), result.attendance.status.name, result.attendance.waitlistSequence),
            )
            is AttendanceCommandResult.Denied -> denied(result.reason, "displayName")
            AttendanceCommandResult.Hidden -> throw GameNotFoundException()
            AttendanceCommandResult.Forbidden -> throw AccessForbiddenException()
        }
    }

    @DeleteMapping("/api/groups/{groupId}/games/{gameId}/attendance/guests/{hostId}/{guestSeq}")
    fun removeGuest(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
        @PathVariable hostId: String,
        @PathVariable guestSeq: Int,
    ): ResponseEntity<Void> = when (val result = guests.remove(actors.resolve(identity), uuid(groupId), uuid(gameId), uuid(hostId), guestSeq)) {
        is AttendanceCommandResult.Success -> ResponseEntity.noContent().build()
        is AttendanceCommandResult.Denied -> denied(result.reason, "guest")
        AttendanceCommandResult.Hidden -> throw GameNotFoundException()
        AttendanceCommandResult.Forbidden -> throw AccessForbiddenException()
    }

    private fun denied(reason: AttendanceDenial, field: String): Nothing = when (reason) {
        AttendanceDenial.HOST_NOT_GOING -> throw AttendanceHostNotGoingException()
        AttendanceDenial.DEADLINE_PASSED -> throw AttendanceDeadlinePassedException()
        AttendanceDenial.NOT_PUBLISHED, AttendanceDenial.FROZEN -> throw AttendanceFrozenException()
        else -> invalid(field)
    }
```
No `when (result.reason)` do `mutation(...)` existente, acrescente o ramo
`AttendanceDenial.HOST_NOT_GOING -> throw AttendanceHostNotGoingException()` (o `when` é exaustivo).

7d. No advice que trata `AttendanceDeadlinePassedException`, acrescente o tratamento de `AttendanceHostNotGoingException`
copiando-o linha a linha: MESMO status HTTP, código `ATTENDANCE_HOST_NOT_GOING`, título/detalhe
"Responda que vai ao jogo antes de levar um convidado."

7e. `AccessSessionConfiguration.kt`: ao lado do bean `respondAttendance`, acrescente
`@Bean fun gameGuests(transaction: JdbcTransactionRunner, repository: JdbcAttendanceCommandRepository, responses: RespondAttendance) = GameGuests(transaction, repository, responses, Instant::now)`
e, no bean/constructor que monta o `AttendanceController`, passe o `GameGuests` (ache com `git grep -n "AttendanceController(" -- backend`).

## Passo 8 · testes

`T/application/attendance/GameGuestsTest.kt` — com um repositório fake em memória (copie o fake do teste vizinho de
`RespondAttendance`, se existir em `T/application/attendance/`; senão escreva um mínimo que implemente `lock`, `save`,
`saveGuest`, `append`, `nextWaitlistSequence`, `earliestWaitlisted`, `nextGuestSeq`, `activeGuests`):
1. `guestJoinsTheWaitlistEvenWithRoom` · 2. `hostWithoutAnswerCannotBringAGuest` (→ `HOST_NOT_GOING`) ·
3. `hostDeclinedCannotBringAGuest` · 4. `afterTheDeadlineTheHostCannotAddOrRemove` (→ `DEADLINE_PASSED`) ·
5. `organizerRemovesAGuestAfterTheDeadline` · 6. `anotherMemberCannotRemove` (→ `Hidden`) ·
7. `invalidNamesAreRejected` (1 letra, 81 letras, só espaços, caractere de controle) · 8. `secondGuestGetsTheNextSeq`.

`IT/.../JdbcGameGuestIntegrationTest.kt` — banco de verdade, fixture copiada de `JdbcAttendanceCommandRepositoryIntegrationTest`:
1. `removingAConfirmedGuestCancelsThePendingChargeAndPromotesTheNext` (jogo com taxa, capacidade 2: host confirmado, convidado promovido
   pelo gestor → cobrança `guest_seq=1` PENDING com `guest_display_name`; remover → CANCELLED, próximo da fila CONFIRMED com cobrança própria).
2. `hostDecliningDropsEveryGuestBeforePromoting` (host CONFIRMED + convidado WAITLISTED em 1º + membro em 2º; host desiste → convidado
   DECLINED, o MEMBRO é promovido, nenhuma cobrança de convidado criada).
3. `rosterExposesGuestSeqAndHostName` · 4. `guestNeverCountsAsDeclinedOrPending` (detail: `declined_count`/`pending_count` iguais antes e depois de remover o convidado).
5. `capacityIncreasePromotesAGuestAndChargesTheGuestRow` (cobrança criada tem `guest_seq=1`, não 0).

`AttendanceControllerTest`: `addGuestReturns201WithTheWaitlistedGuest`, `addGuestWithoutHostAnswerReturnsHostNotGoingProblem`
(código `ATTENDANCE_HOST_NOT_GOING`), `removeGuestReturns204`, `rosterIncludesGuestFields`, `promoteForwardsGuestSeq`.

## Gates (da RAIZ, uma linha cada)

```
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew :features:groups:test --tests '*GameGuestsTest' --tests '*AttendanceTransitionPolicyTest' --tests '*AttendanceControllerTest'
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew :features:groups:integrationTest --tests '*JdbcGameGuestIntegrationTest'
cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew check
```

## PR

Título: `feat(attendance): convidado de jogo — adicionar, tirar, cascata e cobrança (VUL-XXX)`. Corpo: tabela
decisão (A–F) → onde está no código → teste que prova. Commits PT-BR terminando com
`Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`; PR ready com `GH_TOKEN=$(gh auth token --user bruno-halmeida)`,
corpo terminando com `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. PARE depois do PR; depois só `CORRECAO:`.
