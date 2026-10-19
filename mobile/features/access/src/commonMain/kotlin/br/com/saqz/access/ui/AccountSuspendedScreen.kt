package br.com.saqz.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.account_suspended_contact
import br.com.saqz.access.resources.account_suspended_headline
import br.com.saqz.access.resources.account_suspended_headline_emphasis
import br.com.saqz.access.resources.account_suspended_logout
import br.com.saqz.access.resources.account_suspended_supporting_text
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

/** E-mail de contato da equipe e o mailto que a tela abre (mesmo endereço do "Ajuda" do perfil). */
const val AccountSuspendedMailto = "mailto:contato@egysis.com?subject=Saqz%20-%20Conta%20suspensa"

internal object AccountSuspendedTags {
    const val Contact = "account-suspended-contact"
    const val Logout = "account-suspended-logout"
}

/**
 * DEB-085: tela de quem está com a conta suspensa. Neutra, sem motivo: o texto manda escrever para a equipe, e há
 * "Sair" para trocar de conta. **Sem ViewModel**: não há estado a guardar, só dois botões.
 */
@Composable
fun AccountSuspendedScreen(
    onContact: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.surface)) {
        AccessWave(Modifier.align(Alignment.BottomCenter))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = AccessMetrics.horizontalPadding)
                .padding(top = AccessMetrics.topPadding)
                .testTag(AccessChromeTags.Content),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AccessHeader(
                title = stringResource(Res.string.account_suspended_headline),
                emphasis = stringResource(Res.string.account_suspended_headline_emphasis),
                subtitle = stringResource(Res.string.account_suspended_supporting_text),
            )
            Spacer(Modifier.height(32.dp))
            SaqzButton(
                label = stringResource(Res.string.account_suspended_contact),
                onClick = onContact,
                fullWidth = true,
                modifier = Modifier.testTag(AccountSuspendedTags.Contact),
            )
            Spacer(Modifier.height(12.dp))
            SaqzButton(
                label = stringResource(Res.string.account_suspended_logout),
                onClick = onLogout,
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
                modifier = Modifier.testTag(AccountSuspendedTags.Logout),
            )
        }
    }
}

@Preview(name = "Conta suspensa", widthDp = 390, heightDp = 844)
@Composable
private fun AccountSuspendedPreview() = SaqzTheme {
    AccountSuspendedScreen(onContact = {}, onLogout = {})
}
