package br.com.saqz.groups.presentation.ui.gamedetail

import br.com.saqz.groups.presentation.photo.MemberPhoto
import br.com.saqz.groups.presentation.photo.avatarSlot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDashedCircle
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.presentation.gamedetail.GameDetailConfirmedUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestRowUi
import br.com.saqz.groups.presentation.gamedetail.GamePositionUi
import br.com.saqz.groups.presentation.gamedetail.canForce
import br.com.saqz.groups.presentation.ui.pluralLabel
import br.com.saqz.groups.presentation.ui.singularLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_no_show_footer
import br.com.saqz.groups.resources.game_no_show_footer_closed
import br.com.saqz.groups.resources.game_no_show_tag
import br.com.saqz.groups.resources.game_declined_count
import br.com.saqz.groups.resources.game_declined_empty_body
import br.com.saqz.groups.resources.game_declined_empty_title
import br.com.saqz.groups.resources.game_guest_add
import br.com.saqz.groups.resources.game_guest_row_hint
import br.com.saqz.groups.resources.game_person_actions
import br.com.saqz.groups.resources.game_place_action
import br.com.saqz.groups.resources.game_position_complete
import br.com.saqz.groups.resources.game_position_missing_chip
import br.com.saqz.groups.resources.game_queue_ordinal
import br.com.saqz.groups.resources.game_slot_open
import br.com.saqz.groups.resources.game_slot_place
import br.com.saqz.groups.resources.game_slot_position
import br.com.saqz.groups.resources.game_slot_yours
import br.com.saqz.groups.resources.game_slots_more
import br.com.saqz.groups.resources.game_waitlist_empty
import br.com.saqz.groups.resources.game_waitlist_note_fifo
import br.com.saqz.groups.resources.game_waitlist_note_organizer
import br.com.saqz.groups.resources.game_waitlist_place
import br.com.saqz.groups.resources.game_waitlist_promotion_failed
import br.com.saqz.groups.resources.game_waitlist_promotion_reason
import br.com.saqz.groups.resources.game_waitlist_reason_manual
import br.com.saqz.groups.resources.game_waitlist_reason_started
import br.com.saqz.groups.resources.group_member_membership_monthly
import br.com.saqz.groups.resources.group_member_membership_single_game
import br.com.saqz.groups.resources.home_waitlist_you
import br.com.saqz.groups.resources.position_forced_tag
import br.com.saqz.groups.resources.position_without_group
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Aba Vão: confirmados numerados e as vagas abertas (até 3 linhas e um "+ N"); com posições, uma seção por
 * posição. No fim, "Levar convidado" para quem pode levar e "Colocar alguém no jogo" para o gestor.
 */
@Composable
internal fun GameGoingList(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) {
    val slotsOpen = state.header?.statusTone == GameDetailStatusTone.Published && state.header.responseOpen
    var divider = false
    if (state.hasPositions) {
        val sections = state.positions.filter { it.slots > 0 }.sortedBy { it.position.ordinal }
        sections.forEach { section ->
            PositionBand(section)
            val people = state.playedRoster.filter { it.position == section.position }
            people.forEachIndexed { index, person -> ConfirmedRow(state, person, "${index + 1}", index > 0, onIntent) }
            if (slotsOpen) OpenSlots(state, section.missing, people.size, people.isNotEmpty(), section.position, onIntent)
        }
        val known = sections.map { it.position }.toSet()
        val others = state.playedRoster.filter { it.position == null || it.position !in known }
        if (others.isNotEmpty()) {
            NoPositionBand()
            others.forEachIndexed { index, person -> ConfirmedRow(state, person, "${index + 1}", index > 0, onIntent) }
        }
        divider = true
    } else {
        state.playedRoster.forEachIndexed { index, person ->
            ConfirmedRow(state, person, "${index + 1}", index > 0, onIntent)
        }
        val open = state.attendance?.availableSpots ?: 0
        if (slotsOpen) OpenSlots(state, open, state.playedRoster.size, state.playedRoster.isNotEmpty(), null, onIntent)
        divider = state.playedRoster.isNotEmpty() || (slotsOpen && open > 0)
    }
    if (state.guest.visible && state.guest.enabled) {
        ActionRow(
            label = stringResource(Res.string.game_guest_add),
            meta = stringResource(Res.string.game_guest_row_hint),
            icon = SaqzIcons.UserPlus,
            divider = divider,
            tag = GameGuestTags.Add,
            onClick = { onIntent(GameDetailIntent.OpenGuestSheet) },
        )
        divider = true
    }
    if (state.canManageAttendance && !state.placeBlocked) {
        ActionRow(
            label = stringResource(Res.string.game_place_action),
            meta = null,
            icon = SaqzIcons.Plus,
            divider = divider,
            tag = GameOrganizerTags.PlaceAction,
            onClick = { onIntent(GameDetailIntent.OpenPlaceSheet) },
        )
    }
}

