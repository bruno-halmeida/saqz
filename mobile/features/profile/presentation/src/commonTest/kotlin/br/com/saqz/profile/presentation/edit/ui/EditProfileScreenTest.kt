package br.com.saqz.profile.presentation.edit.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import androidx.compose.runtime.remember
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.fake.FakeProfileGateway
import br.com.saqz.profile.presentation.edit.EditProfileIntent
import br.com.saqz.profile.presentation.edit.EditProfileState
import br.com.saqz.profile.presentation.photo.profilePhotoImageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EditProfileScreenTest {
    @Test
    fun `e mail fica desabilitado e nao aceita edicao`() = runComposeUiTest {
        content()

        assertEquals(
            4,
            onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `toque na foto sai pela lambda da tela`() = runComposeUiTest {
        var picked = false
        content(onPickPhoto = { picked = true })

        onNodeWithTag(EditProfileTags.Photo).assertHasClickAction().performClick()

        assertTrue(picked)
    }

    @Test
    fun `cada pilula envia a visibilidade escolhida`() = runComposeUiTest {
        var intent: EditProfileIntent? = null
        content(onIntent = { intent = it })

        onNodeWithTag(EditProfileTags.visibility(PhoneVisibility.EVERYONE)).performClick()

        assertEquals(EditProfileIntent.SelectPhoneVisibility(PhoneVisibility.EVERYONE), intent)
    }

    @Test
    fun `digests diferentes nao compartilham chave de cache da foto`() = runComposeUiTest {
        var oldMemoryKey: String? = null
        var newMemoryKey: String? = null
        var oldDiskKey: String? = null
        var newDiskKey: String? = null

        setContent {
            val context = LocalPlatformContext.current
            val oldRequest = remember(context) {
                profilePhotoImageRequest(context, "/api/session/photo?v=old")
            }
            val newRequest = remember(context) {
                profilePhotoImageRequest(context, "/api/session/photo?v=new")
            }
            oldMemoryKey = oldRequest.memoryCacheKey
            newMemoryKey = newRequest.memoryCacheKey
            oldDiskKey = oldRequest.diskCacheKey
            newDiskKey = newRequest.diskCacheKey
        }

        assertEquals("/api/session/photo?v=old", oldMemoryKey)
        assertEquals("/api/session/photo?v=new", newMemoryKey)
        assertNotEquals(oldMemoryKey, newMemoryKey)
        assertEquals("/api/session/photo?v=old", oldDiskKey)
        assertEquals("/api/session/photo?v=new", newDiskKey)
        assertNotEquals(oldDiskKey, newDiskKey)
    }

    private fun ComposeUiTest.content(
        onIntent: (EditProfileIntent) -> Unit = {},
        onPickPhoto: () -> Unit = {},
    ) = setContent {
        SaqzTheme {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
            EditProfileScreen(
                state = loadedState,
                onIntent = onIntent,
                onPickPhoto = onPickPhoto,
                onBack = {},
                imageLoader = imageLoader,
            )
        }
    }

    private val loadedState = EditProfileState.loaded(FakeProfileGateway().profile)
}
