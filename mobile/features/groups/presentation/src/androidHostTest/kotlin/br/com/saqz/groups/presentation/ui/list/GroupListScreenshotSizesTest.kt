package br.com.saqz.groups.presentation.ui.list

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.SaqzBottomNav
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.list.GroupListState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A lista e a folha no celular de 360 e com a fonte do sistema grande (GER-003): nada cortado. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupListScreenshotSizesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun narrow() = compose.captureGroupList("group-list-2n-360", GroupListSamples.filled)

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun narrowStates() = compose.captureGroupList("group-list-2n-estados-360", GroupListSamples.states)

    @Test
    fun largeFont() = compose.captureGroupList("group-list-2n-fonte-grande", GroupListSamples.filled, fontScale = LARGE_FONT)

    @Test
    fun largeFontSheet() = compose.captureGroupList("group-list-2n-folha-fonte-grande", GroupListSamples.confirming, fontScale = LARGE_FONT)

    private companion object {
        const val LARGE_FONT = 1.6f
    }
}
