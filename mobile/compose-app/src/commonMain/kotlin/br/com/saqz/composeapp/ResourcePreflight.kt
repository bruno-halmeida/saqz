package br.com.saqz.composeapp

import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.preflight_sentinel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

// ponytail: resource holder only — proves :core:design-system resources resolve
// transitively through the umbrella. Not wired into any production UI.
internal object ResourcePreflight {
    val sentinelString: StringResource = Res.string.preflight_sentinel
    val sentinelDrawable: DrawableResource = Res.drawable.preflight_sentinel
}
