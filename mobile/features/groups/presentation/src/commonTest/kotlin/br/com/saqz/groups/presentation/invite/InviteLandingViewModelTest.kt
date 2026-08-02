package br.com.saqz.groups.presentation.invite

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.domain.membership.InviteGateway
import br.com.saqz.groups.domain.membership.InviteNextGame
import br.com.saqz.groups.domain.membership.InvitePreview
import br.com.saqz.groups.domain.membership.InviteRedeem
import br.com.saqz.groups.domain.membership.InviteRedeemStatus
import br.com.saqz.groups.domain.membership.InviteRegularSlot
import br.com.saqz.groups.model.GroupTimeZone
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InviteLandingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `preview success exposes card and approval mode`() = runTest {
        val viewModel = viewModel(preview = SaqzResult.Success(preview(approval = true)))

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("TUESDAY", "THURSDAY"), viewModel.state.value.preview?.regularWeekdays)
        assertEquals("WOMEN", viewModel.state.value.preview?.compositionCode)
        assertEquals("TUESDAY", viewModel.state.value.preview?.nextGame?.weekdayCode)
        assertEquals("04/08", viewModel.state.value.preview?.nextGame?.date)
        assertEquals("19h30", viewModel.state.value.preview?.nextGame?.time)
        assertTrue(viewModel.state.value.preview?.entryRequiresApproval == true)
    }

    @Test
    fun `preview expired preserves a date for the error screen`() = runTest {
        val viewModel = viewModel(preview = SaqzResult.Failure(InviteError.Expired(EXPIRED_AT)))

        assertEquals(InviteLandingError.Expired("31/08/2026"), viewModel.state.value.error)
        assertNull(viewModel.state.value.preview)
    }

    @Test
    fun `utc expiration is rendered in the fixed local timezone`() = runTest {
        val viewModel = viewModel(
            preview = SaqzResult.Failure(InviteError.Expired("2026-08-01T01:30:00Z")),
        )

        assertEquals(InviteLandingError.Expired("31/07/2026"), viewModel.state.value.error)
    }

    @Test
    fun `unavailable timezone falls back to utc without breaking preview`() = runTest {
        val viewModel = viewModel(
            preview = SaqzResult.Success(
                preview().copy(nextGame = InviteNextGame("2026-08-01T01:30:00Z", "CERET", "Quadra 2")),
            ),
            timeZonePort = FakeTimeZonePort(GroupSystemTimeZoneResult.Unavailable),
        )

        assertEquals("SATURDAY", viewModel.state.value.preview?.nextGame?.weekdayCode)
        assertEquals("01/08", viewModel.state.value.preview?.nextGame?.date)
        assertEquals("01h30", viewModel.state.value.preview?.nextGame?.time)
    }

    @Test
    fun `preview invalid never says code invalid and network is retryable`() = runTest {
        val invalid = viewModel(preview = SaqzResult.Failure(InviteError.InvalidOrExpired))
        assertEquals(InviteLandingError.Invalid, invalid.state.value.error)

        val network = viewModel(preview = SaqzResult.Failure(InviteError.DataFailure(DataError.Connectivity)))
        assertEquals(InviteLandingError.Network, network.state.value.error)
    }

    @Test
    fun `joined redeem emits group id`() = runTest {
        val viewModel = viewModel(
            redeem = SaqzResult.Success(InviteRedeem(InviteRedeemStatus.JOINED, GroupId("group-42"), "ATHLETE")),
        )

        viewModel.onIntent(InviteLandingIntent.PrimaryAction)

        assertEquals(InviteLandingEffect.Joined("group-42"), viewModel.effects.first())
        assertFalse(viewModel.state.value.isRedeeming)
    }

    @Test
    fun `pending redeem shows request sent and emits its effect`() = runTest {
        val viewModel = viewModel(
            preview = SaqzResult.Success(preview(approval = true)),
            redeem = SaqzResult.Success(InviteRedeem(InviteRedeemStatus.PENDING, GroupId("group-1"), null)),
        )

        viewModel.onIntent(InviteLandingIntent.PrimaryAction)

        assertEquals(InviteLandingEffect.RequestSent, viewModel.effects.first())
        assertTrue(viewModel.state.value.requestSent)
    }

    @Test
    fun `redeem maps each typed error to a distinguishable state`() = runTest {
        assertEquals(
            InviteLandingError.Invalid,
            viewModel(redeem = SaqzResult.Failure(InviteError.InvalidOrExpired)).redeemError(),
        )
        assertEquals(
            InviteLandingError.Expired("31/08/2026"),
            viewModel(redeem = SaqzResult.Failure(InviteError.Expired(EXPIRED_AT))).redeemError(),
        )
        assertEquals(
            InviteLandingError.RateLimited(23),
            viewModel(redeem = SaqzResult.Failure(InviteError.RateLimited(23))).redeemError(),
        )
        assertEquals(
            InviteLandingError.PlanLimit,
            viewModel(redeem = SaqzResult.Failure(InviteError.PlanLimit)).redeemError(),
        )
        assertEquals(
            InviteLandingError.Network,
            viewModel(redeem = SaqzResult.Failure(InviteError.DataFailure(DataError.Timeout))).redeemError(),
        )
    }

    @Test
    fun `mode flag allows both 3d and 3l`() = runTest {
        assertTrue(viewModel(preview = SaqzResult.Success(preview(approval = true))).state.value.preview!!.entryRequiresApproval)
        assertFalse(viewModel(preview = SaqzResult.Success(preview(approval = false))).state.value.preview!!.entryRequiresApproval)
    }

    @Test
    fun `stale preview response cannot replace a newer generation`() = runTest {
        val oldPreview = CompletableDeferred<SaqzResult<InvitePreview, InviteError>>()
        val newPreview = CompletableDeferred<SaqzResult<InvitePreview, InviteError>>()
        val gateway = FakeInviteGateway(previews = ArrayDeque(listOf(oldPreview, newPreview)))
        val viewModel = InviteLandingViewModel(INVITE_CODE, gateway, FIXED_TIME_ZONE_PORT)

        viewModel.onIntent(InviteLandingIntent.Retry)
        gateway.completePreview(1, preview(approval = false))
        oldPreview.complete(SaqzResult.Success(preview(approval = true)))

        assertFalse(viewModel.state.value.preview!!.entryRequiresApproval)
    }

    private fun viewModel(
        preview: SaqzResult<InvitePreview, InviteError> = SaqzResult.Success(preview()),
        redeem: SaqzResult<InviteRedeem, InviteError> = SaqzResult.Success(
            InviteRedeem(InviteRedeemStatus.JOINED, GroupId("group-1"), "ATHLETE"),
        ),
        timeZonePort: GroupSystemTimeZonePort = FIXED_TIME_ZONE_PORT,
    ): InviteLandingViewModel = InviteLandingViewModel(
        INVITE_CODE,
        FakeInviteGateway(preview, redeem),
        timeZonePort,
    )

    private fun InviteLandingViewModel.redeemError(): InviteLandingError {
        onIntent(InviteLandingIntent.PrimaryAction)
        return state.value.error ?: error("redeem error not exposed")
    }

    private fun preview(approval: Boolean = false) = InvitePreview(
        groupName = "Vôlei do CERET",
        inviterName = "Ana",
        entryRequiresApproval = approval,
        city = "Tatuapé",
        composition = "WOMEN",
        level = "INTERMEDIATE",
        memberCount = 26,
        regularSlots = listOf(
            InviteRegularSlot("TUESDAY", "19:30"),
            InviteRegularSlot("THURSDAY", "19:30"),
        ),
        expiresAt = EXPIRED_AT,
        nextGame = InviteNextGame("2026-08-04T22:30:00Z", "CERET", "Quadra 2"),
    )

    private class FakeInviteGateway(
        private var previewResult: SaqzResult<InvitePreview, InviteError> = SaqzResult.Success(previewData()),
        private val redeemResult: SaqzResult<InviteRedeem, InviteError> = SaqzResult.Success(
            InviteRedeem(InviteRedeemStatus.JOINED, GroupId("group-1"), "ATHLETE"),
        ),
        private val previews: ArrayDeque<CompletableDeferred<SaqzResult<InvitePreview, InviteError>>>? = null,
    ) : InviteGateway {
        private var previewIndex = 0

        override suspend fun preview(code: InviteCode): SaqzResult<InvitePreview, InviteError> =
            previews?.getOrNull(previewIndex++)?.await() ?: previewResult

        override suspend fun redeem(code: InviteCode): SaqzResult<InviteRedeem, InviteError> = redeemResult

        fun completePreview(index: Int, value: InvitePreview) {
            previews?.get(index)?.complete(SaqzResult.Success(value))
        }
    }

    private class FakeTimeZonePort(
        private val result: GroupSystemTimeZoneResult,
    ) : GroupSystemTimeZonePort {
        override fun detect(done: (GroupSystemTimeZoneResult) -> Unit) = done(result)
    }

    private companion object {
        const val INVITE_CODE = "invite-code"
        const val EXPIRED_AT = "2026-08-31T23:59:00Z"
        val FIXED_TIME_ZONE_PORT = FakeTimeZonePort(
            GroupSystemTimeZoneResult.Available(
                (GroupTimeZone.parse("America/Sao_Paulo") as GroupTimeZone.ParseResult.Valid).value,
            ),
        )

        fun previewData() = InvitePreview("Vôlei do CERET", "Ana", false)
    }
}