/** As vagas abertas: no máximo três linhas e, se faltar mais, "+ N vagas abertas". */
@Composable
private fun OpenSlots(
    state: GameDetailState,
    open: Int,
    taken: Int,
    dividerFirst: Boolean,
    position: AthletePosition?,
    onIntent: (GameDetailIntent) -> Unit,
) {
    if (open <= 0) return
    val shown = minOf(open, MAX_OPEN_ROWS)
    val tappable = state.canManageAttendance && !state.placeBlocked
    // "Pode ser a sua" é para quem ainda não respondeu e pode responder.
    val yours = !tappable && state.response.status == null && state.header?.canRespond == true && state.response.visible
    val label = position?.let { stringResource(Res.string.game_slot_position, it.singularLabel().lowercase()) }
        ?: stringResource(Res.string.game_slot_open)
    repeat(shown) { index ->
        val color = if (tappable) SaqzTheme.colors.primary else SaqzTheme.colors.textPlaceholder
        SaqzMemberRow(
            name = label,
            meta = when {
                tappable -> stringResource(Res.string.game_slot_place)
                yours && index == 0 -> stringResource(Res.string.game_slot_yours)
                else -> null
            },
            nameColor = if (tappable) SaqzTheme.colors.primary else SaqzTheme.colors.textSecondary,
            leading = { NumberCell("${taken + index + 1}") },
            avatar = { SaqzDashedCircle(color, icon = if (tappable) SaqzIcons.Plus else null) },
            divider = dividerFirst || index > 0,
            onClick = if (tappable) { { onIntent(GameDetailIntent.OpenPlaceSheet) } } else null,
            modifier = Modifier.testTag(GameDetailTags.openSlot(taken + index)),
        )
    }
    if (open > shown) {
        SaqzMemberRow(
            name = pluralStringResource(Res.plurals.game_slots_more, open - shown, open - shown),
            nameColor = SaqzTheme.colors.textSecondary,
            leading = { NumberCell("") },
            avatar = { SaqzDashedCircle(SaqzTheme.colors.textPlaceholder) },
            divider = true,
            modifier = Modifier.testTag(GameDetailTags.MoreSlots),
        )
    }
}

@Composable
private fun ConfirmedRow(
    state: GameDetailState,
    person: GameDetailConfirmedUi,
    number: String,
    divider: Boolean,
    onIntent: (GameDetailIntent) -> Unit,
) {
    val base = personMeta(state, person.position, person.isMensalista, person.guest, confirmed = true)
    val meta = if (person.forced) listOfNotNull(base, stringResource(Res.string.position_forced_tag)).joinToString(" · ") else base
    PersonRow(
        id = person.id,
        name = person.name,
        meta = meta,
        number = number,
        isYou = person.isYou,
        guest = person.guest != null,
        divider = divider,
        hasActions = person.canRemove || person.guest?.canRemove == true || state.canMarkNoShow,
        onIntent = onIntent,
        photo = person.photo,
    )
}

/**
 * Aba "Faltaram" (PRES-016, só gestor): quem confirmou e não apareceu, com a etiqueta "Faltou" e o ⋮ do Desfazer
 * enquanto o prazo vale. O rodapé lembra que só o gestor vê e até quando dá para marcar.
 */
