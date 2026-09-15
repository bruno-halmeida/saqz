package br.com.saqz.groups.presentation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.domain.communication.NotificationPreferences
import br.com.saqz.groups.presentation.communication.GroupThreadScreen
import br.com.saqz.groups.presentation.communication.GroupThreadState
import br.com.saqz.groups.presentation.communication.NotificationCenterScreen
import br.com.saqz.groups.presentation.communication.NotificationCenterState
import br.com.saqz.groups.presentation.communication.NotificationUi
import br.com.saqz.groups.presentation.communication.ThreadMessageUi
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import br.com.saqz.groups.presentation.details.OwnChargeUi
import br.com.saqz.groups.presentation.details.OwnChargesUi
import br.com.saqz.groups.presentation.memberprofile.MemberProfileScreen
import br.com.saqz.groups.presentation.memberprofile.MemberProfileState
import br.com.saqz.groups.presentation.monthlypayments.MonthlyPaymentsGroupUi
import br.com.saqz.groups.presentation.monthlypayments.OwnMonthlyPaymentsScreen
import br.com.saqz.groups.presentation.monthlypayments.OwnMonthlyPaymentsState
import br.com.saqz.groups.presentation.ui.details.GroupLeaveSheet
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

open class ConnectedFlowsScreenshotScene {
    @get:Rule val compose = createComposeRule()

