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

// A casca do detalhe (C2): barra, skeleton, quadra, mural, galera/gestão e sair, um estado
// por cena. Estado fora da cena é estado não conferido (AGENTS.md §11).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
@Suppress("TooManyFunctions")
class GroupShellScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun member() = compose.captureDetails("group-shell-member", GroupShellPreviewData.member, "shell")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun memberNoGame() = compose.captureDetails("group-shell-member-no-game", GroupShellPreviewData.memberNoGame, "shell")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun memberNoGameMapFailed() =
        compose.captureDetails("group-shell-member-map-failed", GroupShellPreviewData.memberNoGameMapFailed, "shell")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun memberBare() = compose.captureDetails("group-shell-member-bare", GroupShellPreviewData.memberBare, "shell")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun memberLongName() = compose.captureDetails("group-shell-member-long-name", GroupShellPreviewData.memberLongName, "shell")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun manager() = compose.captureDetails("group-shell-manager", GroupShellPreviewData.manager, "shell")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun managerNoCashSummary() =
        compose.captureDetails("group-shell-manager-no-cash-summary", GroupShellPreviewData.managerNoCashSummary, "shell")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun managerNewGroup() =
        compose.captureDetails("group-shell-manager-new-group", GroupShellPreviewData.managerNewGroup, "shell", photoFailed = true)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun managerNotOwner() =
        compose.captureDetails("group-shell-manager-not-owner", GroupShellPreviewData.managerNotOwner, "shell")

    @Test
    fun loading() = compose.captureDetails("group-shell-loading", GroupShellPreviewData.loading, "shell")

    @Test
    fun loadFailed() = compose.captureDetails("group-shell-load-failed", GroupShellPreviewData.loadFailed, "shell")
}
