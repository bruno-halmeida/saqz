package br.com.saqz.composeapp.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.catalog_section_actions
import br.com.saqz.composeapp.resources.catalog_section_data
import br.com.saqz.composeapp.resources.catalog_section_feedback
import br.com.saqz.composeapp.resources.catalog_section_forms
import br.com.saqz.composeapp.resources.catalog_section_foundations
import br.com.saqz.composeapp.resources.catalog_section_navigation
import br.com.saqz.composeapp.resources.catalog_title
import br.com.saqz.designsystem.SaqzAttendance
import br.com.saqz.designsystem.SaqzAttendanceSelector
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzBottomNav
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzChoiceChipDefaults
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzGameSummaryCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.SaqzNavItem
import br.com.saqz.designsystem.SaqzOfflineBanner
import br.com.saqz.designsystem.SaqzProgressBar
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * `testTag` do catálogo. Os controles com estado ganham tag porque o teste os toca;
 * os espécimes sem estado não precisam.
 */
object SaqzCatalogTags {
    const val Root = "saqz-catalog-root"
    const val Content = "saqz-catalog-content"
    const val Foundations = "saqz-catalog-fundamentos"
    const val Actions = "saqz-catalog-acoes"
    const val Forms = "saqz-catalog-formularios"
    const val Data = "saqz-catalog-dados"
    const val Feedback = "saqz-catalog-feedback"
    const val Navigation = "saqz-catalog-navegacao"
    const val MotionTokens = "saqz-catalog-motion"
    const val Attendance = "saqz-catalog-presenca"
    const val Switch = "saqz-catalog-switch"
    const val Stepper = "saqz-catalog-stepper"
    const val Segmented = "saqz-catalog-segmented"
    const val Chips = "saqz-catalog-chips"
    const val CompactChips = "saqz-catalog-chips-compactos"
    const val ToastTrigger = "saqz-catalog-toast-trigger"
    const val Toast = "saqz-catalog-toast"
    const val BottomNav = "saqz-catalog-bottom-nav"
    const val SheetTrigger = "saqz-catalog-sheet-trigger"
    const val Sheet = "saqz-catalog-sheet"
    const val SheetCancel = "saqz-catalog-sheet-cancelar"
}

/**
 * Catálogo do fluxo 10 do export, rodando dentro do app — só no ambiente dev
 * ([br.com.saqz.composeapp.shell.SaqzAppShell] decide quem vê a entrada).
 *
 * Existe porque quadro estático não é animação: o golden do Roborazzi fotografava o
 * spinner indeterminado com varredura ~0 e ninguém viu a divergência de `.18s` do
 * switch olhando PNG. Aqui o spinner gira, o thumb desliza, o sheet sobe em 320ms e o
 * toast morre sozinho em 2600ms — no relógio real, com o dedo do usuário.
 *
 * As cenas vêm de `SaqzScreenshotTest`; o que muda é que cada controle tem estado.
 *
 * Pública desde que a captura de review em `:android-app` compunha a tela daqui. Essa
 * captura por ticket morreu com o VUL-55 — os PNGs saíram do git e cada PR leva os seus.
 * Quem decide se a tela é alcançável é o [br.com.saqz.composeapp.shell.SaqzAppShell],
 * que só mostra a entrada em dev.
 *
 * ponytail: a moldura (título e nome das seções) vem de `composeResources`; o texto
 * *dentro* de cada espécime é literal, como já era no screenshot test. É fixture de
 * amostra copiada do export — não é cópia de produto que alguém vá traduzir —, e
 * setenta entradas em `strings.xml` para uma tela que não sai do dev é boilerplate.
 * Se o catálogo virar material de cliente, os espécimes sobem para recurso.
 *
 * ponytail: `Column` + `verticalScroll` em vez de `LazyColumn` — o catálogo tem ~40
 * peças, e lazy descartaria a composição das seções fora da tela, junto com o estado
 * e a animação em curso. Vira `LazyColumn` no dia em que doer o scroll.
 */
