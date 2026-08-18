package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class IdentityPhotoSelectionSheetTest {
    @Test
    fun `sheet exposes camera and gallery and forwards each selection`() = runComposeUiTest {
        val actions = mutableListOf<String>()
        setContent {
            SaqzTheme {
                IdentityPhotoSelectionSheet(
                    open = true,
                    onClose = {},
                    onTakePhoto = { actions += "camera" },
                    onChooseFromGallery = { actions += "library" },
                )
            }
        }

        onNodeWithText("Tirar foto").assertExists()
        onNodeWithText("Escolher da galeria").assertExists()
        onNodeWithTag(IdentityPhotoTags.Camera).performClick()
        onNodeWithTag(IdentityPhotoTags.Library).performClick()

        assertEquals(listOf("camera", "library"), actions)
    }
}