@Composable
internal fun GameNoShowList(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) {
    state.noShowRoster.forEachIndexed { index, person ->
        PersonRow(
            id = person.id,
            name = person.name,
            meta = personMeta(state, person.position, person.isMensalista, person.guest, confirmed = true),
            number = null,
            isYou = person.isYou,
            guest = person.guest != null,
            divider = index > 0,
            hasActions = state.canMarkNoShow,
            onIntent = onIntent,
            action = {
                SaqzStatusChip(
                    text = stringResource(Res.string.game_no_show_tag),
                    tone = SaqzChipTone.Warning,
                    modifier = Modifier.testTag(GameDetailTags.noShowTag(person.id)),
                )
            },
            photo = person.photo,
        )
    }
    Text(
        text = state.noShow.until?.let { stringResource(Res.string.game_no_show_footer, it) }
            ?: stringResource(Res.string.game_no_show_footer_closed),
        style = SaqzTheme.typography.caption,
        color = SaqzTheme.colors.textSecondary,
        modifier = Modifier
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.blockGap)
            .testTag(GameDetailTags.NoShowFooter),
    )
}

/**
 * Aba Na espera: "1º", "2º" na ordem do servidor (por posição no 5-1), o "Colocar" do gestor na escolha manual e
 * uma nota que explica como a fila anda.
 */
@Composable
internal fun GameWaitingList(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) {
    if (state.waitlist.isEmpty()) {
        EmptyNote(stringResource(Res.string.game_waitlist_empty), null, GameDetailTags.WaitlistEmpty)
        return
    }
    if (state.hasPositions) {
        val sections = state.positions.filter { it.slots > 0 }.sortedBy { it.position.ordinal }
        sections.forEach { section ->
            val people = state.waitlist.filter { it.athletePosition == section.position }
            if (people.isEmpty()) return@forEach
            PositionBand(section)
            people.forEachIndexed { index, person -> WaitingRow(state, person, index, index > 0, onIntent) }
        }
        val known = sections.map { it.position }.toSet()
        val others = state.waitlist.filter { it.athletePosition == null || it.athletePosition !in known }
        if (others.isNotEmpty()) {
            NoPositionBand()
            others.forEachIndexed { index, person -> WaitingRow(state, person, index, index > 0, onIntent) }
        }
    } else {
        state.waitlist.forEachIndexed { index, person -> WaitingRow(state, person, index, index > 0, onIntent) }
    }
    if (state.promotionFailed) {
        Text(
            text = stringResource(Res.string.game_waitlist_promotion_failed),
            color = SaqzTheme.colors.errorForeground,
            style = SaqzTheme.typography.support,
            modifier = Modifier.padding(start = NoteStart, end = SaqzTheme.metrics.horizontalPadding, top = 8.dp),
        )
    }
    ListNote(
        text = stringResource(
            when {
                state.header?.gameStarted == true -> Res.string.game_waitlist_reason_started
                state.promotionMode == PromotionMode.MANUAL && state.isAdmin -> Res.string.game_waitlist_note_organizer
                state.promotionMode == PromotionMode.MANUAL -> Res.string.game_waitlist_reason_manual
                else -> Res.string.game_waitlist_note_fifo
            },
        ),
    )
}

@Composable
private fun WaitingRow(
    state: GameDetailState,
    person: GameDetailWaitlistUi,
    index: Int,
    divider: Boolean,
    onIntent: (GameDetailIntent) -> Unit,
) {
    val queue = person.queuePosition?.toInt() ?: (index + 1)
    val reason = stringResource(Res.string.game_waitlist_promotion_reason)
    val section = state.positions.firstOrNull { it.position == person.athletePosition }
    val force = state.canForce(person)
    PersonRow(
        id = person.id,
        name = person.name,
        meta = personMeta(state, person.athletePosition, person.isMensalista, person.guest, confirmed = false),
        number = stringResource(Res.string.game_queue_ordinal, queue),
        isYou = person.isYou,
        guest = person.guest != null,
        divider = divider,
        hasActions = person.canRemove || person.guest?.canRemove == true || force,
        onIntent = onIntent,
        photo = person.photo,
        action = if (state.canPromote) {
            {
                // Posição completa: contorno, e o toque abre a confirmação de entrada forçada (fica acima das vagas).
                val full = section?.full == true
                SaqzButton(
                    label = stringResource(Res.string.game_waitlist_place),
                    onClick = {
                        if (full && force) {
                            onIntent(GameDetailIntent.RequestForceEntry(person.id))
                        } else {
                            onIntent(GameDetailIntent.Promote(person.guest?.hostId ?: person.id, reason, person.guest?.guestSeq ?: 0))
                        }
                    },
                    modifier = Modifier.testTag(GameWaitlistTags.promote(person.id)),
                    variant = if (full) SaqzButtonVariant.Secondary else SaqzButtonVariant.Primary,
                    size = SaqzButtonSize.Sm,
                    loading = state.promotingMemberId == person.id,
                    enabled = state.promotingMemberId == null,
                )
            }
        } else {
            null
        },
    )
}

