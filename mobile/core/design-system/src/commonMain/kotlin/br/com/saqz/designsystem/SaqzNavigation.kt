package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.action_back
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

/**
 * 10p — logo na home, título no detalhe. O logo vem do arquivo oficial: o design
 * system recebe o desenho pronto em [logo] e nunca redesenha nem redigita a marca.
 */
@Composable
fun SaqzTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    logo: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 56.dp)
            .padding(horizontal = metrics.subGrid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        if (onBack != null) {
            // `.saqz-topbar__back` sobrescreve o navy do `.saqz-topbar` e do `.saqz-iconbtn`
            // com `color:var(--saqz-blue)`: o azul é o que separa o voltar do título.
            SaqzIconButton(onClick = onBack, contentDescription = stringResource(Res.string.action_back)) {
                SaqzIcon(SaqzIcons.ChevronLeft, tint = colors.primary)
            }
        }
        Box(modifier = Modifier.weight(1f).padding(horizontal = metrics.subGrid)) {
            when {
                logo != null -> logo()
                title != null -> Text(
                    text = title,
                    style = SaqzTheme.typography.subtitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

@Immutable
data class SaqzNavItem(val id: String, val label: String, val icon: ImageVector)

/**
 * 10q — fixo, 76dp mais a safe area, branco a 96%. O indicador lime é o único uso
 * de lime na navegação.
 *
 * ponytail: sem o blur de 18px do mock — `Modifier.blur` borra o próprio conteúdo,
 * não o que passa atrás, e backdrop blur real só existe do Android 31 pra cima.
 * Branco a 96% entrega a mesma leitura; o blur volta com uma lib se alguém pedir.
 * Sob Reduce Transparency o token `chrome` já chega opaco.
 */
@Composable
fun SaqzBottomNav(
    items: List<SaqzNavItem>,
    activeId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.chrome)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.bottomNavHeight)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavTab(
                    item = item,
                    selected = item.id == activeId,
                    onSelect = { onSelect(item.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    item: SaqzNavItem,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        SaqzIcon(
            icon = item.icon,
            tint = if (selected) colors.primary else colors.textSecondary,
            size = 24.dp,
        )
        Text(
            text = item.label,
            style = SaqzTheme.typography.navigation,
            color = if (selected) colors.primary else colors.textSecondary,
            maxLines = 1,
        )
        // Último filho no `.saqz-bottomnav__item`: o lime sublinha o rótulo, não encima o
        // ícone. Reserva os 3dp mesmo inativo para a aba não pular ao ser selecionada.
        Box(
            Modifier
                .width(18.dp)
                .height(3.dp)
                .background(if (selected) colors.accent else Color.Transparent, CircleShape),
        )
    }
}

@Preview
@Composable
private fun SaqzTopAppBarPreview() = SaqzTheme {
    Column {
        SaqzTopAppBar(
            title = "Configurações do grupo",
            onBack = {},
            actions = {
                SaqzIconButton(onClick = {}, contentDescription = "Notificações", dot = true) {
                    SaqzIcon(SaqzIcons.Bell)
                }
            },
        )
        SaqzTopAppBar(
            logo = {
                Text("saqz", style = SaqzTheme.typography.title, color = SaqzTheme.colors.primary)
            },
            actions = {
                SaqzIconButton(onClick = {}, contentDescription = "Buscar") { SaqzIcon(SaqzIcons.Search) }
            },
        )
    }
}

@Preview
@Composable
private fun SaqzBottomNavPreview() = SaqzTheme {
    Box(Modifier.fillMaxWidth().background(SaqzTheme.colors.background).padding(top = 24.dp)) {
        SaqzBottomNav(
            items = listOf(
                SaqzNavItem("home", "Início", SaqzIcons.Home),
                SaqzNavItem("games", "Jogos", SaqzIcons.Calendar),
                SaqzNavItem("people", "Galera", SaqzIcons.Users),
                SaqzNavItem("me", "Perfil", SaqzIcons.User),
            ),
            activeId = "games",
            onSelect = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
