package br.com.saqz.groups.invite

import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.domain.membership.InviteGateway
import br.com.saqz.groups.domain.membership.InvitePreview
import br.com.saqz.groups.domain.membership.InviteRedeem
import br.com.saqz.groups.domain.membership.InviteRedeemStatus
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupNativeFailureCode
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupInviteCoordinatorTest {
    @Test
    fun `authenticated invite redeems after persistence and emits group navigation`() = runTest {
        val fixture = fixture()
        fixture.coordinator.onAuthenticated()
        runCurrent()

        fixture.local.actions.clear()
        fixture.gateway.actions.clear()
        fixture.coordinator.acceptInvite("invite-authenticated")
        runCurrent()

        assertEquals(listOf("write:invite-authenticated", "write:null"), fixture.local.actions)
        assertEquals(listOf("preview", "redeem"), fixture.gateway.actions)
        assertEquals(
            listOf("write:invite-authenticated", "preview", "redeem", "write:null"),
            fixture.events,
        )
        assertEquals(
            GroupInviteEffect.NavigateToGroup("group-1", InviteRedeemStatus.JOINED, "invite-authenticated"),
            fixture.coordinator.effects.first(),
        )
        assertNull(fixture.local.pending)
    }

    @Test
    fun `redeeming stays on until the screen handles the effect`() = runTest {
        val fixture = fixture()
        fixture.coordinator.onAuthenticated()
        runCurrent()
        // Sem convite guardado não há nada a esperar.
        assertEquals(false, fixture.coordinator.redeeming.value)

        fixture.coordinator.acceptInvite("invite-authenticated")
        runCurrent()
        assertEquals(true, fixture.coordinator.redeeming.value)
        fixture.coordinator.redeemHandled()
        assertEquals(false, fixture.coordinator.redeeming.value)
    }

    @Test
    fun `signed out invite survives until fake login then redeems`() = runTest {
        val fixture = fixture()
        fixture.gateway.redeemResult = SaqzResult.Success(
            InviteRedeem(InviteRedeemStatus.PENDING, GroupId("group-1"), "ATHLETE"),
        )
        fixture.coordinator.acceptInvite("invite-login")
        runCurrent()

        assertEquals("invite-login", fixture.local.pending)
        assertEquals(GroupInviteEffect.OpenInviteLanding("invite-login"), fixture.coordinator.effects.first())
        assertEquals(GroupInviteEffect.OpenInviteLanding("invite-login"), fixture.sinkEffects.single())

        fixture.coordinator.onAuthenticated()
        runCurrent()

        assertEquals(
            GroupInviteEffect.NavigateToGroup("group-1", InviteRedeemStatus.PENDING, "invite-login"),
            fixture.coordinator.effects.first(),
        )
        assertNull(fixture.local.pending)
    }

    @Test
    fun `cold landing buffered before authentication is discarded after redeem`() = runTest {
        val fixture = fixture()
        fixture.coordinator.acceptInvite("invite-cold")
        runCurrent()

        fixture.coordinator.onAuthenticated()
        runCurrent()

        assertEquals(
            GroupInviteEffect.NavigateToGroup("group-1", InviteRedeemStatus.JOINED, "invite-cold"),
            fixture.coordinator.effects.first(),
        )
        assertNull(withTimeoutOrNull(1) { fixture.coordinator.effects.first() })
    }

    @Test
    fun `landing success clears persisted invite idempotently`() = runTest {
        val fixture = fixture()
        fixture.local.pending = "invite-landing"
        var clearedCount = 0

        fixture.coordinator.clearPendingInvite("invite-landing") { clearedCount++ }
        runCurrent()
        fixture.coordinator.clearPendingInvite("invite-landing") { clearedCount++ }
        runCurrent()

        assertEquals(2, clearedCount)
        assertNull(fixture.local.pending)
        assertEquals(listOf("read", "write:null", "read", "write:null"), fixture.local.actions)
    }

    @Test
    fun `pending storage failure keeps the invite code in its effect`() = runTest {
        val fixture = fixture()
        fixture.local.writeSucceeds = false
        val effect = async { fixture.coordinator.effects.first() }

        fixture.coordinator.acceptInvite("invite-storage")
        runCurrent()

        assertEquals(
            GroupInviteEffect.PendingInviteStorageFailed("invite-storage"),
            effect.await(),
        )
    }

    @Test
    fun `signed out relaunch restores pending code and preview before redeem`() = runTest {
        val fixture = fixture()
        fixture.local.pending = "invite-relaunch"
        fixture.gateway.redeemResult = SaqzResult.Success(
            InviteRedeem(InviteRedeemStatus.PENDING, GroupId("group-1"), "ATHLETE"),
        )

        assertEquals("invite-relaunch", fixture.coordinator.readPendingInviteCode())
        assertEquals(
            InvitePreview("Vôlei do CERET", "Ana", entryRequiresApproval = false),
            assertIs<SaqzResult.Success<InvitePreview>>(fixture.coordinator.previewPending()).value,
        )

        fixture.coordinator.onAuthenticated()
        runCurrent()

        val effect = assertIs<GroupInviteEffect.NavigateToGroup>(fixture.coordinator.effects.first())
        assertEquals(InviteRedeemStatus.PENDING, effect.status)
        assertEquals("invite-relaunch", effect.inviteCode)
    }

    @Test
    fun `repeated authentication does not start a second redeem generation`() = runTest {
        val redeemRelease = CompletableDeferred<Unit>()
        val fixture = fixture(
            redeem = {
                redeemRelease.await()
                SaqzResult.Success(InviteRedeem(InviteRedeemStatus.JOINED, GroupId("group-1"), "ATHLETE"))
            },
        )
        fixture.local.pending = "invite-idempotent"

        fixture.coordinator.onAuthenticated()
        runCurrent()
        fixture.coordinator.onAuthenticated()
        runCurrent()

        assertEquals(listOf("preview", "redeem"), fixture.gateway.actions)

        redeemRelease.complete(Unit)
        runCurrent()

        assertEquals(
            GroupInviteEffect.NavigateToGroup("group-1", InviteRedeemStatus.JOINED, "invite-idempotent"),
            fixture.coordinator.effects.first(),
        )
        assertNull(withTimeoutOrNull(1) { fixture.coordinator.effects.first() })
    }

    @Test
    fun `registration preview feeds the invite header and registration then redeems`() = runTest {
        val fixture = fixture()
        fixture.gateway.previewResult = SaqzResult.Success(
            InvitePreview("Vôlei do CERET", "Ana", entryRequiresApproval = true),
        )
        fixture.gateway.redeemResult = SaqzResult.Success(
            InviteRedeem(InviteRedeemStatus.PENDING, GroupId("group-1"), "ATHLETE"),
        )
        fixture.coordinator.acceptInvite("invite-register")
        runCurrent()
        fixture.coordinator.effects.first()

        val preview = fixture.coordinator.previewPending()

        assertEquals(
            InvitePreview("Vôlei do CERET", "Ana", entryRequiresApproval = true),
            assertIs<SaqzResult.Success<InvitePreview>>(preview).value,
        )

        fixture.coordinator.onAuthenticated()
        runCurrent()

        assertEquals(
            GroupInviteEffect.NavigateToGroup("group-1", InviteRedeemStatus.PENDING, "invite-register"),
            fixture.coordinator.effects.first(),
        )
        assertNull(fixture.local.pending)
    }

    @Test
    fun `terminal error clears pending invite`() = runTest {
        val fixture = fixture(
            previewResult = SaqzResult.Failure(InviteError.InvalidOrExpired("INVITE_INVALID")),
        )
        fixture.local.pending = "invite-terminal"
        val effect = async { fixture.coordinator.effects.first() }
        fixture.coordinator.onAuthenticated()
        runCurrent()

        val result = assertIs<GroupInviteEffect.RedeemFailed>(effect.await())
        assertEquals("invite-terminal", result.code)
        assertEquals(InviteError.InvalidOrExpired("INVITE_INVALID"), result.error)
        assertTrue(!result.willRetry)
        assertNull(fixture.local.pending)
    }

    @Test
    fun `group not receiving athletes is terminal and clears the pending invite`() = runTest {
        val fixture = fixture(
            previewResult = SaqzResult.Failure(InviteError.GroupNotReceiving),
        )
        fixture.local.pending = "invite-paused"
        val effect = async { fixture.coordinator.effects.first() }
        fixture.coordinator.onAuthenticated()
        runCurrent()

        val result = assertIs<GroupInviteEffect.RedeemFailed>(effect.await())
        assertEquals(InviteError.GroupNotReceiving, result.error)
        assertTrue(!result.willRetry)
        assertNull(fixture.local.pending)
    }

    @Test
    fun `network failure keeps pending invite for the next opportunity`() = runTest {
        val fixture = fixture(
            previewResult = SaqzResult.Failure(InviteError.DataFailure(DataError.Connectivity)),
        )
        fixture.local.pending = "invite-network"
        fixture.coordinator.onAuthenticated()
        runCurrent()

        val effect = assertIs<GroupInviteEffect.RedeemFailed>(fixture.coordinator.effects.first())
        assertEquals(InviteError.DataFailure(DataError.Connectivity), effect.error)
        assertTrue(effect.willRetry)
        assertEquals("invite-network", fixture.local.pending)
    }

    @Test
    fun `rate limit keeps pending invite for the next opportunity`() = runTest {
        val fixture = fixture(
            redeemResult = SaqzResult.Failure(InviteError.RateLimited(30)),
        )
        fixture.local.pending = "invite-rate-limit"
        fixture.coordinator.onAuthenticated()
        runCurrent()

        val effect = assertIs<GroupInviteEffect.RedeemFailed>(fixture.coordinator.effects.first())
        assertEquals(InviteError.RateLimited(30), effect.error)
        assertTrue(effect.willRetry)
        assertEquals("invite-rate-limit", fixture.local.pending)
    }

    @Test
    fun `preview and redeem rule failures report invite_invalid with their step`() = runTest {
        val recorded = mutableListOf<Pair<String, Map<String, String>>>()
        SaqzAnalytics.track = { name, params -> recorded += name to params }
        try {
            val onPreview = fixture(previewResult = SaqzResult.Failure(InviteError.InvalidOrExpired("INVITE_INVALID")))
            onPreview.local.pending = "invite-a"
            onPreview.coordinator.onAuthenticated()
            runCurrent()
            val onRedeem = fixture(redeemResult = SaqzResult.Failure(InviteError.RateLimited(30)))
            onRedeem.local.pending = "invite-b"
            onRedeem.coordinator.onAuthenticated()
            runCurrent()
            val network = fixture(previewResult = SaqzResult.Failure(InviteError.DataFailure(DataError.Connectivity)))
            network.local.pending = "invite-c"
            network.coordinator.onAuthenticated()
            runCurrent()
        } finally {
            SaqzAnalytics.reset()
        }

        assertEquals(
            listOf(
                "invite_invalid" to mapOf("reason" to "invalid", "error_code" to "INVITE_INVALID", "step" to "preview"),
                "invite_invalid" to mapOf("reason" to "attempt_limit", "error_code" to "INVITE_ATTEMPT_LIMIT", "step" to "redeem"),
            ),
            recorded,
        )
    }

    @Test
    fun `a newer invite discards old redeem result and keeps newer generation`() = runTest {
        val oldRedeemStarted = CompletableDeferred<Unit>()
        val releaseOldRedeem = CompletableDeferred<Unit>()
        val fixture = fixture(
            redeem = { code ->
                if (code.value == "invite-old") {
                    oldRedeemStarted.complete(Unit)
                    releaseOldRedeem.await()
                    SaqzResult.Success(InviteRedeem(InviteRedeemStatus.JOINED, GroupId("group-old"), "ADMIN"))
                } else {
                    SaqzResult.Success(InviteRedeem(InviteRedeemStatus.JOINED, GroupId("group-new"), "ADMIN"))
                }
            },
        )
        fixture.local.pending = "invite-old"
        fixture.coordinator.onAuthenticated()
        runCurrent()
        oldRedeemStarted.await()

        fixture.coordinator.acceptInvite("invite-new")
        runCurrent()
        releaseOldRedeem.complete(Unit)
        runCurrent()

        assertEquals(
            GroupInviteEffect.NavigateToGroup("group-new", InviteRedeemStatus.JOINED, "invite-new"),
            fixture.coordinator.effects.first(),
        )
        assertNull(withTimeoutOrNull(1) { fixture.coordinator.effects.first() })
        assertNull(fixture.local.pending)
    }

    @Test
    fun `logging out erases the stored invite`() = runTest {
        val fixture = fixture()
        fixture.local.pending = "invite-out"
        fixture.coordinator.onAuthenticated()
        fixture.local.pending = "invite-out"
        fixture.coordinator.onLoggedOut()
        runCurrent()

        assertNull(fixture.local.pending)
    }

    @Test
    fun `failed erase on logout makes the next login skip preview and redeem and retry the erase`() = runTest {
        val fixture = fixture()
        fixture.local.pending = "invite-out"
        fixture.local.writeSucceeds = false
        fixture.coordinator.onLoggedOut()
        runCurrent()
        assertEquals("invite-out", fixture.local.pending)

        fixture.local.writeSucceeds = true
        fixture.gateway.actions.clear()
        fixture.coordinator.onAuthenticated()
        runCurrent()

        assertEquals(emptyList(), fixture.gateway.actions)
        assertNull(fixture.local.pending)
        assertEquals(false, fixture.coordinator.redeeming.value)
    }

    @Test
    fun `a new link after a failed erase is redeemed normally`() = runTest {
        val fixture = fixture()
        fixture.local.pending = "invite-out"
        fixture.local.writeSucceeds = false
        fixture.coordinator.onLoggedOut()
        runCurrent()

        fixture.local.writeSucceeds = true
        fixture.coordinator.acceptInvite("invite-new")
        runCurrent()
        assertEquals("invite-new", fixture.local.pending)

        fixture.coordinator.onAuthenticated()
        runCurrent()

        assertEquals(
            GroupInviteEffect.NavigateToGroup("group-1", InviteRedeemStatus.JOINED, "invite-new"),
            fixture.coordinator.effects.first(),
        )
        assertNull(fixture.local.pending)
    }

    @Test
    fun `a persist in flight during logout does not survive it`() = runTest {
        val fixture = fixture()
        fixture.local.holdWrites = true
        fixture.coordinator.acceptInvite("invite-flight")
        runCurrent()
        // A gravação do link está em voo (trava tomada); o logout espera a vez dela.
        fixture.coordinator.onLoggedOut()
        runCurrent()
        fixture.local.holdWrites = false
        fixture.local.releaseHeld()
        runCurrent()

        assertNull(fixture.local.pending)
        assertEquals(listOf("write:invite-flight", "write:null"), fixture.local.actions)
    }

    @Test
    fun `a preview that answers after logout never calls redeem`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(previewGate = gate)
        fixture.local.pending = "invite-slow"
        fixture.coordinator.onAuthenticated()
        runCurrent()

        fixture.coordinator.onLoggedOut()
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("preview"), fixture.gateway.actions)
        assertNull(fixture.local.pending)
    }

    @Test
    fun `entry request answered as pending clears the stored invite`() = runTest {
        val fixture = fixture(
            redeemResult = SaqzResult.Success(InviteRedeem(InviteRedeemStatus.PENDING, GroupId("group-1"), "ATHLETE")),
        )
        fixture.local.pending = "invite-request"
        fixture.coordinator.onAuthenticated()
        runCurrent()

        assertEquals(
            GroupInviteEffect.NavigateToGroup("group-1", InviteRedeemStatus.PENDING, "invite-request"),
            fixture.coordinator.effects.first(),
        )
        assertNull(fixture.local.pending)
    }

    private fun TestScope.fixture(
        previewResult: SaqzResult<InvitePreview, InviteError> = SaqzResult.Success(
            InvitePreview("Vôlei do CERET", "Ana", entryRequiresApproval = false),
        ),
        redeemResult: SaqzResult<InviteRedeem, InviteError> = SaqzResult.Success(
            InviteRedeem(InviteRedeemStatus.JOINED, GroupId("group-1"), "ATHLETE"),
        ),
        redeem: (suspend (InviteCode) -> SaqzResult<InviteRedeem, InviteError>)? = null,
        previewGate: CompletableDeferred<Unit>? = null,
    ): Fixture {
        val events = mutableListOf<String>()
        val sinkEffects = mutableListOf<GroupInviteEffect>()
        val local = FakeLocalState(events)
        val gateway = FakeInviteGateway(previewResult, redeemResult, redeem, events, previewGate)
        return Fixture(
            local = local,
            gateway = gateway,
            coordinator = GroupInviteCoordinator(FakeLinkPort(), local, gateway, this, effectSink = sinkEffects::add),
            events = events,
            sinkEffects = sinkEffects,
        )
    }

    private data class Fixture(
        val local: FakeLocalState,
        val gateway: FakeInviteGateway,
        val coordinator: GroupInviteCoordinator,
        val events: MutableList<String>,
        val sinkEffects: MutableList<GroupInviteEffect>,
    )

    private class FakeLinkPort : NativeGroupLinkPort {
        private var listener: GroupLinkEventListener? = null

        override fun start(listener: GroupLinkEventListener): GroupCancelable {
            this.listener = listener
            return object : GroupCancelable {
                override fun cancel() {
                    this@FakeLinkPort.listener = null
                }
            }
        }

        fun emit(code: String) = listener?.onEvent(GroupLinkEvent.Invite(code))
    }

    private class FakeLocalState(private val events: MutableList<String>) : LocalGroupStatePort {
        var pending: String? = null
        var writeSucceeds = true

        /** Gravação "em voo": o callback só volta em [releaseHeld], com a trava do coordinator tomada. */
        var holdWrites = false
        private val held = mutableListOf<() -> Unit>()
        val actions = mutableListOf<String>()

        override fun readSelectedGroupId(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))

        override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)

        override fun readPendingInvite(done: GroupValueCallback) {
            actions += "read"
            done.complete(GroupValueResult.Success(pending))
        }

        override fun writePendingInvite(value: String?, done: GroupResultCallback) {
            actions += "write:${value ?: "null"}"
            events += "write:${value ?: "null"}"
            val finish = {
                if (writeSucceeds) {
                    pending = value
                    done.complete(GroupOperationResult.Success)
                } else {
                    done.complete(GroupOperationResult.Failure(GroupNativeFailureCode.UNKNOWN))
                }
            }
            if (holdWrites) held += finish else finish()
        }

        fun releaseHeld() {
            val toRun = held.toList()
            held.clear()
            toRun.forEach { it() }
        }

        override fun readPendingAttendanceLink(done: GroupValueCallback) = done.complete(GroupValueResult.Success(null))

        override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) = done.complete(GroupOperationResult.Success)
    }

    private class FakeInviteGateway(
        var previewResult: SaqzResult<InvitePreview, InviteError>,
        var redeemResult: SaqzResult<InviteRedeem, InviteError>,
        private val redeemHandler: (suspend (InviteCode) -> SaqzResult<InviteRedeem, InviteError>)?,
        private val events: MutableList<String>,
        private val previewGate: CompletableDeferred<Unit>? = null,
    ) : InviteGateway {
        val actions = mutableListOf<String>()

        override suspend fun preview(code: InviteCode): SaqzResult<InvitePreview, InviteError> {
            actions += "preview"
            events += "preview"
            previewGate?.await()
            return previewResult
        }

        override suspend fun redeem(code: InviteCode): SaqzResult<InviteRedeem, InviteError> {
            actions += "redeem"
            events += "redeem"
            return redeemHandler?.invoke(code) ?: redeemResult
        }
    }
}
