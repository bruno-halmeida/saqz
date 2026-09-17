package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Os estados do hero, um por prancha do mock. Estado fora da cena é estado não conferido. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
@Suppress("TooManyFunctions")
class GroupHeroScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, state: GroupDetailsState) = compose.captureDetails(name, state, "hero")

    @Test fun pending() = capture("group-hero-pending", GroupHeroPreviewData.pending)

    @Test fun confirmedWithToast() = capture("group-hero-confirmed-toast", GroupHeroPreviewData.confirmedToast)

    @Test fun responding() = capture("group-hero-responding", GroupHeroPreviewData.responding)

    @Test fun declined() = capture("group-hero-declined", GroupHeroPreviewData.declined)

    // "Alterar" é estado de composição (`remember`), não do ViewModel: a cena toca o botão.
    @Test
    fun changing() {
        compose.setContent {
            SaqzTheme {
                Box(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    GroupDetailsScreen(state = GroupHeroPreviewData.confirmed, onBack = {}, onIntent = {})
                }
            }
        }
        compose.onNodeWithTag(GroupGameResponseTags.Change).performClick()
        compose.onRoot().captureRoboImage("screenshots/hero/group-hero-changing.png")
    }

    @Test fun responseFailed() = capture("group-hero-response-failed", GroupHeroPreviewData.responseFailed)

    @Test fun closed() = capture("group-hero-closed", GroupHeroPreviewData.closed)

    @Test fun closedPending() = capture("group-hero-closed-pending", GroupHeroPreviewData.closedPending)

    @Test fun dayMemberFee() = capture("group-hero-day-member-fee", GroupHeroPreviewData.dayMemberFee)

    @Test fun rosterStale() = capture("group-hero-roster-stale", GroupHeroPreviewData.rosterStale)

    @Test fun rosterRefreshing() = capture("group-hero-roster-refreshing", GroupHeroPreviewData.rosterRefreshing)

    @Test fun mapFailed() = capture("group-hero-map-failed", GroupHeroPreviewData.mapFailed)

    @Test fun noAddress() = capture("group-hero-no-address", GroupHeroPreviewData.noAddress)

    @Test fun autoConfirmationFailed() = capture("group-hero-auto-failed", GroupHeroPreviewData.autoConfirmationFailed)

    @Test fun autoConfirmationUpdating() = capture("group-hero-auto-updating", GroupHeroPreviewData.autoConfirmationUpdating)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun athleteIntro() = capture("group-hero-athlete-intro", GroupHeroPreviewData.athleteIntro)

    @Test fun noGame() = capture("group-hero-no-game", GroupHeroPreviewData.noGame)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun reserve() = capture("group-hero-reserve", GroupHeroPreviewData.reserve)

    @Test
    @Config(qualifiers = "+h1600dp")
    fun dayMemberList() = capture("group-hero-day-member-list", GroupHeroPreviewData.dayMemberList)

    @Test fun adminPending() = capture("group-hero-admin-pending", GroupHeroPreviewData.adminPending)

    @Test fun adminConfirmed() = capture("group-hero-admin-confirmed", GroupHeroPreviewData.adminConfirmed)

    @Test fun adminClosed() = capture("group-hero-admin-closed", GroupHeroPreviewData.adminClosed)

    @Test fun adminNoGame() = capture("group-hero-admin-no-game", GroupHeroPreviewData.adminNoGame)

    @Test fun adminFirstGame() = capture("group-hero-admin-first-game", GroupHeroPreviewData.adminFirstGame)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun adminInviteGuide() = capture("group-hero-admin-invite-guide", GroupHeroPreviewData.adminInviteGuide)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun adminFinanceGuide() = capture("group-hero-admin-finance-guide", GroupHeroPreviewData.adminFinanceGuide)
}
