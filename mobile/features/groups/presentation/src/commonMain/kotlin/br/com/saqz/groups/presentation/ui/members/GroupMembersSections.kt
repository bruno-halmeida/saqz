package br.com.saqz.groups.presentation.ui.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.members.GroupMemberAction
import br.com.saqz.groups.presentation.members.GroupMembersFilter
import br.com.saqz.groups.presentation.members.GroupMembersIntent
import br.com.saqz.groups.presentation.members.JoinRequestUi
import br.com.saqz.groups.presentation.members.MemberUi
import br.com.saqz.groups.presentation.members.sheetActions
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_member_cancel
import br.com.saqz.groups.resources.group_member_edit
import br.com.saqz.groups.resources.group_member_edit_hint
import br.com.saqz.groups.resources.group_member_make_admin
import br.com.saqz.groups.resources.group_member_make_admin_hint
import br.com.saqz.groups.resources.group_member_remove
import br.com.saqz.groups.resources.group_member_remove_admin
import br.com.saqz.groups.resources.group_member_remove_admin_hint
import br.com.saqz.groups.resources.group_member_view_profile
import br.com.saqz.groups.resources.group_members_admin
import br.com.saqz.groups.resources.group_members_filter_admins
import br.com.saqz.groups.resources.group_members_filter_all
import br.com.saqz.groups.resources.group_members_filter_pending
import br.com.saqz.groups.resources.group_members_join_requests
import br.com.saqz.groups.resources.group_members_pending
import br.com.saqz.groups.resources.group_members_search
import br.com.saqz.groups.resources.group_members_you
import br.com.saqz.groups.resources.groups_invite_accept
import br.com.saqz.groups.resources.groups_invite_decline
import org.jetbrains.compose.resources.stringResource

// O export escreve meta, estatística e rodapé da lista em 13px, e a escala do design
// system pula de 14 (`support`) para 12 (`caption`). O `copy` mantém o número do desenho
// sem abrir token novo — mesma saída que o VUL-44 adotou para o rótulo do campo.
@Composable
private fun supportStyle() = SaqzTheme.typography.support.copy(fontSize = 13.sp)

/** Campo de busca do 2k: lupa à esquerda, "Buscar membro" no lugar do valor. */
@Composable
internal fun GroupMembersSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.group_members_search)
    SaqzInput(
        value = query,
        onValueChange = onQueryChange,
        label = label,
        placeholder = label,
        showLabel = false,
        inlineLabel = true,
        leadingContent = { SaqzIcon(SaqzIcons.Search, tint = SaqzTheme.colors.textSecondary) },
        modifier = modifier.testTag(GroupMembersTags.Search),
    )
}

/** As três pílulas com contagem. A selecionada é sólida em `primary`, texto branco. */
@Composable
internal fun GroupMembersFilters(
    filter: GroupMembersFilter,
    totalCount: Int,
    adminCount: Int,
    pendingCount: Int,
    onSelect: (GroupMembersFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        GroupMembersFilter.All to stringResource(Res.string.group_members_filter_all, totalCount),
        GroupMembersFilter.Admins to stringResource(Res.string.group_members_filter_admins, adminCount),
        GroupMembersFilter.Pending to stringResource(Res.string.group_members_filter_pending, pendingCount),
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        labels.forEach { (value, label) ->
            SaqzChoiceChip(
                label = label,
                selected = filter == value,
                onClick = { onSelect(value) },
                modifier = Modifier.testTag(GroupMembersTags.filter(value)),
            )
        }
    }
}

/**
 * Linha de pedido para entrar. Quem ainda espera triagem (`awaitingReview`) mostra só o
 * chip "Pendente"; quem já pode ser decidido mostra os dois círculos do export — recusar
 * branco com borda, aceitar azul sólido.
 */
@Composable
internal fun GroupJoinRequestRow(
    request: JoinRequestUi,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    SaqzMemberRow(
        name = request.name,
        meta = request.meta,
        modifier = modifier.testTag(GroupMembersTags.request(request.id)),
        trailing = {
            if (request.awaitingReview) {
                SaqzStatusChip(
                    text = stringResource(Res.string.group_members_pending),
                    tone = SaqzChipTone.Warning,
                    dot = true,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.grid)) {
                    // TODO(VUL-66): faltam `group_members_accept`/`group_members_decline`
                    // com o nome de quem pediu. Até lá, o verbo solto já distingue os dois
                    // botões para o leitor de tela.
                    SaqzIconButton(
                        onClick = onDecline,
                        contentDescription = stringResource(Res.string.groups_invite_decline),
                        outlined = true,
                        // 40 no export; a grade de 8 do design system não tem o número.
                        size = metrics.grid * 5,
                        modifier = Modifier.testTag(GroupMembersTags.decline(request.id)),
                    ) {
                        SaqzIcon(SaqzIcons.Close, tint = SaqzTheme.colors.textSecondary, size = metrics.grid * 2)
                    }
                    SaqzIconButton(
                        onClick = onAccept,
                        contentDescription = stringResource(Res.string.groups_invite_accept),
                        filled = true,
                        size = metrics.grid * 5,
                        modifier = Modifier.testTag(GroupMembersTags.accept(request.id)),
                    ) {
                        SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.onPrimary, size = metrics.grid * 2)
                    }
                }
            }
        },
    )
}

