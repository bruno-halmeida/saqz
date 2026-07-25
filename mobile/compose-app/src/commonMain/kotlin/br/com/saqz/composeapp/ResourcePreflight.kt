package br.com.saqz.composeapp

import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_brand
import br.com.saqz.access.resources.saqz_lettering
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

// ponytail: resource holder only — proves :features:access resources resolve
// transitively through the umbrella. Not wired into any production UI; aponta para
// os assets de marca reais desde que o sentinela sintético morreu com o design
// system (VUL-36).
internal object ResourcePreflight {
    val sentinelString: StringResource = Res.string.access_brand
    val sentinelDrawable: DrawableResource = Res.drawable.saqz_lettering
}
