package br.com.saqz.profile.presentation.exit

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class ProfileExitScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sheet() = capture("perfil-sair-folha") {
        ProfileExitScreen(
            state = ProfileExitState(email = "rafael@email.com"),
            onIntent = {},
            onClose = {},
            onLogout = {},
        )
    }

    @Test
    fun confirmation() = capture("perfil-sair-confirmacao") {
        ProfileExitScreen(
            state = ProfileExitState(
                email = "rafael@email.com",
                sheet = ProfileExitSheet.ConfirmDelete,
            ),
            onIntent = {},
            onClose = {},
            onLogout = {},
        )
    }

    @Test
    fun error() = capture("perfil-sair-erro") {
        ProfileExitScreen(
            state = ProfileExitState(
                email = "rafael@email.com",
                sheet = ProfileExitSheet.ConfirmDelete,
                confirmationEmail = "rafael@email.com",
                error = ProfileExitError.DeleteFailed,
            ),
            onIntent = {},
            onClose = {},
            onLogout = {},
        )
    }

    @Test
    fun loading() = capture("perfil-sair-carregando") {
        ProfileExitScreen(
            state = ProfileExitState(
                email = "rafael@email.com",
                sheet = ProfileExitSheet.ConfirmDelete,
                confirmationEmail = "rafael@email.com",
                isDeleting = true,
            ),
            onIntent = {},
            onClose = {},
            onLogout = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background),
                ) {
                    ProfileBackdrop()
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-131/$name.png")
    }
}

@Composable
private fun ProfileBackdrop() {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.sectionGap),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
    ) {
        Text(
            text = "Perfil",
            style = SaqzTheme.typography.headline,
            color = colors.textPrimary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(metrics.cardRadius))
                .background(colors.surface)
                .padding(metrics.sectionGap),
            verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            Text(
                text = "Rafael Costa",
                style = SaqzTheme.typography.title,
                color = colors.textPrimary,
            )
            Text(
                text = "Rafa · São Paulo, SP",
                style = SaqzTheme.typography.body,
                color = colors.textSecondary,
            )
            Text(
                text = "42 Jogos   89% Presença   3 Grupos",
                style = SaqzTheme.typography.label,
                color = colors.primary,
            )
        }
        Text(
            text = "Como você joga",
            style = SaqzTheme.typography.title,
            color = colors.textPrimary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(metrics.cardRadius))
                .background(colors.surface)
                .padding(metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            Text("Vôlei do CERET", style = SaqzTheme.typography.label, color = colors.textPrimary)
            Text("Ponteiro · intermediário · Mensalista", style = SaqzTheme.typography.support, color = colors.textSecondary)
            Text("Areia do Ibira", style = SaqzTheme.typography.label, color = colors.textPrimary)
            Text("Joga na direita · intermediário · Avulso", style = SaqzTheme.typography.support, color = colors.textSecondary)
        }
    }
}
