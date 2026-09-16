package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.saqz_mark
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.painterResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * VUL-217: as peças novas do design system da Home nova — as variantes `Accent`/`Inverse`
 * do botão em todos os estados, o chip `Inverse`, o `SaqzHeroCard` cheio e mínimo, e a
 * escala `display` com a marca.
 *
 * Estado que não está na cena não está sendo conferido (`mobile/AGENTS.md`), então os
 * botões novos aparecem habilitados, desabilitados e carregando, e sobre o azul do hero —
 * que é o único fundo em que eles existem.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class HomeHeroDesignSystemScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun accentAndInverseButtons() = capture("botoes-accent-inverse") {
        OverBlue {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaqzButton("Vou", onClick = {}, variant = SaqzButtonVariant.Accent, modifier = Modifier.weight(1f))
                    SaqzButton(
                        "Não vou",
                        onClick = {},
                        variant = SaqzButtonVariant.Inverse,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaqzButton(
                        "Vou",
                        onClick = {},
                        variant = SaqzButtonVariant.Accent,
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    )
                    SaqzButton(
                        "Não vou",
                        onClick = {},
                        variant = SaqzButtonVariant.Inverse,
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaqzButton(
                        "Confirmando",
                        onClick = {},
                        variant = SaqzButtonVariant.Accent,
                        loading = true,
                        modifier = Modifier.weight(1f),
                    )
                    SaqzButton(
                        "Saindo",
                        onClick = {},
                        variant = SaqzButtonVariant.Inverse,
                        loading = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaqzButton(
                        "Não vou",
                        onClick = {},
                        variant = SaqzButtonVariant.Ghost,
                        contentColor = SaqzTheme.colors.onPrimary,
                        borderColor = SaqzTheme.colors.onPrimary.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f),
                    )
                    SaqzButton(
                        "Cancelar",
                        onClick = {},
                        variant = SaqzButtonVariant.Ghost,
                        contentColor = SaqzTheme.colors.onPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    @Test
    fun inverseChips() = capture("chips-inverse") {
        OverBlue {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaqzStatusChip("Vôlei do CERET", tone = SaqzChipTone.Inverse)
                SaqzStatusChip("Lista de espera · 1º", tone = SaqzChipTone.Inverse, dot = true)
            }
        }
    }

    @Test
    fun heroCard() = capture("hero-card") {
        SaqzHeroCard(
            kicker = "PRÓXIMO JOGO",
            title = "Terça, 19h30",
            meta = "28 de julho · CERET — Quadra 2 · Tatuapé",
            trailing = { SaqzStatusChip("Vôlei do CERET", tone = SaqzChipTone.Inverse) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaqzButton("Vou", onClick = {}, variant = SaqzButtonVariant.Accent, modifier = Modifier.weight(1f))
                SaqzButton(
                    "Não vou",
                    onClick = {},
                    variant = SaqzButtonVariant.Ghost,
                    contentColor = SaqzTheme.colors.onPrimary,
                    borderColor = SaqzTheme.colors.onPrimary.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f),
                )
            }
            SaqzAvatarStack(
                names = listOf("Ana Souza", "Bruna Lima", "Caio", "Duda"),
                ring = SaqzTheme.colors.primary,
                overflowContainer = SaqzTheme.colors.accent,
                overflowContent = SaqzTheme.colors.textPrimary,
            )
        }
    }

    @Test
    fun heroCardMinimum() = capture("hero-card-minimo") {
        SaqzHeroCard(kicker = "PRÓXIMO JOGO", title = "Sem jogo marcado")
    }

    @Test
    fun displayScaleAndMark() = capture("display-e-marca") {
        Text("Terça, 19h30", style = SaqzTheme.typography.display)
        Image(
            painter = painterResource(Res.drawable.saqz_mark),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
    }

    // O azul do hero é o único fundo em que Accent, Inverse e o chip invertido existem:
    // fotografá-los sobre o canvas claro mostraria um contraste que o app nunca desenha.
    @Composable
    private fun OverBlue(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SaqzTheme.colors.primary, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) { content() }
    }

    private fun capture(name: String, content: @Composable ColumnScope.() -> Unit) {
        compose.setContent {
            SaqzTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-217/$name.png")
    }
}
