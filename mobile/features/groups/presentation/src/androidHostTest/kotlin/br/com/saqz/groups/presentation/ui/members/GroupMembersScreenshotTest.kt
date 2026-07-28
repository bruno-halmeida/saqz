package br.com.saqz.groups.presentation.ui.members

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.members.GroupMembersState
import br.com.saqz.groups.presentation.members.JoinRequestUi
import br.com.saqz.groups.presentation.members.MemberUi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupMembersScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun list() = capture("group-members-list") { GroupMembersScreen(sampleState, {}, {}) }

    @Test
    fun memberSheet() = capture("group-members-sheet-member") {
        GroupMembersScreen(sampleState.copy(selected = sampleMembers.first()), {}, {})
    }

    @Test
    fun adminSheet() = capture("group-members-sheet-admin") {
        GroupMembersScreen(sampleState.copy(selected = sampleAdmins.last()), {}, {})
    }

    @Test
    fun loading() = capture("group-members-loading") {
        GroupMembersScreen(GroupMembersState(), {}, {})
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-70/$name.png")
    }

    private val sampleAdmins = listOf(
        MemberUi("lucas", "Lucas Prado", "Criou o grupo · levantador", true, true, "42 jogos · 98% de presença"),
        MemberUi("bia", "Bia Souza", "Ponteira · desde março", true, false, "18 jogos · 92% de presença"),
    )

    private val sampleMembers = listOf(
        MemberUi("thiago", "Thiago Melo", "Central · mensalista", false, false, "31 jogos · 88% de presença"),
        MemberUi("camila", "Camila Alves", "Levantadora · avulso", false, false, "9 jogos · 74% de presença"),
        MemberUi("pedro", "Pedro Henrique", "Oposto · mensalista", false, false, "24 jogos · 91% de presença"),
        MemberUi("marina", "Marina Freitas", "Líbero · mensalista", false, false, "27 jogos · 95% de presença"),
    )

    private val sampleState = GroupMembersState(
        isLoading = false,
        totalCount = 26,
        adminCount = 2,
        pendingCount = 2,
        joinRequests = listOf(
            JoinRequestUi("julia", "Julia Martins", "Entrou pelo código · há 2h", awaitingReview = false),
            JoinRequestUi("rafael", "Rafael Costa", "Entrou pelo link · ontem", awaitingReview = true),
        ),
        admins = sampleAdmins,
        members = sampleMembers,
        shownCount = 4,
    )
}
