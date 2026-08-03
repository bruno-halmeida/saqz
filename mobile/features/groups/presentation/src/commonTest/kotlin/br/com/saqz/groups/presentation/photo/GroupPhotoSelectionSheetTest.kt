package br.com.saqz.groups.presentation.photo

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.setup.GroupPhotoSelectionSheet
import br.com.saqz.groups.presentation.ui.setup.GroupPhotoTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupPhotoSelectionSheetTest {
    @Test
    fun `sheet exposes the three actions and forwards each selection`() = runComposeUiTest {
        val actions = mutableListOf<String>()
        setContent {
            SaqzTheme {
                GroupPhotoSelectionSheet(
                    open = true,
                    photoUrl = "/api/groups/group-1/photo?v=preview",
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
        onNodeWithTag(GroupPhotoTags.Camera).performClick()
        onNodeWithTag(GroupPhotoTags.Library).performClick()
        onNodeWithTag(GroupPhotoTags.Remove).performClick()

        assertEquals(listOf("camera", "library", "remove"), actions)
    }

    @Test
    fun `permission error has a visible message`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupPhotoSelectionSheet(
                    open = true,
                    photoUrl = null,
                    onClose = {},
                    onTakePhoto = {},
                    onChooseFromGallery = {},
                    onRemovePhoto = {},
                    error = GroupPhotoUiError.CameraPermissionDenied,
                )
            }
        }

        onNodeWithText("Permita o acesso à câmera nas configurações do celular para tirar uma foto.")
            .assertExists()
    }
}
