package br.com.saqz.groups.presentation.memberprofile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MemberProfileScreenTest {
    private val loaded = MemberProfileState(loading = false, name = "Ana Souza", attributes = listOf("Ponteira"))

    @Test fun anotherMembersProfileOffersReportAndBlock() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            SaqzTheme {
                MemberProfileScreen(loaded, {}, {}, onReport = { calls += "report" }, onToggleBlock = { calls += "block" })
            }
        }

        onNodeWithTag(MemberProfileTags.Report).performScrollTo().performClick()
        onNodeWithText("Bloquear").performScrollTo().performClick()
        assertEquals(listOf("report", "block"), calls)
    }

    @Test fun blockedMemberOffersUnblock() = runComposeUiTest {
        setContent { SaqzTheme { MemberProfileScreen(loaded.copy(blocked = true), {}, {}) } }

        onNodeWithText("Desbloquear").assertExists()
        onNodeWithText("Bloquear").assertDoesNotExist()
    }

    @Test fun ownProfileHasNoModerationActions() = runComposeUiTest {
        setContent { SaqzTheme { MemberProfileScreen(loaded.copy(isSelf = true), {}, {}) } }

        onNodeWithTag(MemberProfileTags.Report).assertDoesNotExist()
        onNodeWithTag(MemberProfileTags.Block).assertDoesNotExist()
    }
}
