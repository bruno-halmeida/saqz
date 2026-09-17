package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import br.com.saqz.groups.presentation.details.GroupDetailsState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A tela inteira nos estados-base. Os estados de cada bloco moram na suíte do bloco. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupDetailsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // A tela rola: a altura sobe só na captura, para o print do PR mostrar a pilha inteira.
    @Test
    @Config(qualifiers = "+h2400dp")
    fun admin() = compose.captureDetails("group-details-admin", GroupDetailsPreviewData.admin, "details")

    @Test
    @Config(qualifiers = "+h1400dp")
    fun adminNoGame() = compose.captureDetails("group-details-admin-no-game", GroupDetailsPreviewData.adminNoGame, "details")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun member() = compose.captureDetails("group-details-member", GroupDetailsPreviewData.member, "details")

    @Test
    @Config(qualifiers = "+h1800dp")
    fun memberNoGame() = compose.captureDetails("group-details-member-no-game", GroupDetailsPreviewData.memberNoGame, "details")

    @Test
    fun loading() = compose.captureDetails("group-details-loading", GroupDetailsState(), "details")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun photoFailed() =
        compose.captureDetails("group-details-photo-failed", GroupDetailsPreviewData.adminNoGame, "details", photoFailed = true)
}