/** Aba Não vão: só nomes, sem número nem ação; servidor antigo mostra só a contagem. */
@Composable
internal fun GameDeclinedList(state: GameDetailState) {
    val declined = state.declinedRoster
    when {
        declined == null -> EmptyNote(
            pluralStringResource(Res.plurals.game_declined_count, state.declinedCount, state.declinedCount),
            null,
            GameDetailTags.DeclinedCount,
        )
        declined.isEmpty() -> EmptyNote(
            stringResource(Res.string.game_declined_empty_title),
            stringResource(Res.string.game_declined_empty_body),
            GameDetailTags.DeclinedEmpty,
        )
        else -> declined.forEachIndexed { index, person ->
            PersonRow(
                id = person.id,
                name = person.name,
                meta = personMeta(state, person.position, person.isMensalista, null, confirmed = false),
                number = null,
                isYou = person.isYou,
                guest = false,
                divider = index > 0,
                hasActions = false,
                muted = true,
                onIntent = {},
                photo = person.photo,
            )
        }
    }
}

/**
 * Apoio da linha: o gestor vê o vínculo (é o que muda a cobrança); o atleta vê a posição, que no 5-1 a seção já
 * diz; o convidado, de quem ele é.
 */
@Composable
private fun personMeta(
    state: GameDetailState,
    position: AthletePosition?,
    mensalista: Boolean,
    guest: GameGuestRowUi?,
    confirmed: Boolean,
): String? = when {
    guest != null -> guest.metaLabel(state.guest.feeLabel, confirmed)
    state.isAdmin -> stringResource(
        if (mensalista) Res.string.group_member_membership_monthly else Res.string.group_member_membership_single_game,
    )
    state.hasPositions || position == null -> null
    else -> stringResource(position.positionResource())
}

@Composable
private fun PersonRow(
    id: String,
    name: String,
    meta: String?,
    number: String?,
    isYou: Boolean,
    guest: Boolean,
    divider: Boolean,
    hasActions: Boolean,
    onIntent: (GameDetailIntent) -> Unit,
    muted: Boolean = false,
    action: (@Composable () -> Unit)? = null,
    photo: MemberPhoto? = null,
) {
    val colors = SaqzTheme.colors
    SaqzMemberRow(
        name = name,
        meta = meta,
        leading = number?.let { { NumberCell(it) } },
        divider = divider,
        photo = if (guest) {
            { SaqzIcon(SaqzIcons.UserPlus, tint = colors.primary, size = GuestIconSize) }
        } else {
            photo.avatarSlot(name, initialsColor = if (muted) colors.textSecondary else colors.primary)
        },
        initialsColor = if (muted) colors.textSecondary else colors.primary,
        modifier = Modifier
            .testTag(if (guest) GameGuestTags.row(id) else GameDetailTags.row(id))
            .then(if (isYou) Modifier.background(colors.accent.copy(alpha = YouAlpha)) else Modifier),
        trailing = if (isYou || hasActions || action != null) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), verticalAlignment = Alignment.CenterVertically) {
                    if (isYou) SaqzStatusChip(stringResource(Res.string.home_waitlist_you), tone = SaqzChipTone.Accent)
                    action?.invoke()
                    if (hasActions) {
                        SaqzIconButton(
                            onClick = { onIntent(GameDetailIntent.OpenPersonActions(id)) },
                            contentDescription = stringResource(Res.string.game_person_actions, name),
                            modifier = Modifier.testTag(GameDetailTags.more(id)),
                        ) {
                            SaqzIcon(SaqzIcons.MoreVertical, tint = colors.textSecondary, size = MoreIconSize)
                        }
                    }
                }
            }
        } else {
            null
        },
    )
}