@Composable
fun SaqzCatalogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    var sheetOpen by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(SaqzCatalogTags.Root),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(title = stringResource(Res.string.catalog_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
                    .testTag(SaqzCatalogTags.Content),
                verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
            ) {
                CatalogSection(Res.string.catalog_section_foundations, SaqzCatalogTags.Foundations) {
                    FoundationSpecimens()
                }
                CatalogSection(Res.string.catalog_section_actions, SaqzCatalogTags.Actions) {
                    ActionSpecimens()
                }
                CatalogSection(Res.string.catalog_section_forms, SaqzCatalogTags.Forms) {
                    FormSpecimens()
                }
                CatalogSection(Res.string.catalog_section_data, SaqzCatalogTags.Data) {
                    DataSpecimens()
                }
                CatalogSection(Res.string.catalog_section_feedback, SaqzCatalogTags.Feedback) {
                    FeedbackSpecimens()
                }
                CatalogSection(Res.string.catalog_section_navigation, SaqzCatalogTags.Navigation) {
                    NavigationSpecimens(onOpenSheet = { sheetOpen = true })
                }
            }
        }
        // O sheet é sobreposição, não Dialog: precisa ser o último filho do Box da tela.
        SaqzBottomSheet(
            open = sheetOpen,
            onClose = { sheetOpen = false },
            modifier = Modifier.testTag(SaqzCatalogTags.Sheet),
            title = "Sair da conta?",
            description = "Você volta para a tela de entrada e precisa entrar de novo.",
            splitFooter = {
                SaqzButton(
                    label = "Cancelar",
                    onClick = { sheetOpen = false },
                    modifier = Modifier.weight(1f).testTag(SaqzCatalogTags.SheetCancel),
                    variant = SaqzButtonVariant.Secondary,
                )
                SaqzButton(
                    label = "Confirmar saída",
                    onClick = { sheetOpen = false },
                    modifier = Modifier.weight(1f),
                    variant = SaqzButtonVariant.Danger,
                )
            },
            content = {},
        )
    }
}

@Composable
private fun CatalogSection(
    title: StringResource,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(title))
        content()
    }
}

// Fundamentos: cor, tipografia e — o que nenhum PNG mostra — as durações vigentes.
// Sob Reduce Motion os números caem para 0 aqui na tela, junto com as animações.
@Composable
private fun ColumnScope.FoundationSpecimens() {
    val colors = SaqzTheme.colors
    val motion = SaqzTheme.motion
    val swatches = listOf(
        "background" to colors.background,
        "surface" to colors.surface,
        "surfaceSoft" to colors.surfaceSoft,
        "primary" to colors.primary,
        "primaryPressed" to colors.primaryPressed,
        "accent" to colors.accent,
        "textPrimary" to colors.textPrimary,
        "textSecondary" to colors.textSecondary,
        "border" to colors.border,
        "success" to colors.success,
        "warning" to colors.warning,
        "errorForeground" to colors.errorForeground,
        "disabledSurface" to colors.disabledSurface,
        "chrome" to colors.chrome,
    )
    val perRow = 4
    swatches.chunked(perRow).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { (name, color) -> ColorSwatch(name = name, color = color, modifier = Modifier.weight(1f)) }
            // Sem os vazios, a última fila incompleta esticaria os swatches e a amostra
            // mentiria sobre o tamanho.
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    SaqzDivider()
    val scale = listOf(
        "headline" to SaqzTheme.typography.headline,
        "title" to SaqzTheme.typography.title,
        "subtitle" to SaqzTheme.typography.subtitle,
        "body" to SaqzTheme.typography.body,
        "support" to SaqzTheme.typography.support,
        "label" to SaqzTheme.typography.label,
        "caption" to SaqzTheme.typography.caption,
        "eyebrow" to SaqzTheme.typography.eyebrow,
        "navigation" to SaqzTheme.typography.navigation,
    )
    scale.forEach { (name, style) -> TypeSpecimen(name = name, style = style) }
    SaqzDivider()
    Text(
        text = "sheet ${motion.sheetDurationMillis}ms · thumb do segmented ${motion.thumbDurationMillis}ms · " +
            "switch ${motion.switchDurationMillis}ms · toast ${motion.toastDwellMillis}ms",
        style = SaqzTheme.typography.support,
        color = colors.textSecondary,
        modifier = Modifier.testTag(SaqzCatalogTags.MotionTokens),
    )
}

@Composable
private fun ColorSwatch(name: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(color, RoundedCornerShape(8.dp))
                .border(1.dp, SaqzTheme.colors.border, RoundedCornerShape(8.dp)),
        )
        Text(text = name, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
    }
}

@Composable
private fun TypeSpecimen(name: String, style: TextStyle, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.textSecondary,
            modifier = Modifier.width(88.dp),
        )
        Text(text = "Bom jogo, galera", style = style, color = SaqzTheme.colors.textPrimary)
    }
}

