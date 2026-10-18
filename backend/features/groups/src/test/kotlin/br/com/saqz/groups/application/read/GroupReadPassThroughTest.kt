package br.com.saqz.groups.application.read

import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.CourtPlayStyle
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O que o app recebe ao abrir o grupo e ao listar os grupos: cada campo do perfil chega como foi salvo. */
class GroupReadPassThroughTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val venue = GroupVenueReadModel(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2")
    private val slot = GroupRegularSlotReadModel(UUID.randomUUID(), DayOfWeek.WEDNESDAY, LocalTime.of(19, 30), 90)
    private val profile = GroupProfileReadModel(
        modality = GroupModality.COURT_VOLLEYBALL,
        composition = GroupComposition.MIXED,
        description = "Vôlei de quarta",
        city = "São Paulo",
        level = GroupLevel.entries.first(),
        customLevel = "intermediário+",
        playStyle = CourtPlayStyle.entries.first(),
        customPlayStyle = "rápido",
        defaultVenue = venue,
        regularSlots = listOf(slot),
        defaultCapacity = 18,
        defaultConfirmationLeadMinutes = 180,
        pixKey = "chave@pix",
        pixLabel = "Tesouraria",
        defaultDurationMinutes = 90,
    )
    private val finance = GroupFinanceDefaultsReadModel(
        defaultGameFeeCents = 1500, monthlyFeeCents = 7000, monthlyDueDay = 10,
        monthlyLateJoinPolicy = MonthlyLateJoinPolicy.entries.last(),
    )

    private fun open(role: GroupRole, entryRequiresApproval: Boolean = true): GroupView {
        val snapshot = GroupReadSnapshot(
            group, AccessName.from("Vôlei"), IanaTimeZone.from("America/Sao_Paulo"), role, 7,
            GroupProfileStatus.COMPLETE, profile, finance, null, entryRequiresApproval,
        )
        val read = GetGroup(object : GroupReadRepository { override fun find(key: GroupReadKey) = snapshot }, GroupAccessPolicy()).execute(actor, group)
        return (read as GetGroupResult.Success).group
    }

    @Test fun `cada campo do perfil chega ao app como foi salvo`() {
        val saved = open(GroupRole.ADMIN).profile!!
        assertEquals(GroupModality.COURT_VOLLEYBALL, saved.modality)
        assertEquals(GroupComposition.MIXED, saved.composition)
        assertEquals("Vôlei de quarta", saved.description)
        assertEquals("São Paulo", saved.city)
        assertEquals(GroupLevel.entries.first(), saved.level)
        assertEquals("intermediário+", saved.customLevel)
        assertEquals(CourtPlayStyle.entries.first(), saved.playStyle)
        assertEquals("rápido", saved.customPlayStyle)
        assertEquals(18, saved.defaultCapacity)
        assertEquals(180, saved.defaultConfirmationLeadMinutes)
        assertEquals("chave@pix", saved.pixKey)
        assertEquals("Tesouraria", saved.pixLabel)
        assertEquals(90, saved.defaultDurationMinutes)
    }

    @Test fun `quadra padrão e horários regulares chegam inteiros`() {
        val saved = open(GroupRole.ATHLETE).profile!!
        assertEquals(venue.id, saved.defaultVenue?.id)
        assertEquals("Arena Central", saved.defaultVenue?.name)
        assertEquals("Rua das Flores 100", saved.defaultVenue?.address)
        assertEquals("Quadra 2", saved.defaultVenue?.court)
        val regular = saved.regularSlots.single()
        assertEquals(slot.id, regular.id)
        assertEquals(DayOfWeek.WEDNESDAY, regular.weekday)
        assertEquals(LocalTime.of(19, 30), regular.startTime)
        assertEquals(90, regular.durationMinutes)
    }

    @Test fun `padrões financeiros chegam ao dono e ao admin com a regra de mensalista que entra tarde`() {
        listOf(GroupRole.OWNER, GroupRole.ADMIN).forEach {
            val defaults = open(it).financeDefaults!!
            assertEquals(1500L, defaults.defaultGameFeeCents)
            assertEquals(7000L, defaults.monthlyFeeCents)
            assertEquals(10, defaults.monthlyDueDay)
            assertEquals(MonthlyLateJoinPolicy.entries.last(), defaults.monthlyLateJoinPolicy)
        }
        assertNull(open(GroupRole.ATHLETE).financeDefaults)
    }

    @Test fun `o pedido de aprovação para entrar e o nome, fuso e versão acompanham o grupo`() {
        val view = open(GroupRole.OWNER, entryRequiresApproval = true)
        assertTrue(view.entryRequiresApproval)
        assertEquals(false, open(GroupRole.OWNER, entryRequiresApproval = false).entryRequiresApproval)
        assertEquals(AccessName.from("Vôlei"), view.name)
        assertEquals(IanaTimeZone.from("America/Sao_Paulo"), view.timeZone)
        assertEquals(7L, view.version)
        assertEquals(group, view.id)
    }

    @Test fun `listar grupos devolve o que o repositório tem para aquela pessoa, na ordem dele`() {
        val mine = listOf(
            GroupSummaryReadModel(group, AccessName.from("Vôlei"), IanaTimeZone.from("UTC"), GroupRole.OWNER, GroupProfileStatus.COMPLETE, GroupModality.COURT_VOLLEYBALL, "São Paulo", 12),
            GroupSummaryReadModel(UUID.randomUUID(), AccessName.from("Areia"), IanaTimeZone.from("UTC"), GroupRole.ATHLETE, GroupProfileStatus.INCOMPLETE, null, null, 3),
        )
        val asked = mutableListOf<UUID>()
        val list = ListGroups(object : GroupSummariesReadRepository { override fun findAllFor(actorUserId: UUID): List<GroupSummaryReadModel> { asked += actorUserId; return mine } })
        val listed = list.execute(actor)
        assertEquals(mine, listed)
        assertEquals(listOf(actor), asked)
        assertEquals(12, listed.first().memberCount)
        assertEquals("São Paulo", listed.first().city)
        assertEquals(GroupModality.COURT_VOLLEYBALL, listed.first().modality)
        assertEquals(GroupProfileStatus.INCOMPLETE, listed.last().profileStatus)
        assertEquals(GroupRole.ATHLETE, listed.last().role)
        assertEquals(AccessName.from("Areia"), listed.last().name)
        assertEquals(IanaTimeZone.from("UTC"), listed.last().timeZone)
        assertEquals(mine.last().id, listed.last().id)
    }
}
