package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.ui.components.HeroOutlineAlpha
import br.com.saqz.groups.presentation.ui.invite.InviteLinkSheet
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_first_access_body
import br.com.saqz.groups.resources.home_first_access_create
import br.com.saqz.groups.resources.home_first_access_invite
import br.com.saqz.groups.resources.home_first_access_kicker
import br.com.saqz.groups.resources.home_first_access_title
import br.com.saqz.groups.resources.home_step_cd
import br.com.saqz.groups.resources.home_step_done
import br.com.saqz.groups.resources.home_step_game_body
import br.com.saqz.groups.resources.home_step_game_title
import br.com.saqz.groups.resources.home_step_group_body
import br.com.saqz.groups.resources.home_step_group_title
import br.com.saqz.groups.resources.home_step_invite_body
import br.com.saqz.groups.resources.home_step_invite_title
import br.com.saqz.groups.resources.home_step_now
import br.com.saqz.groups.resources.home_steps_title
import org.jetbrains.compose.resources.stringResource

/**
 * Primeiro acesso: a Início de quem acabou de criar a conta e não tem grupo nenhum.
 *
 * É o onboarding da Início, e dura só até o primeiro grupo existir — dali em diante a tela
 * volta ao normal e o guia mora no grupo. Quem chega por convite não passa por aqui: cai no
 * convite e depois no grupo. O hero oferece os dois caminhos (criar o grupo ou colar um
 * convite) e a lista mostra os três passos; só o passo atual é um atalho, os seguintes
 * ficam apagados porque ainda não existe lugar para ir.
 */
@Composable
internal fun HomeFirstAccessHero(
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzHeroCard(
        kicker = stringResource(Res.string.home_first_access_kicker),
        title = stringResource(Res.string.home_first_access_title),
        meta = stringResource(Res.string.home_first_access_body),
        modifier = modifier.testTag(HomeTags.FirstAccess),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            SaqzButton(
                label = stringResource(Res.string.home_first_access_create),
                onClick = { onIntent(HomeIntent.CreateGroup) },
                variant = SaqzButtonVariant.Accent,
                fullWidth = true,
                modifier = Modifier.weight(1f).testTag(HomeTags.FirstAccessCreate),
            )
            SaqzButton(
                label = stringResource(Res.string.home_first_access_invite),
                onClick = { onIntent(HomeIntent.OpenInviteSheet) },
                variant = SaqzButtonVariant.Ghost,
                contentColor = colors.onPrimary,
                borderColor = colors.onPrimary.copy(alpha = HeroOutlineAlpha),
                fullWidth = true,
                modifier = Modifier.weight(1f).testTag(HomeTags.FirstAccessInvite),
            )
        }
    }
}

@Composable
internal fun HomeFirstAccessSteps(
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(HomeTags.Steps),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.home_steps_title))
        SaqzCard(padded = false) {
            HomeStepRow(
                number = 1,
                title = stringResource(Res.string.home_step_group_title),
                body = stringResource(Res.string.home_step_group_body),
                state = HomeStepState.Now,
                onClick = { onIntent(HomeIntent.CreateGroup) },
                modifier = Modifier.testTag(HomeTags.step(1)),
            )
            SaqzDivider()
            HomeStepRow(
                number = 2,
                title = stringResource(Res.string.home_step_game_title),
                body = stringResource(Res.string.home_step_game_body),
                state = HomeStepState.Next,
                onClick = null,
                modifier = Modifier.testTag(HomeTags.step(2)),
            )
            SaqzDivider()
            HomeStepRow(
                number = 3,
                title = stringResource(Res.string.home_step_invite_title),
                body = stringResource(Res.string.home_step_invite_body),
                state = HomeStepState.Next,
                onClick = null,
                modifier = Modifier.testTag(HomeTags.step(3)),
            )
        }
    }
}

internal enum class HomeStepState { Done, Now, Next }

/**
 * Uma linha da lista de passos. [onClick] nulo é passo sem destino ainda (apagado, sem seta);
 * com destino a linha inteira é o atalho, para o toque não depender de acertar um botão.
 */
@Composable
internal fun HomeStepRow(
    number: Int,
    title: String,
    body: String,
    state: HomeStepState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val description = stringResource(Res.string.home_step_cd, number, title)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = title, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = description }
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        HomeStepCircle(number = number, state = state)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2),
        ) {
            Text(
                text = title,
                style = SaqzTheme.typography.compactTitle,
                color = if (state == HomeStepState.Next) colors.textSecondary else colors.textPrimary,
            )
            Text(
                text = body,
                style = SaqzTheme.typography.compactMeta,
                color = colors.textSecondary,
            )
        }
        when (state) {
            HomeStepState.Now -> SaqzStatusChip(
                text = stringResource(Res.string.home_step_now),
                tone = SaqzChipTone.Brand,
            )
            HomeStepState.Done -> SaqzStatusChip(
                text = stringResource(Res.string.home_step_done),
                tone = SaqzChipTone.Success,
            )
            HomeStepState.Next -> Unit
        }
        if (onClick != null) {
            SaqzIcon(SaqzIcons.ChevronRight, tint = colors.textSecondary)
        }
    }
}

@Composable
private fun HomeStepCircle(number: Int, state: HomeStepState) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val container = when (state) {
        HomeStepState.Done -> colors.success
        HomeStepState.Now -> colors.primary
        HomeStepState.Next -> colors.surfaceSoft
    }
    Box(
        modifier = Modifier
            .size(metrics.iconButtonSize - metrics.grid)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (state == HomeStepState.Done) {
            SaqzIcon(SaqzIcons.Check, tint = colors.onPrimary)
        } else {
            Text(
                text = number.toString(),
                style = SaqzTheme.typography.label,
                color = if (state == HomeStepState.Now) colors.onPrimary else colors.textSecondary,
            )
        }
    }
}

/** A folha "Tenho um convite" ligada ao estado da Início; a folha em si é [InviteLinkSheet]. */
@Composable
internal fun HomeInviteSheet(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
) {
    InviteLinkSheet(
        open = state.inviteSheetOpen,
        link = state.inviteLink,
        invalid = state.inviteLinkInvalid,
        onLinkChange = { onIntent(HomeIntent.InviteLinkChanged(it)) },
        onClose = { onIntent(HomeIntent.CloseInviteSheet) },
        onSubmit = { onIntent(HomeIntent.SubmitInviteLink) },
        fieldTag = HomeTags.InviteSheetField,
        submitTag = HomeTags.InviteSheetSubmit,
    )
}