@Composable
private fun ColumnScope.ActionSpecimens() {
    SaqzButton(label = "Confirmar presença", onClick = {}, fullWidth = true)
    SaqzButton(label = "Editar", onClick = {}, variant = SaqzButtonVariant.Secondary, fullWidth = true)
    SaqzButton(label = "Excluir grupo", onClick = {}, variant = SaqzButtonVariant.Danger, fullWidth = true)
    SaqzButton(label = "Cancelar", onClick = {}, variant = SaqzButtonVariant.Ghost, fullWidth = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SaqzButton(label = "Criar jogo", onClick = {}, size = SaqzButtonSize.Sm)
        SaqzButton(label = "Criando grupo", onClick = {}, loading = true)
        SaqzButton(label = "Criar grupo", onClick = {}, enabled = false)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SaqzIconButton(onClick = {}, contentDescription = "Voltar") { SaqzIcon(SaqzIcons.ChevronLeft) }
        SaqzIconButton(onClick = {}, contentDescription = "Notificações", dot = true) { SaqzIcon(SaqzIcons.Bell) }
        SaqzIconButton(onClick = {}, contentDescription = "Buscar", soft = true) { SaqzIcon(SaqzIcons.Search) }
        SaqzIconButton(onClick = {}, contentDescription = "Adicionar", soft = true) { SaqzIcon(SaqzIcons.Plus) }
    }
}

@Composable
private fun ColumnScope.FormSpecimens() {
    var email by remember { mutableStateOf("ana@saqz.app") }
    var venue by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("segredo") }
    var invalid by remember { mutableStateOf("ana") }
    var attendance by remember { mutableStateOf<SaqzAttendance?>(SaqzAttendance.Going) }
    var pending by remember { mutableStateOf<SaqzAttendance?>(null) }
    var weekly by remember { mutableStateOf(true) }
    var push by remember { mutableStateOf(false) }
    var slots by remember { mutableIntStateOf(12) }
    var gender by remember { mutableIntStateOf(2) }
    var filter by remember { mutableIntStateOf(0) }
    var compactDay by remember { mutableIntStateOf(2) }

    SaqzInput(email, { email = it }, label = "E-mail", kind = SaqzInputKind.Email)
    SaqzInput(venue, { venue = it }, label = "Local", placeholder = "CERET — Quadra 2")
    SaqzInput(password, { password = it }, label = "Senha", kind = SaqzInputKind.Password)
    SaqzInput(invalid, { invalid = it }, label = "E-mail", errorText = "Informe um e-mail válido")
    SaqzAttendanceSelector(
        value = attendance,
        onSelect = { attendance = it },
        modifier = Modifier.testTag(SaqzCatalogTags.Attendance),
    )
    SaqzAttendanceSelector(value = pending, onSelect = { pending = it })
    SaqzSwitch(
        checked = weekly,
        onCheckedChange = { weekly = it },
        modifier = Modifier.testTag(SaqzCatalogTags.Switch),
        label = "Jogo toda semana",
    )
    SaqzSwitch(checked = push, onCheckedChange = { push = it }, label = "Avisar por push")
    SaqzSwitch(checked = false, onCheckedChange = {}, label = "Bloqueado", enabled = false)
    SaqzStepper(
        value = slots,
        onValueChange = { slots = it },
        modifier = Modifier.testTag(SaqzCatalogTags.Stepper),
        min = 4,
        max = 24,
        label = "Vagas",
    )
    SaqzSegmented(
        options = listOf("Masculino", "Feminino", "Misto"),
        selected = gender,
        onSelect = { gender = it },
        modifier = Modifier.testTag(SaqzCatalogTags.Segmented),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(SaqzCatalogTags.Chips),
    ) {
        listOf("Todos · 26", "Admins · 2", "Pendentes · 2").forEachIndexed { index, label ->
            SaqzChoiceChip(label = label, selected = filter == index, onClick = { filter = index })
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SaqzCatalogTags.CompactChips),
        horizontalArrangement = Arrangement.spacedBy(SaqzChoiceChipDefaults.CompactSpacing),
    ) {
        listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEachIndexed { index, day ->
            SaqzChoiceChip(
                label = day,
                selected = compactDay == index,
                onClick = { compactDay = index },
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ColumnScope.DataSpecimens() {
    var confirmed by remember { mutableStateOf(false) }
    SaqzGameSummaryCard(
        eyebrow = "PRÓXIMO JOGO",
        title = "Ter, 28/07 · 19h30",
        venue = "CERET — Quadra 2",
        address = "Tatuapé, São Paulo",
        going = if (confirmed) 13 else 12,
        maybe = 3,
        out = 2,
    ) {
        SaqzButton(
            label = if (confirmed) "Presença confirmada" else "Confirmar presença",
            onClick = { confirmed = !confirmed },
            fullWidth = true,
            variant = if (confirmed) SaqzButtonVariant.Secondary else SaqzButtonVariant.Primary,
        )
    }
    SaqzCard {
        SaqzSectionHeader(title = "Confirmados", action = "Ver todos", onAction = {})
        SaqzAvatarStack(listOf("Lucas Pereira", "Bruna Silva", "Tiago Moraes", "A", "B", "C"))
    }
    SaqzCard(padded = false) {
        SaqzMemberRow(
            name = "Lucas Pereira",
            meta = "Mensalista · desde 2024",
            trailing = { SaqzStatusChip("Admin", tone = SaqzChipTone.Brand) },
        )
        SaqzDivider()
        SaqzMemberRow(
            name = "Bruna Silva",
            meta = "Avulsa",
            onClick = {},
            trailing = { SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary) },
        )
    }
    SaqzCard(tone = SaqzCardTone.Soft) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzStatusChip("Pendente", tone = SaqzChipTone.Warning, dot = true)
            SaqzStatusChip("Vou", tone = SaqzChipTone.Success, dot = true)
            SaqzStatusChip("Talvez", tone = SaqzChipTone.Accent)
            SaqzStatusChip("Não vou", tone = SaqzChipTone.Error)
            SaqzStatusChip("Reserva", tone = SaqzChipTone.Neutral)
        }
    }
}

