package br.com.saqz.subscriptions.presentation.limit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialError
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import br.com.saqz.subscriptions.presentation.appstore.FakeCatalogGateway
import br.com.saqz.subscriptions.presentation.appstore.ORGANIZADOR_MENSAL
import br.com.saqz.subscriptions.presentation.appstore.appStoreSubscription
import br.com.saqz.subscriptions.presentation.ui.appstore.AppStorePaywallPreviewData
import br.com.saqz.subscriptions.presentation.ui.appstore.AppStorePaywallScreen
import br.com.saqz.subscriptions.presentation.ui.appstore.AppStorePaywallTags
import br.com.saqz.subscriptions.presentation.ui.limit.PaywallGroupLimitTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalTestApi::class)
class GroupLimitTest {
    private val trial = FakeTrial()
    private val subscriptions = FakeCatalogGateway()
    private val resolver = GroupLimitResolver(subscriptions, trial)

    @Test
    fun `a subscription at its group limit names the plan and the numbers`() = runTest {
        subscriptions.subscriptionResult = SaqzResult.Success(
            appStoreSubscription().copy(usage = SubscriptionUsage(groupsUsed = 3, groupsLimit = 3)),
        )

        assertEquals(PaywallGroupLimit(3, 3, "Organizador"), resolver.resolve())
    }

    @Test
    fun `a subscription with room is not a limit`() = runTest {
        subscriptions.subscriptionResult = SaqzResult.Success(
            appStoreSubscription().copy(usage = SubscriptionUsage(groupsUsed = 2, groupsLimit = 3)),
        )

        assertNull(resolver.resolve())
    }

    @Test
    fun `a plan without a group ceiling is never a limit`() = runTest {
        subscriptions.subscriptionResult = SaqzResult.Success(
            appStoreSubscription().copy(usage = SubscriptionUsage(groupsUsed = 40, groupsLimit = null)),
        )

        assertNull(resolver.resolve())
    }

    @Test
    fun `a trial that cannot create more groups is the trial limit`() = runTest {
        trial.access = trialAccess(status = TrialStatus.Active, canCreateGroup = false, maxGroups = 3)

        assertEquals(PaywallGroupLimit(3, 3, "Organizador"), resolver.resolve())
    }

    @Test
    fun `an expired trial is not a group limit`() = runTest {
        trial.access = trialAccess(status = TrialStatus.Expired, canCreateGroup = false, maxGroups = 3)

        assertNull(resolver.resolve())
    }

    @Test
    fun `a failed lookup is not a group limit`() = runTest {
        trial.offline = true

        assertNull(resolver.resolve())
    }

    @Test
    fun `plans below the groups the person already has are marked and cannot be bought`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                AppStorePaywallScreen(
                    state = AppStorePaywallPreviewData.ready,
                    onIntent = {},
                    onBack = {},
                    onOpenTerms = {},
                    onOpenPrivacy = {},
                    groupLimit = PaywallGroupLimit(groupsUsed = 3, groupsLimit = 3, planName = "Organizador"),
                )
            }
        }

        onNodeWithText("Você chegou ao limite de 3 grupos do plano Organizador.").assertExists()
        onNodeWithText("Para criar mais grupos, escolha um plano com mais espaço.").assertExists()
        onNodeWithTag(AppStorePaywallTags.TooSmallPrefix + "Titular").performScrollTo().assertExists()
        onNodeWithText("Não comporta seus 3 grupos").assertExists()
        onNodeWithTag(AppStorePaywallTags.TooSmallPrefix + "Organizador").assertDoesNotExist()
        onNodeWithTag(AppStorePaywallTags.PurchasePrefix + "app.saqz.titular.mensal").performScrollTo().assertIsNotEnabled()
        onNodeWithTag(AppStorePaywallTags.PurchasePrefix + ORGANIZADOR_MENSAL).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `without a limit the paywall shows no notice`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                AppStorePaywallScreen(
                    state = AppStorePaywallPreviewData.ready,
                    onIntent = {},
                    onBack = {},
                    onOpenTerms = {},
                    onOpenPrivacy = {},
                )
            }
        }

        onNodeWithTag(PaywallGroupLimitTags.Notice).assertDoesNotExist()
        onNodeWithTag(AppStorePaywallTags.TooSmallPrefix + "Titular").assertDoesNotExist()
    }

    private fun trialAccess(status: TrialStatus, canCreateGroup: Boolean, maxGroups: Int) = TrialAccess(
        status = status,
        startedAt = null,
        endsAt = null,
        serverTime = "2026-10-13T12:00:00Z",
        readOnly = false,
        canCreateGroup = canCreateGroup,
        maxGroups = maxGroups,
        maxAthletes = null,
        isOwner = true,
        appUrl = null,
    )

    private class FakeTrial : TrialGateway {
        var access = TrialAccess(TrialStatus.Ineligible, null, null, "2026-10-13T12:00:00Z", false, false, 3, null, true, null)
        var offline = false
        override suspend fun ownerTrial(): SaqzResult<TrialAccess, TrialError> =
            if (offline) SaqzResult.Failure(TrialError.Data(DataError.Connectivity)) else SaqzResult.Success(access)
        override suspend fun groupTrial(groupId: GroupId) = ownerTrial()
    }
}