    protected fun leave(name: String, state: GroupDetailsState) = capture(name) { GroupLeaveSheet(state, {}) }
    protected fun member(name: String, state: MemberProfileState) = capture(name) { MemberProfileScreen(state, {}, {}) }
    protected fun monthly(name: String, state: OwnMonthlyPaymentsState) = capture(name) { OwnMonthlyPaymentsScreen(state, {}, {}) }
    protected fun thread(name: String, state: GroupThreadState, notices: Boolean = false) = capture(name) {
        GroupThreadScreen(state, notices, {}, {})
    }
    protected fun notifications(name: String, state: NotificationCenterState, settings: Boolean = false) = capture(name) {
        NotificationCenterScreen(state, settings, {}, {})
    }
    protected fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme { Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) { content() } }
        }
        compose.onRoot().captureRoboImage("screenshots/connected-flows/$name.png")
    }

    protected val memberState = MemberProfileState(
        loading = false, name = "Ana Souza", attributes = listOf("Ponteira", "Intermediário"),
    )
    protected val message = ThreadMessageUi(
        "one", "Bruno Almeida", "O jogo de sábado será na quadra 2. Confirmem a presença pelo app.", "09/09 18:00",
    )
    protected val threadState = GroupThreadState(
        loading = false, messages = listOf(message), canPost = true, draft = "Confirmado para sábado!",
    )
    protected val inboxState = NotificationCenterState(loading = false, items = listOf(
        NotificationUi(2, "a", CommunicationChannel.NOTICE, null, message, false),
        NotificationUi(1, "a", CommunicationChannel.CHAT, null, message.copy(id = "two", author = "Ana", body = "Obrigada!"), true),
    ))
    protected val settingsState = NotificationCenterState(loading = false, preferences = NotificationPreferences(messages = false))
    protected val monthlyState = OwnMonthlyPaymentsState(loading = false, groups = listOf(MonthlyPaymentsGroupUi(
        "a", "Vôlei de sábado", OwnChargesUi(
            pending = listOf(OwnChargeUi("p", "Mensalidade · Setembro", "Vence em 10/09", "R$ 80,00", OwnChargeStatusUi.Pending)),
            history = listOf(OwnChargeStatusUi.Paid, OwnChargeStatusUi.Waived, OwnChargeStatusUi.Cancelled).mapIndexed { i, status ->
                OwnChargeUi("h$i", "Mensalidade · ${8 - i}/2026", "Vencimento: 10/${8 - i}", "R$ 80,00", status)
            },
        ),
    )))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ConnectedProfileFlowsScreenshotTest : ConnectedFlowsScreenshotScene() {
    @Test fun leaveConfirm() = leave("leave-confirm", GroupDetailsState(confirmingLeave = true))
    @Test fun leaveLoading() = leave("leave-loading", GroupDetailsState(confirmingLeave = true, leaving = true))
    @Test fun leaveError() = leave("leave-error", GroupDetailsState(confirmingLeave = true, leaveFailed = true))

    @Test fun memberPrivate() = member("member-private", memberState)
    @Test fun memberStats() = member(
        "member-stats", memberState.copy(phone = "+55 11 99999-0000", games = "8", attendance = "75%", absences = "2"),
    )
    @Test fun memberStatsError() = member("member-stats-error", memberState.copy(statsFailed = true))
    @Test fun memberError() = member("member-error", MemberProfileState(loading = false, error = GroupUiError.Network))
    @Test fun memberLoading() = member("member-loading", MemberProfileState())
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ConnectedMonthlyFlowsScreenshotTest : ConnectedFlowsScreenshotScene() {
    @Test fun monthlyStatuses() = monthly("monthly-statuses", monthlyState)
    @Test fun monthlyEmpty() = monthly("monthly-empty", OwnMonthlyPaymentsState(loading = false))
    @Test fun monthlyError() = monthly("monthly-error", OwnMonthlyPaymentsState(loading = false, error = GroupUiError.Network))
    @Test fun monthlyPartialError() = monthly("monthly-partial-error", monthlyState.copy(groups = monthlyState.groups +
        MonthlyPaymentsGroupUi("b", "Vôlei da praia", OwnChargesUi(failed = true))))
    @Test fun monthlyLoading() = monthly("monthly-loading", OwnMonthlyPaymentsState())
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ConnectedThreadFlowsScreenshotTest : ConnectedFlowsScreenshotScene() {
    @Test fun noticeReadOnly() = thread("notice-read-only", threadState.copy(canPost = false), true)
    @Test fun noticeAdmin() = thread("notice-admin", threadState, true)
    @Test fun chatEmpty() = thread("chat-empty", GroupThreadState(loading = false, canPost = true))
    @Test fun chatLoading() = thread("chat-loading", GroupThreadState())
    @Test fun chatError() = thread("chat-error", GroupThreadState(loading = false, error = GroupUiError.Network))
    @Test fun chatSending() = thread("chat-sending", threadState.copy(sending = true))
    @Test fun chatSendError() = thread("chat-send-error", threadState.copy(sendFailed = true))
    @Test fun chatPageError() = thread("chat-page-error", threadState.copy(pageFailed = true, nextCursor = 2))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ConnectedNotificationFlowsScreenshotTest : ConnectedFlowsScreenshotScene() {
    @Test fun inbox() = notifications("inbox", inboxState)
    @Test fun inboxEmpty() = notifications("inbox-empty", NotificationCenterState(loading = false))
    @Test fun inboxLoading() = notifications("inbox-loading", NotificationCenterState())
    @Test fun inboxError() = notifications("inbox-error", NotificationCenterState(loading = false, error = GroupUiError.Network))
    @Test fun inboxReadError() = notifications("inbox-read-error", inboxState.copy(actionFailed = true))
    @Test fun settings() = notifications("settings", settingsState, true)
    @Test fun settingsSaving() = notifications("settings-saving", settingsState.copy(busy = true), true)
    @Test fun settingsSaved() = notifications("settings-saved", settingsState.copy(saved = true), true)
    @Test fun settingsError() = notifications("settings-error", settingsState.copy(actionFailed = true), true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class NotificationChannelSettingsScreenshotTest : ConnectedFlowsScreenshotScene() {
    @Test fun pushSettings() = notifications("settings-push", settingsState.copy(
        settingsChannel = br.com.saqz.groups.presentation.communication.NotificationSettingsChannel.PUSH), true)
    @Test fun whatsappSettings() = notifications("settings-whatsapp", settingsState.copy(
        settingsChannel = br.com.saqz.groups.presentation.communication.NotificationSettingsChannel.WHATSAPP), true)
    @Test fun whatsappEnabled() = notifications("settings-whatsapp-enabled", settingsState.copy(
        settingsChannel = br.com.saqz.groups.presentation.communication.NotificationSettingsChannel.WHATSAPP,
        preferences = NotificationPreferences(
            whatsapp = br.com.saqz.groups.domain.communication.WhatsAppPreferences(true, true, true))), true)
    @Test fun whatsappSaving() = notifications("settings-whatsapp-saving", settingsState.copy(busy = true,
        settingsChannel = br.com.saqz.groups.presentation.communication.NotificationSettingsChannel.WHATSAPP), true)
}
