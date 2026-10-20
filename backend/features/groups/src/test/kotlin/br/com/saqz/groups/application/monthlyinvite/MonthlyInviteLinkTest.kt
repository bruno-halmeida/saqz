package br.com.saqz.groups.application.monthlyinvite

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteLinkFactory
import br.com.saqz.groups.application.invite.InviteToken
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MonthlyInviteLinkTest {
    private val now = Instant.parse("2026-10-20T15:00:00Z")
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val clock = MutableClock(now)
    private val repository = InMemoryMonthlyInviteLinkRepository()
    private val generator = SequenceGenerator()
    private val renew = GetOrRenewMonthlyInviteLink(repository, generator, LinkFactory, clock)

    @Test
    fun `first send creates the link valid for 7 days`() {
        val link = renew.execute(groupId, actor)

        assertTrue(link.newCode)
        assertEquals(now.plus(Duration.ofDays(7)), link.expiresAt)
        assertEquals(URI("https://saqz.app/?saqz_invite=${link.code.value}"), link.url)
        assertEquals(MonthlyInviteLinkState.ACTIVE, repository.find(groupId)!!.state(now))
    }

    @Test
    fun `send with live code keeps the code and renews 7 days from now`() {
        val first = renew.execute(groupId, actor)
        clock.current = now.plus(Duration.ofDays(6))

        val second = renew.execute(groupId, actor)

        assertFalse(second.newCode)
        assertEquals(first.code, second.code)
        assertEquals(clock.current.plus(Duration.ofDays(7)), second.expiresAt)
    }

    @Test
    fun `send after expiry creates a new code and the old one is gone`() {
        val first = renew.execute(groupId, actor)
        clock.current = now.plus(Duration.ofDays(7))
        assertEquals(MonthlyInviteLinkState.EXPIRED, repository.find(groupId)!!.state(clock.current))

        val second = renew.execute(groupId, actor)

        assertTrue(second.newCode)
        assertNotEquals(first.code, second.code)
        assertFalse(repository.isShortCodeTaken(first.code.value))
    }

    @Test
    fun `draw skips codes already used by the public invite`() {
        repository.publicCodes += generator.peek().value

        val link = renew.execute(groupId, actor)

        assertFalse(link.code.value in repository.publicCodes)
    }

    @Test
    fun `owner and admin invalidate, athlete is forbidden, outsider sees not found`() {
        renew.execute(groupId, actor)

        assertSame(InvalidateMonthlyInviteLinkResult.AccessForbidden, invalidate(GroupRole.ATHLETE).execute(actor, groupId))
        assertEquals(MonthlyInviteLinkState.ACTIVE, repository.find(groupId)!!.state(now))
        assertSame(InvalidateMonthlyInviteLinkResult.GroupNotFound, invalidate(null).execute(actor, groupId))

        assertSame(InvalidateMonthlyInviteLinkResult.Success, invalidate(GroupRole.ADMIN).execute(actor, groupId))
        val stored = repository.find(groupId)!!
        assertNull(stored.shortCode)
        assertEquals(now, stored.invalidatedAt)
        assertEquals(MonthlyInviteLinkState.INVALIDATED, stored.state(now))
        assertSame(InvalidateMonthlyInviteLinkResult.Success, invalidate(GroupRole.OWNER).execute(actor, groupId))
    }

    @Test
    fun `send after invalidation creates a new code`() {
        val first = renew.execute(groupId, actor)
        invalidate(GroupRole.OWNER).execute(actor, groupId)

        val second = renew.execute(groupId, actor)

        assertTrue(second.newCode)
        assertNotEquals(first.code, second.code)
        assertNull(repository.find(groupId)!!.invalidatedAt)
    }

    private fun invalidate(role: GroupRole?) = InvalidateMonthlyInviteLink(
        DirectTransaction,
        object : GroupReadRepository {
            override fun find(key: GroupReadKey) = role?.let {
                GroupReadSnapshot(groupId, AccessName.from("Vôlei"), IanaTimeZone.from("UTC"), it, 1)
            }
        },
        repository,
        GroupAccessPolicy(),
        clock,
    )

    private object DirectTransaction : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private object LinkFactory : InviteLinkFactory {
        override fun create(code: InviteCode): URI = URI("https://saqz.app/?saqz_invite=${code.value}")
    }

    private class SequenceGenerator : SecureTokenGenerator {
        private var seed = 0

        fun peek(): InviteCode = code(seed)

        override fun generate(): InviteToken {
            val code = code(seed++)
            return InviteToken(code, InviteTokenDigest.sha256(code))
        }

        private fun code(seed: Int) = InviteCode.from(InviteCode.SHORT_ALPHABET.substring(seed, seed + 8))
    }
}

class MutableClock(var current: Instant) : Clock() {
    override fun instant(): Instant = current

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this
}

class InMemoryMonthlyInviteLinkRepository : MonthlyInviteLinkRepository {
    val links = mutableMapOf<UUID, MonthlyInviteLink>()
    val publicCodes = mutableSetOf<String>()

    override fun lockAndFind(groupId: UUID): MonthlyInviteLink? = links[groupId]

    override fun find(groupId: UUID): MonthlyInviteLink? = links[groupId]

    override fun replaceCode(groupId: UUID, shortCode: String, expiresAt: Instant, createdByUserId: UUID, now: Instant) {
        links[groupId] = MonthlyInviteLink(groupId, shortCode, expiresAt, null)
    }

    override fun renew(groupId: UUID, expiresAt: Instant, now: Instant) {
        links.computeIfPresent(groupId) { _, link -> link.copy(expiresAt = expiresAt) }
    }

    override fun invalidate(groupId: UUID, now: Instant) {
        links.computeIfPresent(groupId) { _, link -> link.copy(shortCode = null, invalidatedAt = now) }
    }

    override fun isShortCodeTaken(shortCode: String): Boolean =
        shortCode in publicCodes || links.values.any { it.shortCode == shortCode }
}
