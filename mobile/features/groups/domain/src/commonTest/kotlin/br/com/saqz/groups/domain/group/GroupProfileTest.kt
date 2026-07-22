package br.com.saqz.groups.domain.group

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.ValidationDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupProfileTest {
    @Test fun `all roles remain explicit`() = assertEquals(3, GroupRole.entries.size)
    @Test fun `all modalities remain explicit`() = assertEquals(3, GroupModality.entries.size)
    @Test fun `all compositions remain explicit`() = assertEquals(3, GroupComposition.entries.size)
    @Test fun `all levels remain explicit`() = assertEquals(5, GroupLevel.entries.size)
    @Test fun `all play styles remain explicit`() = assertEquals(4, GroupPlayStyle.entries.size)
    @Test fun `all weekdays remain explicit`() = assertEquals(7, GroupWeekday.entries.size)
    @Test fun `profile status remains explicit`() = assertEquals(listOf("COMPLETE", "INCOMPLETE"), GroupProfileStatus.entries.map { it.name })
    @Test fun `privacy remains private`() = assertEquals(listOf(GroupPrivacy.PRIVATE), GroupPrivacy.entries)
    @Test fun `currency remains BRL`() = assertEquals(listOf(GroupCurrency.BRL), GroupCurrency.entries)
    @Test fun `version token preserves exact quoting`() = assertEquals("\"7\"", GroupVersionToken("\"7\"").value)
    @Test fun `timezone preserves exact identifier`() = assertEquals("America/Sao_Paulo", GroupTimeZone("America/Sao_Paulo").id)
    @Test fun `venue preserves optional id and court`() = assertEquals(null, GroupVenue(null, "Gym", "Street", null).court)
    @Test fun `slot preserves schedule values`() = assertEquals(90, slot().durationMinutes)
    @Test fun `finance defaults preserve integer cents`() = assertEquals(2500L, finance().defaultGameFeeCents)
    @Test fun `profile preserves optional values`() = assertNull(profile().description)
    @Test fun `group preserves complete profile`() = assertEquals(GroupId("group-1"), group().id)
    @Test fun `versioned group preserves opaque token`() = assertEquals("v1", VersionedGroup(group(), GroupVersionToken("v1")).versionToken.value)
    @Test fun `create command preserves idempotency key`() = assertEquals("command-1", CreateGroupCommand("command-1", "Group", GroupTimeZone("UTC")).commandKey)
    @Test fun `update command preserves group and version`() = assertEquals("etag", UpdateGroupSettingsCommand(GroupId("g"), GroupVersionToken("etag"), "Group", GroupTimeZone("UTC")).versionToken.value)
    @Test fun `profile create command preserves full form`() = assertEquals(form(), CreateGroupProfileCommand("key", GroupTimeZone("UTC"), form()).form)
    @Test fun `profile update command preserves full form`() = assertEquals(form(), UpdateGroupProfileCommand(GroupId("g"), GroupVersionToken("v"), form()).form)
    @Test fun `cleaning trims safe optional fields`() = assertEquals("City", form(description = " ", city = " City ").cleaned().city)
    @Test fun `cleaning removes blank optional fields`() = assertNull(form(description = "  ").cleaned().description)
    @Test fun `cleaning removes court style for beach modality`() = assertNull(form(modality = GroupModality.BEACH_VOLLEYBALL, playStyle = GroupPlayStyle.FIVE_ONE).cleaned().playStyle)
    @Test fun `cleaning removes obsolete custom level`() = assertNull(form(level = GroupLevel.ADVANCED, customLevel = "old").cleaned().customLevel)
    @Test fun `cleaning preserves active custom labels`() = assertEquals("Elite", form(level = GroupLevel.CUSTOM, customLevel = " Elite ").cleaned().customLevel)
    @Test fun `validation preserves field details`() = assertEquals(listOf("required"), (GroupProfileError.Validation(ValidationDetails(emptyList(), mapOf("name" to listOf("required"))))).details.fieldMessages["name"])
    @Test fun `conflict preserves optional current version`() = assertEquals("new", GroupProfileError.Conflict(GroupVersionToken("new")).currentVersion?.value)
    @Test fun `data failure preserves shared category`() = assertEquals(DataError.Forbidden, GroupProfileError.DataFailure(DataError.Forbidden).error)

    private fun slot() = GroupRegularSlot(null, GroupWeekday.MONDAY, "19:00", 90)
    private fun finance() = GroupFinanceDefaults(2500, 10000, 10)
    private fun profile() = GroupProfile(GroupModality.COURT_VOLLEYBALL, GroupComposition.MIXED, null, null, GroupLevel.MIXED_LEVELS, null, GroupPlayStyle.FIVE_ONE, null, null, listOf(slot()), 12, 360)
    private fun group() = Group(GroupId("group-1"), "Group", GroupTimeZone("UTC"), 1, GroupRole.OWNER, GroupProfileStatus.COMPLETE, GroupPrivacy.PRIVATE, GroupCurrency.BRL, profile(), finance())
    private fun form(description: String? = null, city: String? = null, modality: GroupModality = GroupModality.COURT_VOLLEYBALL, level: GroupLevel? = null, customLevel: String? = null, playStyle: GroupPlayStyle? = null) = GroupSetupForm(" Group ", modality, GroupComposition.MIXED, description, city, level, customLevel, playStyle)
}