/** Cabeçalho "Pedidos para entrar" mais o card com as linhas de pedido. */
@Composable
internal fun GroupJoinRequestsSection(
    requests: List<JoinRequestUi>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        GroupMembersSectionHeader(
            title = stringResource(Res.string.group_members_join_requests),
            action = null,
        )
        SaqzCard(padded = false) {
            requests.forEachIndexed { index, request ->
                if (index > 0) SaqzDivider()
                GroupJoinRequestRow(
                    request = request,
                    onAccept = { onAccept(request.id) },
                    onDecline = { onDecline(request.id) },
                )
            }
        }
    }
}

/**
 * Cabeçalho da seção mais o card com as linhas. [footer] é o "Mostrando N de M membros",
 * que só a seção de Membros carrega.
 */
@Composable
internal fun GroupMemberListSection(
    title: String,
    members: List<MemberUi>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    action: String? = null,
    footer: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        GroupMembersSectionHeader(title = title, action = action)
        SaqzCard(padded = false) {
            members.forEachIndexed { index, member ->
                if (index > 0) SaqzDivider()
                GroupMemberRow(member = member, onOpen = onOpen)
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                style = supportStyle(),
                color = SaqzTheme.colors.textSecondary,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid),
            )
        }
    }
}

@Composable
private fun GroupMemberRow(member: MemberUi, onOpen: (String) -> Unit) {
    SaqzMemberRow(
        // TODO(VUL-96): o export escreve o "· você" em peso 400 e `textSecondary`, e o
        // `SaqzMemberRow` recebe o nome como String. Concatenado aqui até o design system
        // aceitar um sufixo com estilo próprio.
        name = if (member.isSelf) {
            "${member.name} · ${stringResource(Res.string.group_members_you)}"
        } else {
            member.name
        },
        meta = member.meta,
        // O sufixo entra no nome e as iniciais saem dele: sem isto, "Lucas Prado · você"
        // vira "LV". O `photo` recebe o mesmo avatar, desenhado a partir do nome limpo.
        photo = if (member.isSelf) {
            { SaqzAvatar(name = member.name, initialsColor = SaqzTheme.colors.primary) }
        } else {
            null
        },
        // A própria linha não abre sheet no 2k; a ViewModel repete a guarda.
        onClick = if (member.isSelf) null else ({ onOpen(member.id) }),
        modifier = Modifier.testTag(GroupMembersTags.member(member.id)),
        trailing = {
            if (member.isAdmin) {
                SaqzStatusChip(text = stringResource(Res.string.group_members_admin), tone = SaqzChipTone.Brand)
            } else {
                // TODO(VUL-96): o export usa três pontos verticais. A Lucide entra no
                // design system como `implementation`, então `MoreVertical` só existe
                // depois do ticket; o chevron diz a mesma coisa — a linha abre algo.
                SaqzIcon(
                    SaqzIcons.ChevronRight,
                    tint = SaqzTheme.colors.textSecondary,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        },
    )
}

/** O carregando não está desenhado: esqueleto na forma da linha de membro. */
@Composable
internal fun GroupMembersLoading(modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    SaqzCard(modifier = modifier.testTag(GroupMembersTags.Loading), padded = false) {
        repeat(3) { index ->
            if (index > 0) SaqzDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SaqzSkeleton(width = metrics.grid * 5, height = metrics.grid * 5, circle = true)
                Column(verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
                    SaqzSkeleton(width = metrics.grid * 15, height = metrics.blockGap)
                    SaqzSkeleton(width = metrics.grid * 11, height = metrics.grid + metrics.subGrid / 2)
                }
            }
        }
    }
}

/**
 * O sheet do 2k e o do 2l: mesmo painel, linhas derivadas de `selected.isAdmin`.
 * Cabeçalho com avatar de 48, nome 17/700, estatística e o chip "Admin" quando for o
 * caso — separado das ações pela divisória.
 */
@Composable
internal fun GroupMemberActionsSheet(
    selected: MemberUi?,
    onIntent: (GroupMembersIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzBottomSheet(
        open = selected != null,
        onClose = { onIntent(GroupMembersIntent.DismissSheet) },
        modifier = modifier,
        footer = {
            SaqzButton(
                label = stringResource(Res.string.group_member_cancel),
                onClick = { onIntent(GroupMembersIntent.DismissSheet) },
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
                modifier = Modifier.testTag(GroupMembersTags.Cancel),
            )
        },
    ) {
        // O painel ainda compõe durante a animação de saída, quando já não há ninguém
        // selecionado — sem membro não há cabeçalho nem ações a derivar.
        val member = selected ?: return@SaqzBottomSheet
        Row(
            modifier = Modifier.fillMaxWidth().testTag(GroupMembersTags.Sheet),
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzAvatar(name = member.name, size = metrics.grid * 6)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
                Text(
                    text = member.name,
                    style = SaqzTheme.typography.subtitle.copy(fontSize = 17.sp, fontWeight = FontWeight(700)),
                    color = colors.textPrimary,
                )
                if (member.stats.isNotBlank()) {
                    Text(text = member.stats, style = supportStyle(), color = colors.textSecondary)
                }
            }
            if (member.isAdmin) {
                SaqzStatusChip(text = stringResource(Res.string.group_members_admin), tone = SaqzChipTone.Brand)
            }
        }
        SaqzDivider()
        Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            member.sheetActions().forEach { action ->
                GroupMemberActionRow(
                    action = action,
                    onClick = { onIntent(GroupMembersIntent.PerformAction(action)) },
                )
            }
        }
    }
}

