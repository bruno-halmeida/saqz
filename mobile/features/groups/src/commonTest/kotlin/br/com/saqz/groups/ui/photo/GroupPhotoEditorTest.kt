package br.com.saqz.groups.ui.photo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.port.GroupPhotoSelection
import br.com.saqz.groups.port.GroupPhotoSourceHandle
import br.com.saqz.groups.presentation.photo.ExistingGroupPhoto
import br.com.saqz.groups.presentation.photo.GroupPhotoError
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupPhotoEditorTest {
    @Test fun `fallback initials are deterministic for empty single and multiple words`() {
        assertEquals("SG", groupPhotoInitials("  "))
        assertEquals("VO", groupPhotoInitials("Volei"))
        assertEquals("VT", groupPhotoInitials("Volei de Terca"))
    }

    @Test fun `registration explains that photo is optional`() = runComposeUiTest {
        setup(optional = true)
        onNodeWithText("Opcional. Você pode continuar sem foto e alterar depois.").assertExists()
    }

    @Test fun `missing photo renders deterministic fallback`() = runComposeUiTest {
        setup(groupName = "Volei Terca")
        onNodeWithTag(GroupPhotoTags.Fallback).assertExists()
        onNodeWithText("VT").assertExists()
    }

    @Test fun `athlete sees photo without edit actions`() = runComposeUiTest {
        setup(canEdit = false)
        onNodeWithTag(GroupPhotoTags.Preview).assertExists()
        onNodeWithTag(GroupPhotoTags.Camera).assertDoesNotExist()
        onNodeWithTag(GroupPhotoTags.Library).assertDoesNotExist()
    }

    @Test fun `organizer can choose camera and library`() = runComposeUiTest {
        setup()
        onNodeWithTag(GroupPhotoTags.Camera).assertExists()
        onNodeWithTag(GroupPhotoTags.Library).assertExists()
    }

    @Test fun `camera action emits camera intent`() = runComposeUiTest {
        val intents = mutableListOf<GroupPhotoIntent>()
        setup(onIntent = intents::add)
        onNodeWithTag(GroupPhotoTags.Camera).performScrollTo().performClick()
        assertEquals(listOf<GroupPhotoIntent>(GroupPhotoIntent.ChooseCamera), intents)
    }

    @Test fun `library action emits library intent`() = runComposeUiTest {
        val intents = mutableListOf<GroupPhotoIntent>()
        setup(onIntent = intents::add)
        onNodeWithTag(GroupPhotoTags.Library).performScrollTo().performClick()
        assertEquals(listOf<GroupPhotoIntent>(GroupPhotoIntent.ChooseLibrary), intents)
    }

    @Test fun `selection uses automatic crop without manual controls`() = runComposeUiTest {
        setup(state = selectedState())
        listOf("Esquerda", "Direita", "Cima", "Baixo", "Diminuir", "Ampliar").forEach {
            onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test fun `registration confirmation defers upload and marks photo prepared`() = runComposeUiTest {
        val intents = mutableListOf<GroupPhotoIntent>()
        val prepared = mutableListOf<Boolean>()
        setup(state = selectedState(), deferUpload = true, onIntent = intents::add, onPrepared = prepared::add)
        onNodeWithTag(GroupPhotoTags.Confirm).performScrollTo().performClick()
        assertEquals(listOf(true), prepared)
        assertTrue(intents.isEmpty())
    }

    @Test fun `registration confirmation returns selected photo to compact state`() = runComposeUiTest {
        setContent {
            var prepared by remember { mutableStateOf(false) }
            SaqzTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    GroupPhotoEditor(
                        state = selectedState(),
                        groupName = "Volei de Terca",
                        canEdit = true,
                        optional = true,
                        deferUpload = true,
                        onIntent = {},
                        onPrepared = { prepared = it },
                        compactIdle = true,
                        prepared = prepared,
                    )
                }
            }
        }
        onNodeWithTag(GroupPhotoTags.Confirm).performScrollTo().performClick()
        onNodeWithTag(GroupPhotoTags.Confirm).assertDoesNotExist()
        onNodeWithTag(GroupPhotoTags.Preview, useUnmergedTree = true).assertExists()
        onNodeWithTag(GroupPhotoTags.Picker).assertHasClickAction()
    }

    @Test fun `profile confirmation uploads immediately`() = runComposeUiTest {
        val intents = mutableListOf<GroupPhotoIntent>()
        setup(state = selectedState(), deferUpload = false, onIntent = intents::add)
        onNodeWithTag(GroupPhotoTags.Confirm).performScrollTo().performClick()
        assertEquals(listOf<GroupPhotoIntent>(GroupPhotoIntent.Upload), intents)
    }

    @Test fun `cancel clears pending registration selection`() = runComposeUiTest {
        val intents = mutableListOf<GroupPhotoIntent>()
        val prepared = mutableListOf<Boolean>()
        setup(state = selectedState(), onIntent = intents::add, onPrepared = prepared::add)
        onNodeWithTag(GroupPhotoTags.Cancel).performScrollTo().performClick()
        assertEquals(listOf<GroupPhotoIntent>(GroupPhotoIntent.Cancel), intents)
        assertEquals(listOf(false), prepared)
    }

    @Test fun `existing photo exposes destructive remove`() = runComposeUiTest {
        val intents = mutableListOf<GroupPhotoIntent>()
        setup(state = existingState(), onIntent = intents::add)
        onNodeWithTag(GroupPhotoTags.Remove).performScrollTo().performClick()
        assertEquals(listOf<GroupPhotoIntent>(GroupPhotoIntent.Remove), intents)
    }

    @Test fun `upload failure keeps preview and offers retry`() = runComposeUiTest {
        val intents = mutableListOf<GroupPhotoIntent>()
        setup(state = selectedState(error = GroupPhotoError.UPLOAD_FAILED), onIntent = intents::add)
        onNodeWithTag(GroupPhotoTags.Preview).assertExists()
        onNodeWithText("A foto não foi enviada. Sua seleção foi mantida.").assertExists()
        onNodeWithTag(GroupPhotoTags.Retry).performScrollTo().performClick()
        assertEquals(listOf<GroupPhotoIntent>(GroupPhotoIntent.RetryUpload), intents)
    }

    @Test fun `invalid photo keeps selection and offers retry`() = runComposeUiTest {
        setup(state = selectedState(error = GroupPhotoError.ENCODING_FAILED))
        onNodeWithText("Esta foto não pôde ser processada. Ajuste ou escolha outra.").assertExists()
        onNodeWithTag(GroupPhotoTags.Retry).assertExists()
    }

    @Test fun `stale version offers authoritative reload instead of retry`() = runComposeUiTest {
        var reloads = 0
        setup(state = selectedState(error = GroupPhotoError.STALE_VERSION), onReloadTarget = { reloads++ })
        onNodeWithTag(GroupPhotoTags.Retry).assertDoesNotExist()
        onNodeWithTag(GroupPhotoTags.Reload).performScrollTo().performClick()
        assertEquals(1, reloads)
    }

    @Test fun `remove failure keeps existing photo and retries removal`() = runComposeUiTest {
        val intents = mutableListOf<GroupPhotoIntent>()
        setup(state = existingState(error = GroupPhotoError.REMOVE_FAILED), onIntent = intents::add)
        onNodeWithTag(GroupPhotoTags.Preview).assertExists()
        onNodeWithTag(GroupPhotoTags.Retry).performScrollTo().performClick()
        assertEquals(listOf<GroupPhotoIntent>(GroupPhotoIntent.Remove), intents)
    }

    @Test fun `busy selection shows progress and disables source actions`() = runComposeUiTest {
        setup(state = GroupPhotoState(stage = GroupPhotoStage.SELECTING))
        onNodeWithTag(GroupPhotoTags.Progress).assertExists()
        onNodeWithTag(GroupPhotoTags.Camera).assertIsNotEnabled()
        onNodeWithTag(GroupPhotoTags.Library).assertIsNotEnabled()
    }

    @Test fun `compact photo actions retain 48 dp touch targets`() = runComposeUiTest {
        setup()
        onNodeWithTag(GroupPhotoTags.Camera).performScrollTo().assertHeightIsAtLeast(48.dp)
        onNodeWithTag(GroupPhotoTags.Library).performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setup(
        state: GroupPhotoState = GroupPhotoState(),
        groupName: String = "Volei de Terca",
        canEdit: Boolean = true,
        optional: Boolean = false,
        deferUpload: Boolean = false,
        onIntent: (GroupPhotoIntent) -> Unit = {},
        onPrepared: (Boolean) -> Unit = {},
        onReloadTarget: () -> Unit = {},
    ) = setContent {
        SaqzTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                GroupPhotoEditor(state, groupName, canEdit, optional, deferUpload, onIntent, onPrepared, onReloadTarget)
            }
        }
    }

    private companion object {
        fun selectedState(error: GroupPhotoError? = null) = GroupPhotoState(
            selection = GroupPhotoSelection(
                GroupPhotoSourceHandle("source"),
                GroupPhotoPreviewHandle("preview"),
                width = 1200,
                height = 900,
            ),
            stage = GroupPhotoStage.CROPPING,
            error = error,
        )

        fun existingState(error: GroupPhotoError? = null) = GroupPhotoState(
            groupId = "group-1",
            groupEtag = "etag-1",
            existing = ExistingGroupPhoto(GroupPhotoPreviewHandle("existing"), "photo-etag"),
            error = error,
        )
    }
}