/** Linha tracejada azul de ação no fim da aba Vão ("Levar convidado", "Colocar alguém no jogo"). */
@Composable
private fun ActionRow(
    label: String,
    meta: String?,
    icon: ImageVector,
    divider: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    SaqzMemberRow(
        name = label,
        meta = meta,
        nameColor = SaqzTheme.colors.primary,
        leading = { NumberCell("") },
        avatar = { SaqzDashedCircle(SaqzTheme.colors.primary, icon = icon) },
        divider = divider,
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}

/** "1", "2º": a coluna de 26 à esquerda do avatar, na fonte do placar. */
@Composable
private fun NumberCell(text: String) = Text(
    text = text,
    style = SaqzTheme.typography.display.copy(fontSize = 14.sp, lineHeight = 16.sp),
    color = SaqzTheme.colors.textSecondary,
    textAlign = TextAlign.Center,
    maxLines = 1,
    modifier = Modifier.width(NumberWidth),
)

/** Faixa da posição: "Centrais" e "1/2" com "Falta 1", ou "2/2 · completo" com o check. */
@Composable
private fun PositionBand(row: GamePositionUi) {
    val colors = SaqzTheme.colors
    val name = if (row.slots == 1) row.position.singularLabel() else row.position.pluralLabel().replaceFirstChar { it.titlecase() }
    Band(name, Modifier.testTag(GamePositionTags.header(row.position))) {
        val count = "${row.confirmed}/${row.slots}"
        if (row.full) {
            Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid), verticalAlignment = Alignment.CenterVertically) {
                SaqzIcon(SaqzIcons.Check, tint = colors.successForeground, size = BandIconSize)
                Text(
                    text = stringResource(Res.string.game_position_complete, count),
                    style = bandMetaStyle(),
                    color = colors.successForeground,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), verticalAlignment = Alignment.CenterVertically) {
                Text(text = count, style = bandMetaStyle(), color = colors.textSecondary)
                SaqzStatusChip(
                    pluralStringResource(Res.plurals.game_position_missing_chip, row.missing, row.missing),
                    tone = SaqzChipTone.Warning,
                )
            }
        }
    }
}

@Composable
private fun NoPositionBand() = Band(stringResource(Res.string.position_without_group)) {}

@Composable
private fun Band(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SaqzTheme.colors.surfaceSoft)
            .heightIn(min = BandMinHeight)
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.grid),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = SaqzTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary,
        )
        trailing()
    }
}

@Composable
private fun bandMetaStyle() = SaqzTheme.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

/** Nota no fim da lista, alinhada na coluna do nome, com a divisória de cima. */
@Composable
private fun ListNote(text: String) {
    val line = SaqzTheme.colors.border
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = NoteStart)
            .drawBehind { drawRect(line, size = Size(size.width, 1.dp.toPx())) }
            .padding(top = SaqzTheme.metrics.blockGap, bottom = NoteBottom, end = SaqzTheme.metrics.horizontalPadding)
            .testTag(GameDetailTags.WaitlistNote),
    ) {
        Text(text = text, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
    }
}

/** Lista vazia: frase e apoio, centralizados no branco. */
@Composable
private fun EmptyNote(title: String, body: String?, tag: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.sectionGap)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        Text(
            text = title,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(text = body, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

private const val MAX_OPEN_ROWS = 3
private const val YouAlpha = 0.14f
private val NumberWidth = 26.dp
private val GuestIconSize = 18.dp
private val MoreIconSize = 20.dp
private val BandIconSize = 14.dp
private val BandMinHeight = 44.dp

/** 10 + 26 + 12 + 40 + 12: a coluna do nome. */
private val NoteStart = 100.dp
private val NoteBottom = 14.dp
