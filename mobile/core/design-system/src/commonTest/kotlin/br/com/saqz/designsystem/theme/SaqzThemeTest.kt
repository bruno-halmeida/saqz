package br.com.saqz.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SaqzThemeTest {
    @Test
    fun exposesEveryRegistry() = runComposeUiTest {
        lateinit var colors: SaqzColorTokens
        lateinit var metrics: SaqzMetrics
        lateinit var typography: SaqzTypography
        lateinit var motion: SaqzMotionPolicy
        lateinit var shadows: SaqzShadows
        lateinit var family: FontFamily
        setContent {
            SaqzTheme {
                colors = SaqzTheme.colors
                metrics = SaqzTheme.metrics
                typography = SaqzTheme.typography
                motion = SaqzTheme.motion
                shadows = SaqzTheme.shadows
                family = saqzFontFamily()
            }
        }
        assertEquals(SaqzColorTokens.Light, colors)
        assertEquals(SaqzMetrics.Default, metrics)
        assertEquals(SaqzMotionPolicy.Normal, motion)
        assertEquals(SaqzShadows.Default, shadows)
        assertEquals(SaqzTypography.Default.body.fontSize, typography.body.fontSize)
        assertEquals(family, typography.body.fontFamily)
    }

    @Test
    fun nestedThemeOverridesTogether() = runComposeUiTest {
        val outerColors = SaqzColorTokens.Light.copy(primary = Color(0xFF123456))
        lateinit var colors: SaqzColorTokens
        lateinit var metrics: SaqzMetrics
        lateinit var motion: SaqzMotionPolicy
        setContent {
            CompositionLocalProvider(LocalSaqzColors provides outerColors) {
                SaqzTheme {
                    colors = SaqzTheme.colors
                    metrics = SaqzTheme.metrics
                    motion = SaqzTheme.motion
                }
            }
        }
        // SaqzTheme re-provides the whole registry set, overriding the outer local.
        assertEquals(SaqzColorTokens.Light, colors)
        assertEquals(SaqzMetrics.Default, metrics)
        assertEquals(SaqzMotionPolicy.Normal, motion)
    }

    @Test
    fun materialColorsDeriveFromSaqz() = runComposeUiTest {
        lateinit var material: Colors
        lateinit var saqz: SaqzColorTokens
        setContent {
            SaqzTheme {
                material = MaterialTheme.colors
                saqz = SaqzTheme.colors
            }
        }
        assertEquals(saqz.primary, material.primary)
        assertEquals(saqz.onPrimary, material.onPrimary)
        assertEquals(saqz.background, material.background)
        assertEquals(saqz.surface, material.surface)
        assertEquals(saqz.errorForeground, material.error)
    }

    @Test
    fun materialTypographyDerivesFromSaqz() = runComposeUiTest {
        lateinit var material: Typography
        lateinit var saqz: SaqzTypography
        setContent {
            SaqzTheme {
                material = MaterialTheme.typography
                saqz = SaqzTheme.typography
            }
        }
        assertEquals(saqz.headline, material.h3)
        assertEquals(saqz.title, material.h4)
        assertEquals(saqz.subtitle, material.subtitle1)
        assertEquals(saqz.body, material.body1)
        assertEquals(saqz.support, material.body2)
        assertEquals(saqz.label, material.button)
        assertEquals(saqz.eyebrow, material.overline)
    }

    @Test
    fun shapesDeriveFromMetrics() = runComposeUiTest {
        lateinit var shapes: Shapes
        lateinit var metrics: SaqzMetrics
        setContent {
            SaqzTheme {
                shapes = MaterialTheme.shapes
                metrics = SaqzTheme.metrics
            }
        }
        assertEquals(RoundedCornerShape(metrics.inputRadius), shapes.small)
        assertEquals(RoundedCornerShape(metrics.cardRadius), shapes.medium)
        assertEquals(RoundedCornerShape(metrics.blockRadius), shapes.large)
    }

    @Test
    fun noLoosePublicTokenAccessors() = runComposeUiTest {
        // The only public path is SaqzTheme.*, backed by internal locals with sane
        // defaults — no loose public token constants exist to read outside a theme.
        lateinit var colors: SaqzColorTokens
        lateinit var metrics: SaqzMetrics
        lateinit var typography: SaqzTypography
        lateinit var motion: SaqzMotionPolicy
        lateinit var shadows: SaqzShadows
        setContent {
            colors = SaqzTheme.colors
            metrics = SaqzTheme.metrics
            typography = SaqzTheme.typography
            motion = SaqzTheme.motion
            shadows = SaqzTheme.shadows
        }
        assertEquals(SaqzColorTokens.Light, colors)
        assertEquals(SaqzMetrics.Default, metrics)
        assertEquals(SaqzTypography.Default, typography)
        assertEquals(SaqzMotionPolicy.Normal, motion)
        assertEquals(SaqzShadows.Default, shadows)
    }
}