@Composable
private fun ColumnScope.FeedbackSpecimens() {
    var toastVisible by remember { mutableStateOf(false) }
    SaqzButton(
        label = "Mostrar toast",
        onClick = { toastVisible = true },
        modifier = Modifier.testTag(SaqzCatalogTags.ToastTrigger),
        variant = SaqzButtonVariant.Secondary,
        fullWidth = true,
    )
    // Sem dispensa manual: o toast conta os 2600ms dele e chama onDismiss sozinho.
    SaqzToast(
        visible = toastVisible,
        onDismiss = { toastVisible = false },
        modifier = Modifier.testTag(SaqzCatalogTags.Toast),
    ) {
        SaqzToastText("Presença confirmada. Bom jogo!")
    }
    SaqzOfflineBanner()
    SaqzProgressBar(value = 0.4f)
    SaqzProgressBar()
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzSpinner(size = 16.dp)
        SaqzSpinner(size = 20.dp)
        SaqzSpinner(size = 30.dp)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzSkeleton(width = 40.dp, height = 40.dp, circle = true)
        SaqzSkeleton(width = 180.dp, height = 14.dp)
    }
    SaqzSkeleton(height = 72.dp, radius = 12.dp)
    SaqzEmptyState(
        title = "Nenhum jogo marcado",
        description = "Crie o próximo jogo e a galera recebe o convite na hora.",
        icon = SaqzIcons.Plus,
        action = "Criar jogo",
        onAction = { toastVisible = true },
    )
}

@Composable
private fun ColumnScope.NavigationSpecimens(onOpenSheet: () -> Unit) {
    var tab by remember { mutableStateOf("games") }
    SaqzTopAppBar(
        title = "Configurações do grupo",
        onBack = {},
        actions = {
            SaqzIconButton(onClick = {}, contentDescription = "Notificações", dot = true) {
                SaqzIcon(SaqzIcons.Bell)
            }
        },
    )
    SaqzBottomNav(
        items = listOf(
            SaqzNavItem("home", "Início", SaqzIcons.Home),
            SaqzNavItem("games", "Jogos", SaqzIcons.Calendar),
            SaqzNavItem("people", "Grupos", SaqzIcons.Users),
            SaqzNavItem("me", "Perfil", SaqzIcons.User),
        ),
        activeId = tab,
        onSelect = { tab = it },
        modifier = Modifier.testTag(SaqzCatalogTags.BottomNav),
    )
    SaqzButton(
        label = "Abrir sheet",
        onClick = onOpenSheet,
        modifier = Modifier.testTag(SaqzCatalogTags.SheetTrigger),
        fullWidth = true,
    )
}

@Preview
@Composable
private fun SaqzCatalogScreenPreview() = SaqzTheme { SaqzCatalogScreen(onBack = {}) }
