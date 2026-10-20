package br.com.saqz.groups.application.invite.redeem

import br.com.saqz.groups.application.communication.EntryRequestNotice
import br.com.saqz.groups.application.communication.FirstMemberNotice
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteKind
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RedeemInviteTest {
    @Test
    fun `expired trial refuses pending athlete even if their occupied slot would bypass the limit`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60), entryRequiresApproval = true))
        fixture.repository.openMembers += actor
        val original = now.minusSeconds(30)
        fixture.repository.persistedRequestAt[actor] = original
        val service = RedeemInvite(fixture.transaction, fixture.repository, FixedSubscriptionLimits(athleteLimit = 0), fixture.clock,
            br.com.saqz.sharedkernel.subscription.GroupWriteAccess { false })
        kotlin.test.assertFailsWith<br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException> { service.execute(actor, code.value) }
        assertEquals(original, fixture.repository.persistedRequestAt[actor])
        assertTrue(fixture.repository.entryRequests.isEmpty())
        assertTrue(fixture.repository.roles.isEmpty())
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `expired plan refuses direct entry and a marked return without creating anything`() {
        val fixture = fixture()
        fixture.repository.returnMarked += actor
        val service = RedeemInvite(fixture.transaction, fixture.repository, FixedSubscriptionLimits(), fixture.clock,
            br.com.saqz.sharedkernel.subscription.GroupWriteAccess { false })

        kotlin.test.assertFailsWith<br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException> { service.execute(actor, code.value) }
        fixture.repository.returnMarked.clear()
        kotlin.test.assertFailsWith<br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException> { service.execute(actor, code.value) }

        assertTrue(fixture.repository.entryRequests.isEmpty())
        assertTrue(fixture.repository.redemptions.isEmpty())
        assertTrue(fixture.repository.invalidAttempts.isEmpty())
    }

    @Test
    fun `expired trial lets an existing member resolve their group without a new membership`() {
        val fixture = fixture()
        fixture.repository.roles[actor] = GroupRole.ATHLETE
        val service = RedeemInvite(fixture.transaction, fixture.repository, FixedSubscriptionLimits(athleteLimit = 0), fixture.clock,
            br.com.saqz.sharedkernel.subscription.GroupWriteAccess { false })
        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), service.execute(actor, code.value))
        assertEquals(mapOf(actor to GroupRole.ATHLETE), fixture.repository.roles)
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    private val now = Instant.parse("2026-07-16T18:00:00Z")
    private val actor = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val code = InviteCode.from(
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 5 }),
    )

    @Test
    fun `permanent invite is reusable decades later without duplicate memberships`() {
        val fixture = fixture(target = RedeemableInvite(groupId, null))
        fixture.clock.current = Instant.parse("2099-01-01T00:00:00Z")
        val secondActor = UUID.randomUUID()

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), fixture.useCase.execute(actor, code.value))
        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), fixture.useCase.execute(actor, code.value))
        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), fixture.useCase.execute(secondActor, code.value))
        assertEquals(mapOf(actor to GroupRole.ATHLETE, secondActor to GroupRole.ATHLETE), fixture.repository.roles)
        assertTrue(fixture.repository.invalidAttempts.isEmpty())
    }

    @Test
    fun `permanent invite still requires approval decades later`() {
        val fixture = fixture(target = RedeemableInvite(groupId, null, entryRequiresApproval = true))
        val future = Instant.parse("2099-01-01T00:00:00Z")
        fixture.clock.current = future

        assertEquals(RedeemInviteResult.Pending(groupId), fixture.useCase.execute(actor, code.value))
        assertEquals(listOf(CreateEntryRequestCommand(groupId, actor, future)), fixture.repository.entryRequests)
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `quem saiu ou foi removido volta por pedido mesmo com convite que entra direto`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60), entryRequiresApproval = false))
        fixture.repository.returnMarked += actor
        val requested = mutableListOf<Pair<UUID, UUID>>()
        val service = RedeemInvite(fixture.transaction, fixture.repository, FixedSubscriptionLimits(), fixture.clock,
            entryRequest = EntryRequestNotice { group, user -> requested += group to user })

        assertEquals(RedeemInviteResult.Pending(groupId), service.execute(actor, code.value))

        assertEquals(listOf(CreateEntryRequestCommand(groupId, actor, now)), fixture.repository.entryRequests)
        assertTrue(fixture.repository.redemptions.isEmpty())
        assertEquals(listOf(groupId to actor), requested)
    }

    @Test
    fun `saida antiga sem marca entra direto como antes`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60), entryRequiresApproval = false))

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), fixture.useCase.execute(actor, code.value))

        assertTrue(fixture.repository.entryRequests.isEmpty())
        assertEquals(listOf(RedeemMembershipCommand(groupId, actor)), fixture.repository.redemptions)
    }

    @Test
    fun `quem voltou com a marca ainda confere o limite de atletas antes do pedido`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60)), athleteLimit = 0)
        fixture.repository.returnMarked += actor

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))

        assertTrue(fixture.repository.entryRequests.isEmpty())
    }

    @Test
    fun `a marca de saida de outra pessoa nao afeta quem nunca saiu`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60)))
        fixture.repository.returnMarked += UUID.randomUUID()

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `valid invite creates athlete membership once`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), result)
        assertEquals(listOf(RedeemMembershipCommand(groupId, actor)), fixture.repository.redemptions)
    }

    @Test
    fun `approval-enabled invite creates a pending request without membership`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60), entryRequiresApproval = true))

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Pending(groupId), result)
        assertTrue(fixture.repository.redemptions.isEmpty())
        assertEquals(listOf(CreateEntryRequestCommand(groupId, actor, now)), fixture.repository.entryRequests)
    }

    @Test
    fun `repeating approval-enabled redeem keeps the original request timestamp`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60), entryRequiresApproval = true))

        assertEquals(RedeemInviteResult.Pending(groupId), fixture.useCase.execute(actor, code.value))
        fixture.clock.current = now.plusSeconds(30)
        assertEquals(RedeemInviteResult.Pending(groupId), fixture.useCase.execute(actor, code.value))

        assertEquals(2, fixture.repository.entryRequests.size)
        assertEquals(now, fixture.repository.entryRequests.first().requestedAt)
        assertEquals(now, fixture.repository.persistedRequestAt[actor])
    }

    @Test
    fun `existing member joins immediately even when approval is enabled`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60), entryRequiresApproval = true)).also {
            it.repository.roles[actor] = GroupRole.ADMIN
            it.repository.openMembers += actor
        }

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ADMIN), result)
        assertTrue(fixture.repository.entryRequests.isEmpty())
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `approval-enabled redeem checks athlete limit before creating the request`() {
        val fixture = fixture(
            target = RedeemableInvite(groupId, now.plusSeconds(60), entryRequiresApproval = true),
            athleteLimit = 0,
        )

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.entryRequests.isEmpty())
    }

    @Test
    fun `invite for deleted group has its own outcome without invalid attempt or membership`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60), groupDeleted = true))

        assertSame(RedeemInviteResult.GroupDeleted, fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.invalidAttempts.isEmpty())
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `valid invite lookup uses only SHA-256 digest`() {
        val fixture = fixture()

        fixture.useCase.execute(actor, code.value)

        assertEquals(listOf(InviteTokenDigest.sha256(code)), fixture.repository.lookups)
    }

    @Test
    fun `valid invite does not record or reset attempt window`() {
        val original = InviteAttemptWindow(now.minusSeconds(120), 4)
        val fixture = fixture().also { it.repository.windows[actor] = original }

        fixture.useCase.execute(actor, code.value)

        assertTrue(fixture.repository.invalidAttempts.isEmpty())
        assertEquals(original, fixture.repository.windows[actor])
    }

    @Test
    fun `existing owner role is preserved`() {
        assertPreservedRole(GroupRole.OWNER)
    }

    @Test
    fun `existing admin role is preserved`() {
        assertPreservedRole(GroupRole.ADMIN)
    }

    @Test
    fun `existing athlete retry is idempotent`() {
        val fixture = fixture().also {
            it.repository.roles[actor] = GroupRole.ATHLETE
            it.repository.openMembers += actor
        }

        val first = fixture.useCase.execute(actor, code.value)
        val second = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), first)
        assertEquals(first, second)
        assertEquals(1, fixture.repository.roles.size)
    }

    @Test
    fun `two actors can redeem the same reusable invite`() {
        val fixture = fixture()
        val secondActor = UUID.randomUUID()

        val first = fixture.useCase.execute(actor, code.value)
        val second = fixture.useCase.execute(secondActor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), first)
        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), second)
        assertEquals(setOf(actor, secondActor), fixture.repository.roles.keys)
        assertEquals(2, fixture.repository.lookups.size)
    }

    @Test
    fun `athlete entry within titular cap of 25 succeeds`() {
        val fixture = fixture(athleteLimit = 25).also {
            it.repository.openMembers += (1..24).map { UUID.randomUUID() }
        }

        assertEquals(
            RedeemInviteResult.Success(groupId, GroupRole.ATHLETE),
            fixture.useCase.execute(actor, code.value),
        )
    }

    @Test
    fun `athlete entry above titular cap of 25 is refused`() {
        val fixture = fixture(athleteLimit = 25).also {
            it.repository.openMembers += (1..25).map { UUID.randomUUID() }
        }

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `athletes who left the group no longer count toward the cap`() {
        // Quem saiu não está em group_memberships: 24 membros agora num limite de 25 deixam a vaga livre.
        val fixture = fixture(athleteLimit = 25).also {
            it.repository.openMembers += (1..24).map { UUID.randomUUID() }
        }

        assertTrue(fixture.useCase.execute(actor, code.value) is RedeemInviteResult.Success)
    }

    @Test
    fun `athlete cap of zero blocks entry for an owner without subscription`() {
        val fixture = fixture(athleteLimit = 0)

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `pending downgrade uses the lower athlete limit from the port`() {
        val fixture = fixture(athleteLimit = 25).also {
            it.repository.openMembers += (1..25).map { UUID.randomUUID() }
        }

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `owner redeeming own invite bypasses athlete cap`() {
        val fixture = fixture(athleteLimit = 0).also {
            it.repository.openMembers += (1..25).map { UUID.randomUUID() }
            it.repository.roles[ownerId] = GroupRole.OWNER
        }

        assertEquals(
            RedeemInviteResult.Success(groupId, GroupRole.OWNER),
            fixture.useCase.execute(ownerId, code.value),
        )
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `unknown invite records one invalid attempt`() {
        val fixture = fixture(target = null)

        assertSame(RedeemInviteResult.InvalidOrExpired, fixture.useCase.execute(actor, code.value))
        assertEquals(InviteAttemptWindow(now, 1), fixture.repository.windows[actor])
    }

    @Test
    fun `expired invite is invalid and records one invalid attempt`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.minusSeconds(1)))

        assertSame(RedeemInviteResult.InvalidOrExpired, fixture.useCase.execute(actor, code.value))
        assertEquals(1, fixture.repository.invalidAttempts.size)
        assertEquals(InviteAttemptWindow(now, 1), fixture.repository.windows[actor])
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `non-expired invite proceeds to membership redemption`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(1)))

        assertEquals(
            RedeemInviteResult.Success(groupId, GroupRole.ATHLETE),
            fixture.useCase.execute(actor, code.value),
        )
        assertEquals(listOf(RedeemMembershipCommand(groupId, actor)), fixture.repository.redemptions)
        assertTrue(fixture.repository.invalidAttempts.isEmpty())
    }

    @Test
    fun `malformed invite records failure without digest lookup`() {
        val fixture = fixture()

        assertSame(RedeemInviteResult.InvalidOrExpired, fixture.useCase.execute(actor, "malformed"))
        assertTrue(fixture.repository.lookups.isEmpty())
        assertEquals(1, fixture.repository.invalidAttempts.size)
    }

    @Test
    fun `expired and rotated invite reveal no group`() {
        val fixture = fixture(target = null)

        val expired = fixture.useCase.execute(actor, code.value)
        val rotated = fixture.useCase.execute(actor, code.value)

        assertSame(RedeemInviteResult.InvalidOrExpired, expired)
        assertSame(expired, rotated)
        assertTrue(expired.toString().contains("InvalidOrExpired"))
        assertTrue(!expired.toString().contains(groupId.toString()))
    }

    @Test
    fun `first ten invalid attempts are permitted`() {
        val fixture = fixture(target = null)

        val results = List(10) { fixture.useCase.execute(actor, code.value) }

        assertTrue(results.all { it === RedeemInviteResult.InvalidOrExpired })
        assertEquals(10, fixture.repository.windows.getValue(actor).invalidCount)
    }

    @Test
    fun `eleventh attempt in the window is rate limited without lookup`() {
        val fixture = fixture(target = null)
        repeat(10) { fixture.useCase.execute(actor, code.value) }
        fixture.repository.lookups.clear()

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.AttemptLimit(600), result)
        assertTrue(fixture.repository.lookups.isEmpty())
        assertEquals(10, fixture.repository.windows.getValue(actor).invalidCount)
    }

    @Test
    fun `rate limit reports full window at its start`() {
        val fixture = fixture().also { it.repository.windows[actor] = InviteAttemptWindow(now, 10) }

        assertEquals(RedeemInviteResult.AttemptLimit(600), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `rate limit rounds a partial final second up`() {
        val clockNow = now.plusMillis(599_100)
        val fixture = fixture(clockNow = clockNow).also {
            it.repository.windows[actor] = InviteAttemptWindow(now, 10)
        }

        assertEquals(RedeemInviteResult.AttemptLimit(1), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `attempt exactly at window end starts a fresh window`() {
        val fixture = fixture(target = null).also {
            it.repository.windows[actor] = InviteAttemptWindow(now.minus(Duration.ofMinutes(10)), 10)
        }

        assertSame(RedeemInviteResult.InvalidOrExpired, fixture.useCase.execute(actor, code.value))
        assertEquals(InviteAttemptWindow(now, 1), fixture.repository.windows[actor])
    }

    @Test
    fun `rate-limited actor cannot probe even a valid invite`() {
        val fixture = fixture().also { it.repository.windows[actor] = InviteAttemptWindow(now, 10) }

        assertEquals(RedeemInviteResult.AttemptLimit(600), fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.lookups.isEmpty())
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    private fun assertPreservedRole(role: GroupRole) {
        val fixture = fixture().also {
            it.repository.roles[actor] = role
            it.repository.openMembers += actor
        }

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, role), result)
        assertEquals(role, fixture.repository.roles[actor])
    }

    @Test
    fun `entrada imediata avisa o primeiro atleta com grupo e pessoa, dentro da transacao`() {
        val joined = mutableListOf<Pair<UUID, UUID>>()
        val fixture = fixture(firstMember = FirstMemberNotice { group, user -> joined += group to user })

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), fixture.useCase.execute(actor, code.value))

        assertEquals(listOf(groupId to actor), joined)
    }

    @Test
    fun `quem ja e membro, pedido pendente e convite invalido nao avisam`() {
        val joined = mutableListOf<Pair<UUID, UUID>>()
        val notice = FirstMemberNotice { group, user -> joined += group to user }
        val member = fixture(firstMember = notice).also { it.repository.roles[actor] = GroupRole.ATHLETE }
        member.useCase.execute(actor, code.value)
        val pending = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60), entryRequiresApproval = true), firstMember = notice)
        pending.useCase.execute(actor, code.value)
        val invalid = fixture(target = null, firstMember = notice)
        invalid.useCase.execute(actor, code.value)

        assertTrue(joined.isEmpty())
    }

    private fun monthly(
        approval: Boolean = false,
        athleteLimit: Int? = null,
        writeAllowed: Boolean = true,
    ): Pair<Fixture, MutableList<Triple<UUID, UUID, MonthlyJoinOutcome>>> {
        val notices = mutableListOf<Triple<UUID, UUID, MonthlyJoinOutcome>>()
        val first = mutableListOf<UUID>()
        val target = RedeemableInvite(groupId, now.plusSeconds(60), entryRequiresApproval = approval, kind = InviteKind.MONTHLY)
        val base = fixture(target = target, athleteLimit = athleteLimit)
        val useCase = RedeemInvite(
            base.transaction,
            base.repository,
            FixedSubscriptionLimits(athleteLimit = athleteLimit),
            base.clock,
            br.com.saqz.sharedkernel.subscription.GroupWriteAccess { writeAllowed },
            firstMember = FirstMemberNotice { _, user -> first += user },
            monthlyJoin = MonthlyJoinNotice { group, user, outcome -> notices += Triple(group, user, outcome) },
        )
        firstMemberCalls = first
        return base.copy(useCase = useCase) to notices
    }

    private var firstMemberCalls: List<UUID> = emptyList()

    @Test
    fun `link de mensalistas entra como mensalista, sem pedido mesmo com aprovacao, e avisa so pelo link`() {
        val (fixture, notices) = monthly(approval = true)

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE, AthleteMembershipType.MENSALISTA), result)
        assertEquals(AthleteMembershipType.MENSALISTA, fixture.repository.redemptions.single().membershipType)
        assertTrue(fixture.repository.entryRequests.isEmpty())
        assertEquals(listOf(Triple(groupId, actor, MonthlyJoinOutcome.JOINED_MENSALISTA)), notices)
        assertTrue(firstMemberCalls.isEmpty())
        assertEquals(listOf(actor), fixture.repository.inviteeDeletions)
    }

    @Test
    fun `link de mensalistas sem mensalidade padrao entra como avulso e avisa o motivo`() {
        val (fixture, notices) = monthly()
        fixture.repository.monthlyFeeDefault = false

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE, AthleteMembershipType.AVULSO), result)
        assertEquals(listOf(Triple(groupId, actor, MonthlyJoinOutcome.JOINED_AVULSO_NO_DEFAULT_FEE)), notices)
    }

    @Test
    fun `quem saiu antes entra pelo link de mensalistas sem pedido e perde a marca de volta`() {
        val (fixture, _) = monthly()
        fixture.repository.returnMarked += actor

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE, AthleteMembershipType.MENSALISTA), result)
        assertTrue(fixture.repository.entryRequests.isEmpty())
        assertEquals(listOf(actor), fixture.repository.clearedMarks)
    }

    @Test
    fun `avulso do grupo vira mensalista pelo link mantendo o papel`() {
        val (fixture, notices) = monthly()
        fixture.repository.roles[actor] = GroupRole.ADMIN
        fixture.repository.types[actor] = AthleteMembershipType.AVULSO

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ADMIN, AthleteMembershipType.MENSALISTA), result)
        assertEquals(listOf(actor), fixture.repository.promotions)
        assertEquals(listOf(Triple(groupId, actor, MonthlyJoinOutcome.BECAME_MENSALISTA)), notices)
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `mensalista que reabre e avulso sem mensalidade padrao nao mudam nem avisam`() {
        val (mensalista, mensalistaNotices) = monthly()
        mensalista.repository.roles[actor] = GroupRole.ATHLETE
        mensalista.repository.types[actor] = AthleteMembershipType.MENSALISTA
        assertEquals(
            RedeemInviteResult.Success(groupId, GroupRole.ATHLETE, AthleteMembershipType.MENSALISTA),
            mensalista.useCase.execute(actor, code.value),
        )

        val (avulso, avulsoNotices) = monthly()
        avulso.repository.monthlyFeeDefault = false
        avulso.repository.roles[actor] = GroupRole.ATHLETE
        avulso.repository.types[actor] = AthleteMembershipType.AVULSO
        assertEquals(
            RedeemInviteResult.Success(groupId, GroupRole.ATHLETE, AthleteMembershipType.AVULSO),
            avulso.useCase.execute(actor, code.value),
        )

        assertTrue(mensalistaNotices.isEmpty() && avulsoNotices.isEmpty())
        assertTrue(mensalista.repository.promotions.isEmpty() && avulso.repository.promotions.isEmpty())
    }

    @Test
    fun `link de mensalistas respeita o limite do plano e o plano vencido`() {
        val (full, fullNotices) = monthly(athleteLimit = 1)
        full.repository.openMembers += UUID.randomUUID()
        assertSame(RedeemInviteResult.AthleteLimitExceeded, full.useCase.execute(actor, code.value))
        assertTrue(full.repository.redemptions.isEmpty() && fullNotices.isEmpty())

        val (expired, _) = monthly(writeAllowed = false)
        kotlin.test.assertFailsWith<br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException> {
            expired.useCase.execute(actor, code.value)
        }
        assertTrue(expired.repository.redemptions.isEmpty())
    }

    @Test
    fun `link de mensalistas vencido conta como tentativa invalida`() {
        val notices = mutableListOf<MonthlyJoinOutcome>()
        val target = RedeemableInvite(groupId, now.minusSeconds(1), kind = InviteKind.MONTHLY)
        val base = fixture(target = target)
        val useCase = RedeemInvite(
            base.transaction, base.repository, FixedSubscriptionLimits(), base.clock,
            monthlyJoin = MonthlyJoinNotice { _, _, outcome -> notices += outcome },
        )

        assertSame(RedeemInviteResult.InvalidOrExpired, useCase.execute(actor, code.value))
        assertEquals(1, base.repository.invalidAttempts.size)
        assertTrue(notices.isEmpty())
    }

    private fun fixture(
        clockNow: Instant = now,
        target: RedeemableInvite? = RedeemableInvite(groupId, clockNow.plusSeconds(60), groupDeleted = false),
        athleteLimit: Int? = null,
        firstMember: FirstMemberNotice = FirstMemberNotice { _, _ -> },
    ): Fixture {
        val repository = RecordingRedemptionRepository(target, ownerId)
        val transaction = RecordingTransactionRunner()
        val clock = MutableClock(clockNow)
        return Fixture(
            RedeemInvite(
                transaction,
                repository,
                FixedSubscriptionLimits(athleteLimit = athleteLimit),
                clock,
                firstMember = firstMember,
            ),
            repository,
            transaction,
            clock,
        )
    }

    private data class Fixture(
        val useCase: RedeemInvite,
        val repository: RecordingRedemptionRepository,
        val transaction: RecordingTransactionRunner,
        val clock: MutableClock,
    )

    private class FixedSubscriptionLimits(
        private val groupLimit: Int? = null,
        private val athleteLimit: Int? = null,
    ) : SubscriptionLimits {
        override fun groupLimitFor(ownerId: UUID): Int? = groupLimit
        override fun athleteLimitFor(ownerId: UUID): Int? = athleteLimit
    }

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0
        override fun <T> inTransaction(block: () -> T): T {
            calls += 1
            return block()
        }
    }

    private class MutableClock(initial: Instant) : Clock() {
        var current = initial

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId) = this

        override fun instant() = current
    }

    private class RecordingRedemptionRepository(
        private val target: RedeemableInvite?,
        private val ownerUserId: UUID,
    ) : InviteRedemptionRepository {
        val windows = mutableMapOf<UUID, InviteAttemptWindow>()
        val lookups = mutableListOf<InviteTokenDigest>()
        val invalidAttempts = mutableListOf<RecordInvalidInviteAttempt>()
        val redemptions = mutableListOf<RedeemMembershipCommand>()
        val entryRequests = mutableListOf<CreateEntryRequestCommand>()
        val persistedRequestAt = mutableMapOf<UUID, Instant>()
        val roles = mutableMapOf<UUID, GroupRole>()
        val openMembers = mutableSetOf<UUID>()
        val returnMarked = mutableSetOf<UUID>()

        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): InviteAttemptWindow =
            windows[userId] ?: InviteAttemptWindow(initializedAt, 0)

        override fun findInvite(digest: InviteTokenDigest): RedeemableInvite? {
            lookups += digest
            return target
        }

        override fun recordInvalidAttempt(command: RecordInvalidInviteAttempt) {
            invalidAttempts += command
            windows[command.userId] = InviteAttemptWindow(command.windowStartedAt, command.invalidCount)
        }

        override fun loadAthleteOccupancy(groupId: UUID): GroupAthleteOccupancy? {
            if (target == null || target.groupId != groupId) return null
            return GroupAthleteOccupancy(
                ownerUserId = ownerUserId,
                memberIds = openMembers.toSet(),
            )
        }

        override fun findMembershipRole(groupId: UUID, userId: UUID): GroupRole? = roles[userId]

        override fun createEntryRequest(command: CreateEntryRequestCommand) {
            entryRequests += command
            persistedRequestAt.putIfAbsent(command.userId, command.requestedAt)
        }

        override fun mustRequestReturn(groupId: UUID, userId: UUID): Boolean = userId in returnMarked

        override fun redeemMembership(command: RedeemMembershipCommand): GroupRole {
            redemptions += command
            types.putIfAbsent(command.userId, command.membershipType)
            return roles.getOrPut(command.userId) { GroupRole.ATHLETE }
        }

        val types = mutableMapOf<UUID, AthleteMembershipType>()
        var monthlyFeeDefault = true
        val promotions = mutableListOf<UUID>()
        val clearedMarks = mutableListOf<UUID>()
        val inviteeDeletions = mutableListOf<UUID>()

        override fun findMembershipType(groupId: UUID, userId: UUID): AthleteMembershipType? = types[userId]

        override fun hasMonthlyFeeDefault(groupId: UUID): Boolean = monthlyFeeDefault

        override fun promoteToMensalista(groupId: UUID, userId: UUID) {
            promotions += userId
            types[userId] = AthleteMembershipType.MENSALISTA
        }

        override fun clearReturnMark(groupId: UUID, userId: UUID) {
            clearedMarks += userId
            returnMarked -= userId
        }

        override fun deleteMatchingInvitee(groupId: UUID, userId: UUID) {
            inviteeDeletions += userId
        }
    }
}
