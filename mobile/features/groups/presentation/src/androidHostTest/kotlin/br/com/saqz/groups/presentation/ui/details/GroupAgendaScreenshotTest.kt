package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// "Próximos jogos": os cinco tons do chip, o "Ver mais" fechado e aberto e a ação do gestor.
// Estado fora da cena é estado não conferido (AGENTS.md §11).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupAgendaScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun member() = compose.captureDetails("group-details-agenda-member", GroupAgendaPreviewData.member, "agenda")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun memberMoreThenExpanded() {
        compose.captureDetails("group-details-agenda-more", GroupAgendaPreviewData.memberMore, "agenda")
        compose.onNodeWithTag(GroupDetailsTags.AgendaMore).performClick()
        compose.onRoot().captureRoboImage("screenshots/agenda/group-details-agenda-expanded.png")
    }

    @Test
    @Config(qualifiers = "+h2400dp")
    fun adminDraft() = compose.captureDetails("group-details-agenda-admin-draft", GroupAgendaPreviewData.adminDraft, "agenda")
}
