package br.com.saqz.composeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.access.ui.AccessBrandMark
import br.com.saqz.access.ui.AccessWave
import br.com.saqz.designsystem.theme.SaqzTheme

internal const val SPLASH_TEST_TAG = "saqz-splash"

/**
 * A abertura do app: a **única** tela de carregamento de tela cheia do Saqz.
 *
 * Ela existe para que a primeira tela que a pessoa vê já seja a certa. Sem ela o app
 * compunha o destino a partir de `SignedOut` — o estado inicial da máquina, e não uma
 * resposta —, então quem abria já autenticado via o login piscar antes da home.
 *
 * Sem spinner de propósito: a espera é curta e o guia de carregamento pede que a abertura
 * seja a marca, não um indicador. O que sustenta a espera é a própria marca centralizada.
 */
@Composable
internal fun SaqzSplash(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.surface)
            .testTag(SPLASH_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        AccessWave(Modifier.align(Alignment.BottomCenter))
        AccessBrandMark(large = true)
    }
}
