package br.com.saqz.groups.presentation.setup

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.CreateGroupProfileCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupFinanceDefaults
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupDraftResource
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupPlayStyle
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.domain.group.GroupSetupForm
import br.com.saqz.groups.model.GroupTimeZone as SystemGroupTimeZone
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.port.GroupDraftFailure
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSetupViewModelTest {
    @Test
    fun `create initializes every section with one stable command key and detected timezone`() = runTest {
        val fixture = fixture()

        assertEquals(GroupSetupMode.CREATE, fixture.viewModel.state.value.mode)
        assertEquals("command-1", fixture.viewModel.state.value.commandKey)
        assertEquals("America/Sao_Paulo", fixture.viewModel.state.value.timeZone?.id)
        assertEquals(newGroupDefaults(), fixture.viewModel.state.value.form)
        assertFalse(fixture.viewModel.state.value.timezoneSelectionRequired)
    }

    @Test
    fun `timezone failure requires friendly fallback and rejects invalid identifiers`() = runTest {
        val fixture = fixture(timeZoneResult = GroupSystemTimeZoneResult.Unavailable)

        assertTrue(fixture.viewModel.state.value.timezoneSelectionRequired)
        fixture.viewModel.onIntent(GroupSetupIntent.SelectFallbackTimeZone("Mars/Olympus"))
        assertEquals(listOf("must be a valid timezone"), fixture.viewModel.state.value.fieldErrors["timeZone"])
        fixture.viewModel.onIntent(GroupSetupIntent.SelectFallbackTimeZone("Europe/Lisbon"))
        assertEquals("Europe/Lisbon", fixture.viewModel.state.value.timeZone?.id)
        assertFalse(fixture.viewModel.state.value.timezoneSelectionRequired)
    }

    @Test
    fun `matching versioned draft restores form version etag and stable command key`() = runTest {
        val draft = GroupSetupDraft(
            resource = GroupDraftResource.CREATE_GROUP,
            groupId = null,
            groupVersion = 4,
            etag = "\"4\"",
            commandKey = "restored-command",
            form = validForm().copy(name = "Restored Group").toDraftForm(),
        )
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft))

        assertEquals("Restored Group", fixture.viewModel.state.value.form.name)
        assertEquals("restored-command", fixture.viewModel.state.value.commandKey)
        assertEquals(4, fixture.viewModel.state.value.groupVersion)
        assertEquals("\"4\"", fixture.viewModel.state.value.etag)
    }

    @Test
    fun `draft read failure is typed and keeps a usable empty form`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Failure(GroupDraftFailure.CORRUPT))

        assertEquals(GroupSetupError.DRAFT_UNAVAILABLE, fixture.viewModel.state.value.error)
        assertEquals(newGroupDefaults(), fixture.viewModel.state.value.form)
    }

    @Test
    fun `non-court modality immediately clears style and custom style`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm().copy(
            modality = GroupModality.COURT_VOLLEYBALL,
            playStyle = GroupPlayStyle.CUSTOM,
            customPlayStyle = "Fast",
        ))))

        fixture.viewModel.onIntent(GroupSetupIntent.UpdateModality(GroupModality.FOOTVOLLEY))

        assertNull(fixture.viewModel.state.value.form.playStyle)
        assertNull(fixture.viewModel.state.value.form.customPlayStyle)
        assertFalse(fixture.viewModel.state.value.showPlayStyle)
    }

    @Test
    fun `preset level immediately clears obsolete custom level`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm().copy(
            level = GroupLevel.CUSTOM,
            customLevel = "Elite",
        ))))

        fixture.viewModel.onIntent(GroupSetupIntent.UpdateLevel(GroupLevel.ADVANCED))

        assertEquals(GroupLevel.ADVANCED, fixture.viewModel.state.value.form.level)
        assertNull(fixture.viewModel.state.value.form.customLevel)
        assertFalse(fixture.viewModel.state.value.showCustomLevel)
    }

    @Test
    fun `preset play style immediately clears obsolete custom style`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm().copy(
            modality = GroupModality.COURT_VOLLEYBALL,
            playStyle = GroupPlayStyle.CUSTOM,
            customPlayStyle = "Fast",
        ))))

        fixture.viewModel.onIntent(GroupSetupIntent.UpdatePlayStyle(GroupPlayStyle.FIVE_ONE))

        assertEquals(GroupPlayStyle.FIVE_ONE, fixture.viewModel.state.value.form.playStyle)
        assertNull(fixture.viewModel.state.value.form.customPlayStyle)
        assertFalse(fixture.viewModel.state.value.showCustomPlayStyle)
    }

    @Test
    fun `invalid required form submits no request and exposes exact fields`() = runTest {
        val fixture = fixture(
            draftResult = GroupDraftReadResult.Success(draft(GroupSetupForm())),
        )

        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(fixture.gateway.creates.isEmpty())
        assertEquals(setOf("name", "modality", "composition"), fixture.viewModel.state.value.fieldErrors.keys)
        assertTrue(fixture.viewModel.state.value.validationAttempted)
    }

    @Test
    fun `invalid nested defaults expose exact paths and submit no request`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm().copy(
            defaultVenue = GroupVenue(name = "", address = "x"),
            regularSlots = listOf(GroupRegularSlot(weekday = GroupWeekday.MONDAY, startTime = "", durationMinutes = 10)),
            defaultCapacity = 1,
            monthlyFeeCents = 7000,
            monthlyDueDay = null,
        ))))

        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(fixture.gateway.creates.isEmpty())
        assertEquals(
            setOf("defaultVenue.name", "defaultVenue.address", "regularSlots[0].startTime", "regularSlots[0].durationMinutes", "defaultCapacity", "monthlyDueDay"),
            fixture.viewModel.state.value.fieldErrors.keys,
        )
    }

    @Test
    fun `duplicate submit remains single flight with one command key`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm())))

        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(1, fixture.gateway.creates.size)
        assertEquals("restored-command", fixture.gateway.creates.single().commandKey)
    }

    @Test
    fun `failed create retry reuses the same command key`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm())))
        fixture.gateway.createResult = failure(503, "UNAVAILABLE")

        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        fixture.gateway.createResult = SaqzResult.Success(group())
        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(listOf("restored-command", "restored-command"), fixture.gateway.creates.map { it.commandKey })
        assertEquals(GROUP_ID, fixture.viewModel.state.value.successGroupId)
    }

    @Test
    fun `confirmed create clears only matching draft and selects then opens group`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm())))

        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(Triple(GroupDraftKey(GroupDraftResource.CREATE_GROUP, null), "restored-command", Unit), fixture.drafts.clears.single())
        assertEquals(GroupSetupEffect.SelectGroup(GROUP_ID), fixture.viewModel.effects.first())
        assertEquals(GroupSetupEffect.OpenGroup(GROUP_ID), fixture.viewModel.effects.first())
        assertEquals(GROUP_ID, fixture.viewModel.state.value.successGroupId)
    }

    @Test
    fun `post-create photo failure remains retryable without another create`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm())))
        fixture.viewModel.onIntent(GroupSetupIntent.SetPhotoPending(true))
        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(GroupSetupEffect.UploadPhoto(GROUP_ID, "\"1\""), fixture.viewModel.effects.first())
        assertEquals("\"1\"", fixture.viewModel.state.value.etag)
        fixture.viewModel.onIntent(GroupSetupIntent.PhotoUploadFailed)
        assertTrue(fixture.viewModel.state.value.photoRetryAvailable)
        fixture.viewModel.onIntent(GroupSetupIntent.RetryPhotoUpload)
        assertEquals(GroupSetupEffect.UploadPhoto(GROUP_ID, "\"1\""), fixture.viewModel.effects.first())
        assertEquals(1, fixture.gateway.creates.size)
    }

    @Test
    fun `pending photo completes before created group navigation`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm())))
        fixture.viewModel.onIntent(GroupSetupIntent.SetPhotoPending(true))
        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(GroupSetupEffect.UploadPhoto(GROUP_ID, "\"1\""), fixture.viewModel.effects.first())
        fixture.viewModel.onIntent(GroupSetupIntent.PhotoUploadSucceeded)

        assertEquals(GroupSetupEffect.SelectGroup(GROUP_ID), fixture.viewModel.effects.first())
        assertEquals(GroupSetupEffect.OpenGroup(GROUP_ID), fixture.viewModel.effects.first())
        assertFalse(fixture.viewModel.state.value.photoPending)
    }

    @Test
    fun `cancelling pending photo after create continues without another create`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm())))
        fixture.viewModel.onIntent(GroupSetupIntent.SetPhotoPending(true))
        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        fixture.viewModel.effects.first()

        fixture.viewModel.onIntent(GroupSetupIntent.SetPhotoPending(false))

        assertEquals(GroupSetupEffect.SelectGroup(GROUP_ID), fixture.viewModel.effects.first())
        assertEquals(GroupSetupEffect.OpenGroup(GROUP_ID), fixture.viewModel.effects.first())
        assertEquals(1, fixture.gateway.creates.size)
    }

    @Test
    fun `edit initializes from authoritative nested profile and finance defaults`() = runTest {
        val fixture = fixture(existing = versioned())

        assertEquals(GroupSetupMode.EDIT, fixture.viewModel.state.value.mode)
        assertEquals("Existing Group", fixture.viewModel.state.value.form.name)
        assertEquals(GroupModality.BEACH_VOLLEYBALL, fixture.viewModel.state.value.form.modality)
        assertEquals(1500, fixture.viewModel.state.value.form.defaultGameFeeCents)
        assertEquals("\"7\"", fixture.viewModel.state.value.etag)
    }

    @Test
    fun `edit sends current group ETag and opens updated group`() = runTest {
        val fixture = fixture(existing = versioned())

        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(GROUP_ID, fixture.gateway.updates.single().groupId.value)
        assertEquals("\"7\"", fixture.gateway.updates.single().versionToken.value)
        assertEquals(GroupSetupEffect.OpenGroup(GROUP_ID), fixture.viewModel.effects.first())
        assertTrue(fixture.drafts.clears.single().first.resource == GroupDraftResource.UPDATE_GROUP)
    }

    @Test
    fun `version conflict retains draft and offers reload`() = runTest {
        val fixture = fixture(existing = versioned())
        fixture.gateway.updateResult = failure(409, "VERSION_CONFLICT")

        fixture.viewModel.onIntent(GroupSetupIntent.UpdateName("Local Draft"))
        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(fixture.viewModel.state.value.conflict)
        assertEquals("Local Draft", fixture.viewModel.state.value.form.name)
        assertTrue(fixture.drafts.clears.isEmpty())
        assertEquals("Local Draft", fixture.drafts.writes.last().form.name)
    }

    @Test
    fun `conflict reload replaces form and ETag with authoritative response`() = runTest {
        val fixture = fixture(existing = versioned())
        fixture.gateway.readResult = SaqzResult.Success(versioned(name = "Reloaded Group", version = 9, etag = "\"9\""))

        fixture.viewModel.onIntent(GroupSetupIntent.ReloadConflict)
        runCurrent()

        assertEquals("Reloaded Group", fixture.viewModel.state.value.form.name)
        assertEquals(9, fixture.viewModel.state.value.groupVersion)
        assertEquals("\"9\"", fixture.viewModel.state.value.etag)
        assertFalse(fixture.viewModel.state.value.conflict)
    }

    @Test
    fun `server validation preserves exact field paths and local draft`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm())))
        fixture.gateway.createResult = SaqzResult.Failure(
            GroupProfileError.Validation(
                ValidationDetails(
                    globalMessages = emptyList(),
                    fieldMessages = mapOf("defaultVenue.address" to listOf("invalid")),
                ),
            ),
        )

        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(listOf("invalid"), fixture.viewModel.state.value.fieldErrors["defaultVenue.address"])
        assertEquals(validForm().toDraftForm(), fixture.drafts.writes.last().form)
        assertNull(fixture.viewModel.state.value.error)
    }

    @Test
    fun `server validation without global message uses generic presentation fallback`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm())))
        fixture.gateway.createResult = SaqzResult.Failure(
            GroupProfileError.Validation(
                ValidationDetails(globalMessages = emptyList(), fieldMessages = emptyMap()),
            ),
        )

        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(
            listOf(GroupSetupValidationMessage.Generic),
            fixture.viewModel.state.value.validationMessages,
        )
    }

    @Test
    fun `server validation preserves safe global messages`() = runTest {
        val fixture = fixture(draftResult = GroupDraftReadResult.Success(draft(validForm())))
        fixture.gateway.createResult = SaqzResult.Failure(
            GroupProfileError.Validation(
                ValidationDetails(
                    globalMessages = listOf("Confira os dados do grupo"),
                    fieldMessages = emptyMap(),
                ),
            ),
        )

        fixture.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(
            listOf(GroupSetupValidationMessage.Safe("Confira os dados do grupo")),
            fixture.viewModel.state.value.validationMessages,
        )
    }

    @Test
    fun `forbidden and missing responses remain distinct typed errors`() = runTest {
        val forbidden = fixture(existing = versioned()).also { it.gateway.updateResult = failure(403, "ACCESS_FORBIDDEN") }
        forbidden.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        val missing = fixture(existing = versioned()).also { it.gateway.updateResult = failure(404, "GROUP_NOT_FOUND") }
        missing.viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(GroupSetupError.FORBIDDEN, forbidden.viewModel.state.value.error)
        assertEquals(GroupSetupError.NOT_FOUND, missing.viewModel.state.value.error)
    }

    @Test
    fun `each form mutation persists current command and resource identity`() = runTest {
        val fixture = fixture()

        fixture.viewModel.onIntent(GroupSetupIntent.UpdateName("Draft Group"))
        fixture.viewModel.onIntent(GroupSetupIntent.UpdateComposition(GroupComposition.MIXED))

        assertEquals(2, fixture.drafts.writes.size)
        assertEquals("command-1", fixture.drafts.writes.last().commandKey)
        assertEquals(GroupDraftResource.CREATE_GROUP, fixture.drafts.writes.last().resource)
        assertEquals("Draft Group", fixture.drafts.writes.last().form.name)
    }

    private fun TestScope.fixture(
        existing: VersionedGroup? = null,
        draftResult: GroupDraftReadResult = GroupDraftReadResult.Success(null),
        timeZoneResult: GroupSystemTimeZoneResult = GroupSystemTimeZoneResult.Available(zone()),
    ): Fixture {
        val gateway = FakeGateway()
        val drafts = FakeDrafts(draftResult)
        val viewModel = GroupSetupViewModel(
            GroupSetupInput(existing),
            gateway,
            GroupSystemTimeZonePort { it(timeZoneResult) },
            drafts,
            GroupCommandKeyFactory { "command-1" },
            backgroundScope,
        )
        return Fixture(viewModel, gateway, drafts)
    }

    private class FakeGateway : GroupProfileGateway {
        val creates = mutableListOf<CreateGroupProfileCommand>()
        val updates = mutableListOf<UpdateGroupProfileCommand>()
        var createResult: SaqzResult<Group, GroupProfileError> = SaqzResult.Success(group())
        var updateResult: SaqzResult<VersionedGroup, GroupProfileError> = SaqzResult.Success(versioned(version = 8, etag = "\"8\""))
        var readResult: SaqzResult<VersionedGroup, GroupProfileError> = SaqzResult.Success(versioned())
        override suspend fun createProfile(command: CreateGroupProfileCommand): SaqzResult<Group, GroupProfileError> {
            creates += command
            return createResult
        }
        override suspend fun readProfile(groupId: GroupId) = readResult
        override suspend fun updateProfile(command: UpdateGroupProfileCommand): SaqzResult<VersionedGroup, GroupProfileError> {
            updates += command
            return updateResult
        }
    }

    private class FakeDrafts(private val readResult: GroupDraftReadResult) : GroupDraftStorePort {
        val writes = mutableListOf<GroupSetupDraft>()
        val clears = mutableListOf<Triple<GroupDraftKey, String, Unit>>()
        override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) = done(readResult)
        override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) {
            writes += draft
            done(GroupDraftWriteResult.Success)
        }
        override fun clear(key: GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit) {
            clears += Triple(key, commandKey, Unit)
            done(GroupDraftWriteResult.Success)
        }
    }

    private data class Fixture(val viewModel: GroupSetupViewModel, val gateway: FakeGateway, val drafts: FakeDrafts)

    private companion object {
        const val GROUP_ID = "group-1"
        fun zone() = assertIs<SystemGroupTimeZone.ParseResult.Valid>(SystemGroupTimeZone.parse("America/Sao_Paulo")).value
        fun validForm() = GroupSetupForm("Training Club", GroupModality.COURT_VOLLEYBALL, GroupComposition.MIXED)
        fun draft(form: GroupSetupForm) = GroupSetupDraft(
            resource = GroupDraftResource.CREATE_GROUP,
            groupId = null,
            groupVersion = null,
            etag = null,
            commandKey = "restored-command",
            form = form.toDraftForm(),
        )
        fun group(name: String = "Training Club", version: Long = 1) = Group(
            GROUP_ID,
            name,
            "America/Sao_Paulo",
            version,
            GroupRole.OWNER,
        )
        fun versioned(name: String = "Existing Group", version: Long = 7, etag: String = "\"7\"") = VersionedGroup(
            group(name, version).copy(
                profile = GroupProfile(
                    modality = GroupModality.BEACH_VOLLEYBALL,
                    composition = GroupComposition.MIXED,
                    description = null,
                    city = null,
                    level = null,
                    customLevel = null,
                    playStyle = null,
                    customPlayStyle = null,
                    defaultVenue = null,
                    regularSlots = emptyList(),
                    defaultCapacity = null,
                    defaultConfirmationLeadMinutes = null,
                ),
                financeDefaults = GroupFinanceDefaults(
                    defaultGameFeeCents = 1500,
                    monthlyFeeCents = null,
                    monthlyDueDay = null,
                ),
            ),
            GroupVersionToken(etag),
        )
        fun failure(status: Int, code: String): SaqzResult.Failure<GroupProfileError> = SaqzResult.Failure(
            when {
                status == 409 -> GroupProfileError.Conflict()
                status == 403 -> GroupProfileError.DataFailure(DataError.Forbidden)
                status == 404 -> GroupProfileError.DataFailure(DataError.NotFound)
                else -> GroupProfileError.DataFailure(DataError.Server)
            },
        )
    }
}
