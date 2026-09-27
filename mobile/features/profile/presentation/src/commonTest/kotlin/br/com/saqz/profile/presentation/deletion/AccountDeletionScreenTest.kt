package br.com.saqz.profile.presentation.deletion

import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.domain.AccountDeletionMethod
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AccountDeletionScreenTest {
    @Test fun `destructive controls require confirmation and apple is available only on supported devices`() = runComposeUiTest {
        setContent { SaqzTheme { AccountDeletionScreen(AccountDeletionState(userId = "profile", isLoading = false), {}, {}) } }
        onNodeWithTag(AccountDeletionTags.delete(AccountDeletionMethod.GOOGLE)).assertIsNotEnabled()
        onNodeWithTag(AccountDeletionTags.delete(AccountDeletionMethod.PASSWORD)).assertIsNotEnabled()
        onNodeWithTag(AccountDeletionTags.delete(AccountDeletionMethod.APPLE)).assertDoesNotExist()
    }

    @Test fun `apple confirmation dispatches the matching deletion intention`() = runComposeUiTest {
        var selected: AccountDeletionIntent? = null
        setContent { SaqzTheme {
            AccountDeletionScreen(AccountDeletionState(userId = "profile", isLoading = false, confirmed = true, supportsApple = true), { selected = it }, {})
        } }
        onNodeWithTag(AccountDeletionTags.delete(AccountDeletionMethod.APPLE)).performScrollTo().performClick()
        assertEquals(AccountDeletionIntent.Delete(AccountDeletionMethod.APPLE), selected)
    }
}
