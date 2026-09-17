package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// VUL-203 — os quatro estados da seção de cobranças: com pendência (e Pix), quitada,
// carregando e com falha. Estado fora da cena é estado não conferido (AGENTS.md §11).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupOwnDebtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun ownCharges() = compose.captureDetails("group-details-own-charges", GroupDetailsPreviewData.member, "own-debt")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun ownChargesSettled() =
        compose.captureDetails("group-details-own-charges-settled", GroupDetailsPreviewData.memberOwnChargesSettled, "own-debt")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun ownChargesLoading() =
        compose.captureDetails("group-details-own-charges-loading", GroupDetailsPreviewData.memberOwnChargesLoading, "own-debt")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun ownChargesFailed() =
        compose.captureDetails("group-details-own-charges-failed", GroupDetailsPreviewData.memberOwnChargesFailed, "own-debt")
}