/**
 * Linha de ação do sheet: 56 de altura mínima, ícone de 22 em `primary`, título 16/600 e
 * subtítulo opcional 13/400. "Remover do grupo" é a exceção — inteira em
 * `errorForeground`, sem subtítulo e sem ícone azul.
 */
@Composable
internal fun GroupMemberActionRow(
    action: GroupMemberAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val destructive = action == GroupMemberAction.Remove
    val foreground = if (destructive) colors.errorForeground else colors.textPrimary
    val title = stringResource(action.titleResource())
    val hint = action.hintResource()?.let { stringResource(it) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = title, role = Role.Button, onClick = onClick)
            .heightIn(min = metrics.grid * 7)
            .padding(horizontal = metrics.subGrid)
            .testTag(GroupMembersTags.action(action)),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(action.icon(), tint = if (destructive) colors.errorForeground else colors.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2)) {
            Text(
                text = title,
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight(600)),
                color = foreground,
            )
            if (hint != null) {
                Text(text = hint, style = supportStyle(), color = colors.textSecondary)
            }
        }
    }
}

/**
 * A contagem do cabeçalho de Membros não é botão no export, e o `SaqzSectionHeader` só
 * pinta a ação quando recebe `onAction` — ela entra ao lado do cabeçalho, não dentro.
 *
 * TODO(VUL-96): ação estática no `SaqzSectionHeader` resolve isto dentro do componente.
 */
@Composable
private fun GroupMembersSectionHeader(title: String, action: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = SaqzTheme.metrics.grid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        SaqzSectionHeader(title = title, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                text = action,
                style = supportStyle().copy(fontWeight = FontWeight(600)),
                color = SaqzTheme.colors.textSecondary,
            )
        }
    }
}

private fun GroupMemberAction.titleResource() = when (this) {
    GroupMemberAction.ViewProfile -> Res.string.group_member_view_profile
    GroupMemberAction.EditMember -> Res.string.group_member_edit
    GroupMemberAction.Promote -> Res.string.group_member_make_admin
    GroupMemberAction.Demote -> Res.string.group_member_remove_admin
    GroupMemberAction.Remove -> Res.string.group_member_remove
}

private fun GroupMemberAction.hintResource() = when (this) {
    GroupMemberAction.EditMember -> Res.string.group_member_edit_hint
    GroupMemberAction.Promote -> Res.string.group_member_make_admin_hint
    GroupMemberAction.Demote -> Res.string.group_member_remove_admin_hint
    // "Ver perfil" e "Remover do grupo" são linhas de uma frase só no export.
    GroupMemberAction.ViewProfile, GroupMemberAction.Remove -> null
}

// TODO(VUL-96): o export usa lápis (editar), estrela (admin) e user-minus (remover). Os
// três glifos existem na Lucide mas não no `SaqzIcons`, e a Lucide entra no design system
// como `implementation` — inalcançável daqui. Até o ticket, o glifo mais próximo do
// conceito; "Ver perfil" e "Editar jogador" nunca aparecem no mesmo sheet, então dividir
// `User` entre os dois não colide.
private fun GroupMemberAction.icon() = when (this) {
    GroupMemberAction.ViewProfile, GroupMemberAction.EditMember -> SaqzIcons.User
    GroupMemberAction.Promote, GroupMemberAction.Demote -> SaqzIcons.Users
    GroupMemberAction.Remove -> SaqzIcons.Trash
}
