package br.com.saqz.profile.presentation.photo

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfilePhotoSelectionSheetTest {
    @Test
    fun `sheet exposes the three actions and forwards each selection`() = runComposeUiTest {
        val actions = mutableListOf<String>()
        setContent {
            SaqzTheme {
                ProfilePhotoSelectionSheet(
                    open = true,
                    photoUrl = "/api/session/photo?v=preview",
                    onClose = {},
                    onTakePhoto = { actions += "camera" },
                    onChooseFromGallery = { actions += "library" },
                    onRemovePhoto = { actions += "remove" },
                )
            }
        }

        onNodeWithText("Tirar foto").assertExists()
        onNodeWithText("Escolher da galeria").assertExists()
        onNodeWithText("Remover foto").assertExists()
        onNodeWithTag(ProfilePhotoTags.Camera).performClick()
        onNodeWithTag(ProfilePhotoTags.Library).performClick()
        onNodeWithTag(ProfilePhotoTags.Remove).performClick()

        assertEquals(listOf("camera", "library", "remove"), actions)
    }

    @Test
    fun `permission errors have their own visible messages`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                ProfilePhotoSelectionSheet(
                    open = true,
                    photoUrl = null,
                    onClose = {},
                    onTakePhoto = {},
                    onChooseFromGallery = {},
                    onRemovePhoto = {},
                    error = ProfilePhotoError.CameraPermissionDenied,
                )
            }
        }

        onNodeWithText("Permita o acesso à câmera nas configurações do celular para tirar uma foto.").assertExists()
        onNodeWithText("Permita o acesso às fotos nas configurações do celular para escolher uma imagem.")
            .assertDoesNotExist()
    }

    @Test
    fun `sheet hides remove action without a photo`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                ProfilePhotoSelectionSheet(
                    open = true,
                    photoUrl = null,
                    onClose = {},
                    onTakePhoto = {},
                    onChooseFromGallery = {},
                    onRemovePhoto = {},
                )
            }
        }

        onNodeWithTag(ProfilePhotoTags.Remove).assertDoesNotExist()
    }
}
