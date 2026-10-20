package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.presentation.details.AgendaMissing
import br.com.saqz.groups.presentation.details.GroupAgendaRowUi
import br.com.saqz.groups.presentation.details.GroupAgendaStatus
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupPositionCourt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DEB-109, quadros `Grupo` e `GrupoCompleto`: o cartão do próximo jogo de quem administra e a linha "Faltam" da agenda. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h1600dp-xxhdpi", application = Application::class)
class GroupPositionsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val agenda = listOf(
        GroupAgendaRowUi(
            "g2", "30", "JUL", "Quinta · 19h30", "6 de 12 confirmados", GroupAgendaStatus.Pending, "Sem resposta", "",
            missing = AgendaMissing.Some(listOf(AthletePosition.CENTRAL, AthletePosition.OPOSTO, AthletePosition.LEVANTADOR)),
        ),
        GroupAgendaRowUi(
            "g3", "04", "AGO", "Terça · 19h30", "3 de 12 · Arena Mooca", GroupAgendaStatus.Going, "Você vai", "",
            missing = AgendaMissing.All,
        ),
    )

    private fun state(complete: Boolean = false, libero: Int = 2): GroupDetailsState {
        val base = GroupHeroPreviewData.adminPending
        val court = GroupPositionCourt.from(GroupDetailsPreviewData.courtPositions(complete = complete, libero = libero))
        return base.copy(attendance = base.attendance?.copy(court = court, waiting = 1, notGoing = 1), agenda = agenda)
    }

    @Test fun faltam() = compose.captureDetails("grupo-faltam", state(), "deb-109")
    @Test fun completo() = compose.captureDetails("grupo-completo", state(complete = true), "deb-109")
    @Test fun semLibero() = compose.captureDetails("grupo-sem-libero", state(libero = 0), "deb-109")
}
