package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.access.presentation.login.LoginState
import br.com.saqz.access.ui.LoginScreen
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.athlete.AthleteFinancialStatus
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.finance.Expense
import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.domain.finance.FinanceTotals
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.presentation.GroupActions
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionMembership
import br.com.saqz.groups.presentation.athlete.AthleteRosterState
import br.com.saqz.groups.presentation.finance.expenses.ExpenseState
import br.com.saqz.groups.presentation.games.list.GameListItem
import br.com.saqz.groups.presentation.games.list.GamesState
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationAccess
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.ui.athlete.AthleteRosterScreen
import br.com.saqz.groups.ui.finance.expenses.ExpenseScreen
import br.com.saqz.groups.ui.games.list.GamesScreen
import br.com.saqz.groups.ui.games.list.GamesScreenState
import br.com.saqz.groups.ui.group.GroupDetailScreen
import br.com.saqz.groups.ui.group.GroupsListScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots de tela em JVM (Roborazzi + Robolectric) — sem emulador.
 *
 * Gravar:   ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:    android-app/screenshots/
 *
 * ponytail: sem verify/CI por enquanto — só o loop visual. Gate de regressão
 * visual entra quando os goldens estabilizarem.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Application puro: o SaqzApplication real inicia Koin/Firebase, que são estáticos
// na JVM e quebram a partir do segundo teste. Screenshot não precisa de DI.
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class SaqzScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { SaqzTheme { content() } }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun login() = capture("login") {
        LoginScreen(state = LoginState(email = "ana@saqz.app"), onIntent = {})
    }

    @Test
    fun gruposLista() = capture("grupos_lista") {
        GroupsListScreen(
            memberships = listOf(
                GroupSelectionMembership("volei-terca", "Vôlei de Terça", GroupRole.OWNER),
                GroupSelectionMembership("praia-sabado", "Praia de Sábado", GroupRole.ATHLETE),
            ),
            onSelectGroup = {},
            onOpenCreateGroup = {},
        )
    }

    @Test
    fun grupoDetalhe() = capture("grupo_detalhe") {
        GroupDetailScreen(
            group = grupo,
            administration = GroupAdministrationState(
                group = VersionedGroup(grupo, GroupVersionToken("etag")),
                memberships = listOf(
                    GroupMembership(userId = "user-1", displayName = "Ana Lima", role = GroupRole.ATHLETE),
                    GroupMembership(userId = "user-2", displayName = "Bruno Reis", role = GroupRole.ADMIN),
                ),
                actions = GroupActions(
                    canEditSettings = true,
                    canManageRoles = true,
                    canManageInvite = true,
                    canManageAthletes = true,
                ),
            ),
            access = GroupsNavigationAccess(
                showPeople = true,
                showGames = true,
                showFinance = true,
                canMutateOperations = true,
                financeDestination = GroupsDestination.FINANCE,
            ),
            groupPhotoState = GroupPhotoState(),
            groupPhotoPreview = null,
            onNavigationIntent = {},
            onOpenSettings = {},
            onOpenInvite = {},
            onRequestLogout = {},
        )
    }

    @Test
    fun jogosLista() = capture("jogos_lista") {
        GamesScreenState(
            games = GamesState(
                groupId = "volei-terca",
                role = GroupRole.OWNER,
                upcoming = listOf(
                    GameListItem(
                        id = "game-1",
                        title = "Vôlei de Terça",
                        dateText = "ter, 28 de jul",
                        timeText = "19:30 – 21:00",
                        venueText = "Quadra do Parque",
                        status = GameStatus.Published,
                        availableSpots = 3,
                        waitlistCount = 0,
                        startsAt = "2026-07-28T19:30",
                    ),
                    GameListItem(
                        id = "game-2",
                        title = "Amistoso de Quinta",
                        dateText = "qui, 30 de jul",
                        timeText = "20:00 – 22:00",
                        venueText = "Arena Beira-Mar",
                        status = GameStatus.Draft,
                        availableSpots = 12,
                        waitlistCount = 2,
                        startsAt = "2026-07-30T20:00",
                    ),
                ),
            ),
        ).let { GamesScreen(state = it, onIntent = {}) }
    }

    @Test
    fun despesas() = capture("despesas") {
        ExpenseScreen(
            state = ExpenseState(
                groupId = "volei-terca",
                role = GroupRole.OWNER,
                expenses = listOf(
                    Expense(
                        id = "exp-1",
                        groupId = GroupId("volei-terca"),
                        description = "Aluguel da quadra — julho",
                        amountCents = 48_000,
                        expenseDate = "2026-07-01",
                        category = ExpenseCategory.Venue,
                        status = ExpenseStatus.Active,
                        version = 1,
                        audit = emptyList(),
                    ),
                    Expense(
                        id = "exp-2",
                        groupId = GroupId("volei-terca"),
                        description = "Bola Mikasa V200W",
                        amountCents = 39_990,
                        expenseDate = "2026-07-10",
                        category = ExpenseCategory.Equipment,
                        status = ExpenseStatus.Active,
                        version = 1,
                        audit = emptyList(),
                    ),
                ),
                totals = FinanceTotals(
                    pendingChargeCents = 24_000,
                    paidChargeCents = 96_000,
                    waivedChargeCents = 0,
                    cancelledChargeCents = 0,
                    activeExpenseCents = 87_990,
                ),
                isLoading = false,
            ),
            onIntent = {},
        )
    }

    @Test
    fun atletas() = capture("atletas") {
        AthleteRosterScreen(
            state = AthleteRosterState(
                groupId = "volei-terca",
                athletes = listOf(
                    AthleteRosterEntry(
                        userId = "user-1",
                        displayName = "Ana Lima",
                        phone = "+55 11 91234-5678",
                        position = AthletePosition.LEVANTADOR,
                        membershipType = AthleteMembershipType.MENSALISTA,
                        active = true,
                        financialStatus = AthleteFinancialStatus.EM_DIA,
                    ),
                    AthleteRosterEntry(
                        userId = "user-2",
                        displayName = "Bruno Reis",
                        phone = null,
                        position = AthletePosition.CENTRAL,
                        membershipType = AthleteMembershipType.AVULSO,
                        active = true,
                        financialStatus = AthleteFinancialStatus.PENDENTE,
                    ),
                    AthleteRosterEntry(
                        userId = "user-3",
                        displayName = "Carla Souza",
                        phone = "+55 21 99876-1234",
                        position = AthletePosition.LIBERO,
                        membershipType = AthleteMembershipType.MENSALISTA,
                        active = false,
                        financialStatus = AthleteFinancialStatus.DESCONHECIDO,
                    ),
                ),
            ),
            canManage = true,
            onIntent = {},
        )
    }

    private companion object {
        val grupo = Group(
            id = GroupId("volei-terca"),
            name = "Vôlei de Terça",
            timeZone = GroupTimeZone("America/Sao_Paulo"),
            version = 1,
            role = GroupRole.OWNER,
            profile = GroupProfile(
                modality = null,
                composition = null,
                description = null,
                city = null,
                level = null,
                customLevel = null,
                playStyle = null,
                customPlayStyle = null,
                defaultVenue = GroupVenue(id = "venue-1", name = "Quadra do Parque", address = "Rua das Flores, 10"),
                regularSlots = listOf(
                    GroupRegularSlot(
                        id = "slot-1",
                        weekday = GroupWeekday.TUESDAY,
                        startTime = "19:30",
                        durationMinutes = 90,
                    ),
                ),
                defaultCapacity = null,
                defaultConfirmationLeadMinutes = null,
            ),
        )
    }
}
