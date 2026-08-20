package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoByteSource
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoError
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoMediaType
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoReceipt
import br.com.saqz.groups.domain.photo.GroupPhotoSelection
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoSourceHandle
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
import br.com.saqz.groups.presentation.FakeGroupSystemTimeZonePort
import br.com.saqz.groups.presentation.photo.GROUP_PHOTO_PNG_PIXEL
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoViewModel
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupSetupRootTest {
    @Test
    fun `creating a group uploads the held photo before leaving`() = runComposeUiTest {
        val photos = FakePhotoGateway()
        val created = mutableListOf<String>()
        val setup = createViewModel()
        val photo = photoViewModel(photos)

        setContent {
            SaqzTheme {
                GroupSetupRoot(
                    mode = GroupSetupMode.Create,
                    onGroupCreate = created::add,
                    onGroupSave = {},
                    onGroupDelete = {},
                    onDraftSave = {},
                    onBack = {},
                    viewModel = setup,
                    photoViewModel = photo,
                )
            }
        }
        waitForIdle()
        photo.onIntent(GroupPhotoIntent.ChooseCamera)
        waitUntil(timeoutMillis = 10_000) { photo.state.value.hasPending && !photo.state.value.isLoading }
        assertEquals(0, photos.uploadCalls)

        setup.onIntent(GroupSetupIntent.ConfirmCreate)
        waitUntil(timeoutMillis = 10_000) { created.isNotEmpty() }

        assertEquals(1, photos.uploadCalls)
        assertEquals(listOf("group-1"), created)
        assertTrue(!photo.state.value.hasPending)
    }

    @Test
    fun `creating without a photo leaves immediately`() = runComposeUiTest {
        val photos = FakePhotoGateway()
        val created = mutableListOf<String>()
        val setup = createViewModel()

        setContent {
            SaqzTheme {
                GroupSetupRoot(
                    mode = GroupSetupMode.Create,
                    onGroupCreate = created::add,
                    onGroupSave = {},
                    onGroupDelete = {},
                    onDraftSave = {},
                    onBack = {},
                    viewModel = setup,
                    photoViewModel = photoViewModel(photos),
                )
            }
        }
        waitForIdle()
        setup.onIntent(GroupSetupIntent.ConfirmCreate)
        waitForIdle()

        assertEquals(0, photos.uploadCalls)
        assertEquals(listOf("group-1"), created)
    }

    @Test
    fun `saving an edit uploads the held photo before leaving`() = runComposeUiTest {
        val photos = FakePhotoGateway()
        var saved = 0
        val setup = createViewModel(GroupSetupMode.Edit("group-1"))
        val photo = photoViewModel(photos)

        setContent {
            SaqzTheme {
                GroupSetupRoot(
                    mode = GroupSetupMode.Edit("group-1"),
                    onGroupCreate = {},
                    onGroupSave = { saved++ },
                    onGroupDelete = {},
                    onDraftSave = {},
                    onBack = {},
                    viewModel = setup,
                    photoViewModel = photo,
                )
            }
        }
        waitForIdle()
        photo.onIntent(GroupPhotoIntent.ChooseCamera)
        waitUntil(timeoutMillis = 10_000) { photo.state.value.hasPending && !photo.state.value.isLoading }
        assertEquals(0, photos.uploadCalls)

        setup.onIntent(GroupSetupIntent.Submit)
        waitUntil(timeoutMillis = 10_000) { saved == 1 }

        assertEquals(1, photos.uploadCalls)
        assertEquals(1, saved)
        assertTrue(!photo.state.value.hasPending)
    }

    private fun createViewModel(
        mode: GroupSetupMode = GroupSetupMode.Create,
    ) = GroupSetupViewModel(
        initialState = GroupSetupState(mode = mode, form = completeForm, isLoading = mode is GroupSetupMode.Edit),
        savedState = SavedStateHandle(),
        groupGateway = FakeGroupGateway(),
        profileGateway = FakeGroupProfileGateway(),
        timeZonePort = FakeGroupSystemTimeZonePort(),
    )

    private fun photoViewModel(gateway: FakePhotoGateway) = GroupPhotoViewModel(
        profileGateway = FakeGroupProfileGateway(),
        photoGateway = gateway,
        selection = ImmediateSelection(),
        encoder = ImmediateEncoder,
        previews = GroupPhotoPreviewPort { GROUP_PHOTO_PNG_PIXEL },
    )

    private class ImmediateSelection : GroupPhotoSelectionPort {
        override suspend fun chooseCamera() = GroupPhotoSelectionResult.Selected(sampleSelection())

        override suspend fun chooseLibrary() = GroupPhotoSelectionResult.Selected(sampleSelection())

        override fun cleanup(source: String) = Unit
    }

    private object ImmediateEncoder : br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort {
        override suspend fun encode(source: String, crop: GroupPhotoCrop) = GroupPhotoEncodingResult.Encoded(
            EncodedGroupPhoto(
                mediaType = GroupPhotoMediaType.JPEG,
                contentLength = 1,
                source = GroupPhotoByteSource { byteArrayOf(1) },
            ),
        )

        override fun cancel(source: String) = Unit
    }

    private class FakePhotoGateway : GroupPhotoGateway {
        var uploadCalls = 0
        private var readCalls = 0

        override suspend fun upload(command: GroupPhotoUploadCommand): SaqzResult<GroupPhotoReceipt, GroupPhotoError> {
            uploadCalls++
            return SaqzResult.Success(GroupPhotoReceipt(GroupPhotoVersionToken("\"group-3\"")))
        }

        override suspend fun read(
            groupId: GroupId,
            version: GroupPhotoVersionToken?,
        ): SaqzResult<GroupPhotoReadResult, GroupPhotoError> {
            val versionValue = if (readCalls++ == 0) "\"photo-1\"" else "\"photo-2\""
            return SaqzResult.Success(
                GroupPhotoReadResult.Available(
                    GROUP_PHOTO_PNG_PIXEL,
                    GroupPhotoMediaType.JPEG.value,
                    GroupPhotoVersionToken(versionValue),
                ),
            )
        }

        override suspend fun remove(
            groupId: GroupId,
            groupVersion: GroupPhotoVersionToken,
        ): SaqzResult<GroupPhotoReceipt, GroupPhotoError> =
            SaqzResult.Success(GroupPhotoReceipt(GroupPhotoVersionToken("\"group-4\"")))
    }

    private companion object {
        val completeForm = GroupSetupForm(
            name = "Vôlei do CERET",
            modality = GroupModality.COURT_VOLLEYBALL,
            composition = GroupComposition.MIXED,
            level = GroupLevel.INTERMEDIATE,
            defaultVenue = GroupVenueForm(name = "CERET — Quadra 2", address = "R. Canuto Abreu, s/n"),
            regularSlots = listOf(
                GroupRegularSlotForm(
                    weekday = GroupWeekday.TUESDAY,
                    startTime = "19:30",
                    durationMinutes = 120,
                ),
            ),
            defaultCapacity = 12,
            defaultConfirmationLeadMinutes = 360,
        )

        fun sampleSelection() = GroupPhotoSelection(
            source = GroupPhotoSourceHandle("source"),
            preview = br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle("preview"),
            width = 100,
            height = 100,
        )
    }
}
