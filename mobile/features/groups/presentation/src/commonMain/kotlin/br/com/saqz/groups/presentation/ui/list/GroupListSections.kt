package br.com.saqz.groups.presentation.ui.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzDashedRow
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzPhotoButton
import br.com.saqz.designsystem.SaqzPhotoTag
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.resources.saqz_volleyball
import br.com.saqz.designsystem.saqzInitials
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.list.GroupCardAttendance
import br.com.saqz.groups.presentation.list.GroupCardGameUi
import br.com.saqz.groups.presentation.list.GroupCardUi
import br.com.saqz.groups.presentation.photo.GroupRemotePhotoRoot
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_confirm
import br.com.saqz.groups.resources.groups_confirm_sheet_no
import br.com.saqz.groups.resources.groups_confirm_sheet_title
import br.com.saqz.groups.resources.groups_confirm_sheet_yes
import br.com.saqz.groups.resources.groups_empty_create
import br.com.saqz.groups.resources.groups_empty_join
import br.com.saqz.groups.resources.groups_empty_owner_title
import br.com.saqz.groups.resources.groups_first_access_body
import br.com.saqz.groups.resources.groups_first_access_create
import br.com.saqz.groups.resources.groups_first_access_create_body
import br.com.saqz.groups.resources.groups_first_access_invite_body
import br.com.saqz.groups.resources.groups_first_access_kicker
import br.com.saqz.groups.resources.groups_first_access_paste
import br.com.saqz.groups.resources.groups_first_access_title
import br.com.saqz.groups.resources.groups_game_live
import br.com.saqz.groups.resources.groups_no_game
import br.com.saqz.groups.resources.groups_open_spots
import br.com.saqz.groups.resources.groups_role_admin
import br.com.saqz.groups.resources.groups_spots_full
import br.com.saqz.groups.resources.groups_spots_going
import br.com.saqz.groups.resources.groups_summary_awaiting
import br.com.saqz.groups.resources.groups_summary_count
import br.com.saqz.groups.resources.groups_summary_count_one
import br.com.saqz.groups.resources.groups_title
import br.com.saqz.groups.resources.home_response_error
import br.com.saqz.groups.resources.home_response_offline
import br.com.saqz.groups.resources.home_upcoming_status_going
import br.com.saqz.groups.resources.home_upcoming_status_out
import br.com.saqz.groups.resources.home_upcoming_status_waitlisted
import br.com.saqz.groups.resources.subscription_paused_athlete
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import br.com.saqz.designsystem.resources.Res as DesignRes

// Medidas da linha H do desenho (gerar.py: h_row, g_media, h_when, h_vagas_section) que não são token do fluxo 10.
private val HeaderTopPadding = 14.dp
private val HeaderBottomPadding = 10.dp
private val TitleSize = 26.sp
private val TightGap = 2.dp
private val CreateGlyph = 20.dp
private val PhotoWidth = 112.dp
private val RowMinHeight = 96.dp
private val PhotoTextGap = 14.dp
private val InitialsSize = 26.sp
private val BallSize = 124.dp
private val BallOffsetX = 42.dp
private val BallOffsetY = 38.dp
private const val BALL_ALPHA = 0.07f
private val PlaceGlyph = 13.dp
private val PlaceSize = 13.sp
private val PlaceLine = 18.sp
private val BadgeWidth = 29.dp
private val BadgeRadius = 6.dp
private val BadgeWeekdaySize = 6.5.sp
private val BadgeWeekdayLine = 8.sp
private val BadgeDaySize = 11.sp
private val BadgeDayLine = 13.sp
private val BadgeTimeSize = 7.5.sp
private val BadgeTimeLine = 9.sp
private val SpotsRadius = 10.dp
private val SpotsPaddingX = 12.dp
private val SpotsPaddingY = 10.dp
private val SpotsGap = 6.dp
private val SlotHeight = 4.dp
private val SlotRadius = 2.dp
private val SlotGap = 2.dp
private val SpotsTextSize = 13.sp
private val SpotsTextLine = 16.sp
private val SheetPhoto = 48.dp
private val SheetPhotoRadius = 12.dp
private val SheetNameSize = 17.sp
private val SheetQuestionSize = 18.sp
private val SkeletonTitleWidth = 150.dp
private val SkeletonMetaWidth = 110.dp
private val SkeletonMetaHeight = 12.dp
private const val SKELETON_ROWS = 3

/** Cabeçalho de 2n. [onCreate] nulo esconde o "+", que é como 2o se desenha. */
@Composable
internal fun GroupListHeader(
    groupCount: Int,
    awaitingConfirmation: Int,
    onCreate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SaqzTheme.metrics.horizontalPadding,
                end = SaqzTheme.metrics.horizontalPadding,
                top = HeaderTopPadding,
                bottom = HeaderBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(TightGap),
    ) {
        // O "+" fica na linha do título e o resumo embaixo, na largura toda: ao lado do botão ele quebrava.
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = SaqzTheme.metrics.iconButtonSize),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            Text(
                text = stringResource(Res.string.groups_title),
                // headline já carrega o letter-spacing de -0.03em do export.
                style = SaqzTheme.typography.headline.copy(fontSize = TitleSize, fontWeight = FontWeight(800)),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (onCreate != null) {
                SaqzIconButton(
                    onClick = onCreate,
                    contentDescription = stringResource(Res.string.groups_empty_create),
                    outlined = true,
                    modifier = Modifier.testTag(GroupListTags.Create),
                ) {
                    SaqzIcon(SaqzIcons.Plus, tint = colors.primary, size = CreateGlyph)
                }
            }
        }
        if (groupCount > 0) {
            val count = stringResource(
                if (groupCount == 1) Res.string.groups_summary_count_one else Res.string.groups_summary_count,
                groupCount,
            )
            Text(
                text = if (awaitingConfirmation > 0) {
                    stringResource(Res.string.groups_summary_awaiting, count, awaitingConfirmation)
                } else {
                    count
                },
                style = SaqzTheme.typography.support,
                color = colors.textSecondary,
                modifier = Modifier.testTag(GroupListTags.Summary),
            )
        }
    }
}

/**
 * Um grupo: faixa branca de ponta a ponta com a foto sangrada à esquerda (etiquetas por cima) e, à direita, nome, onde,
 * o selo do dia e as vagas. A faixa inteira abre o grupo; o "Confirmar" da foto abre a folha.
 */
@Composable
internal fun GroupRow(
    group: GroupCardUi,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .groupRowRules(colors.border)
            .clickable(onClickLabel = group.name, role = Role.Button, onClick = onOpen)
            .testTag(GroupListTags.group(group.id)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .padding(
                    start = PhotoWidth + PhotoTextGap,
                    end = metrics.horizontalPadding,
                    top = metrics.horizontalPadding,
                    bottom = metrics.horizontalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(metrics.horizontalPadding),
        ) {
            val game = group.nextGame
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TightGap)) {
                    Text(
                        text = group.name,
                        style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
                        color = colors.textPrimary,
                    )
                    group.place?.let { GroupPlaceLine(it) }
                }
                if (game != null) GroupCalendarBadge(game)
            }
            if (game != null) {
                GroupSpots(game)
            } else {
                Text(
                    text = stringResource(Res.string.groups_no_game),
                    style = SaqzTheme.typography.support,
                    color = colors.textSecondary,
                )
            }
        }
        // Depois do texto: a foto e as etiquetas desenham por cima e o "Confirmar" recebe o toque antes da faixa.
        Box(Modifier.matchParentSize()) {
            GroupRowPhoto(
                group = group,
                onConfirm = onConfirm,
                modifier = Modifier.width(PhotoWidth).fillMaxHeight(),
            )
        }
    }
}

/** Fio em cima e embaixo da faixa, por cima da foto (no desenho a borda é do contêiner). */
internal fun Modifier.groupRowRules(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
    drawLine(color, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
}

@Composable
private fun GroupRowPhoto(group: GroupCardUi, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val inset = SaqzTheme.metrics.grid
    Box(modifier = modifier) {
        GroupRemotePhotoRoot(photoUrl = group.photoUrl, modifier = Modifier.fillMaxSize()) {
            GroupInitialsTile(name = group.name, modifier = Modifier.fillMaxSize())
        }
        if (group.isAdmin) {
            SaqzPhotoTag(
                text = stringResource(Res.string.groups_role_admin),
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.TopStart).padding(inset).testTag(GroupListTags.admin(group.id)),
            )
        }
        val game = group.nextGame ?: return@Box
        when (game.attendance) {
            // A pílula visível fica a 8 da base; os 8 que sobram do alvo de 44 são o respiro transparente embaixo.
            GroupCardAttendance.Pending -> SaqzPhotoButton(
                label = stringResource(Res.string.groups_confirm),
                onClick = onConfirm,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = inset)
                    .testTag(GroupListTags.confirm(group.id)),
            )
            else -> GroupAttendanceTag(
                attendance = game.attendance,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(inset)
                    .testTag(GroupListTags.status(group.id)),
            )
        }
    }
}

@Composable
private fun GroupAttendanceTag(attendance: GroupCardAttendance, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    // Mesmos rótulos da lista "Próximos jogos" da Início.
    val (text, color, dot) = when (attendance) {
        GroupCardAttendance.Going -> Triple(Res.string.home_upcoming_status_going, colors.successForeground, colors.success)
        GroupCardAttendance.Waitlisted ->
            Triple(Res.string.home_upcoming_status_waitlisted, colors.warningForeground, colors.warning)
        GroupCardAttendance.Out, GroupCardAttendance.Pending ->
            Triple(Res.string.home_upcoming_status_out, colors.textSecondary, colors.textPlaceholder)
    }
    SaqzPhotoTag(text = stringResource(text), color = color, dot = dot, modifier = modifier)
}

/** Sem foto: fundo claro, as iniciais em Anybody e a bola do Saqz ao fundo; o fio à direita separa do texto. */
@Composable
internal fun GroupInitialsTile(
    name: String,
    modifier: Modifier = Modifier,
    textSize: TextUnit = InitialsSize,
) {
    val colors = SaqzTheme.colors
    Box(
        modifier = modifier
            .background(colors.surfaceSoft)
            .drawWithContent {
                drawContent()
                val stroke = 1.dp.toPx()
                drawLine(colors.border, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
            }
            .clip(RectangleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(DesignRes.drawable.saqz_volleyball),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.primary),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = BallOffsetX, y = BallOffsetY)
                .size(BallSize)
                .alpha(BALL_ALPHA),
        )
        Text(
            text = saqzInitials(name),
            style = SaqzTheme.typography.display.copy(fontSize = textSize, lineHeight = textSize),
            color = colors.primary,
        )
    }
}

/** "CERET — Quadra 2" com o pino. A quadra não se separa: o nome longo só quebra no travessão. */
@Composable
internal fun GroupPlaceLine(place: String, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val text = place.split(" — ", limit = 2).let { parts ->
        if (parts.size == 2) "${parts[0]} — ${parts[1].replace(' ', ' ')}" else place
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        SaqzIcon(SaqzIcons.Pin, tint = colors.textSecondary, size = PlaceGlyph, modifier = Modifier.padding(top = TightGap))
        Text(
            text = text,
            style = SaqzTheme.typography.support.copy(fontSize = PlaceSize, lineHeight = PlaceLine),
            color = colors.textSecondary,
        )
    }
}

/** Selo de calendário: faixa azul com o dia da semana (ou "AGORA" em lima), o dia em Anybody e o horário. */
@Composable
internal fun GroupCalendarBadge(game: GroupCardGameUi, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val shape = RoundedCornerShape(BadgeRadius)
    // Selo de 29 dp fixos: o texto não cresce com a fonte do sistema (quebrava "19h/30"); dia e hora seguem no leitor de tela.
    val unscale = 1f / LocalDensity.current.fontScale
    Column(
        modifier = modifier
            .width(BadgeWidth)
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .testTag(GroupListTags.Badge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (game.live) stringResource(Res.string.groups_game_live) else game.weekday,
            style = SaqzTheme.typography.caption.copy(
                fontSize = BadgeWeekdaySize * unscale,
                lineHeight = BadgeWeekdayLine * unscale,
                fontWeight = FontWeight.Bold,
            ),
            color = if (game.live) colors.accent else colors.onPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(colors.primary).padding(vertical = 1.dp),
        )
        Text(
            text = game.day,
            style = SaqzTheme.typography.display.copy(fontSize = BadgeDaySize * unscale, lineHeight = BadgeDayLine * unscale),
            color = colors.textPrimary,
            modifier = Modifier.padding(top = TightGap),
        )
        Text(
            text = game.time,
            style = SaqzTheme.typography.caption.copy(
                fontSize = BadgeTimeSize * unscale,
                lineHeight = BadgeTimeLine * unscale,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
            modifier = Modifier.padding(start = TightGap, end = TightGap, bottom = TightGap),
        )
    }
}

/**
 * As vagas como bloco próprio: um traço por vaga e, embaixo, "14 de 18 vão" e "4 vagas abertas" (ou "Completo"). Acima
 * da lotação, só "13 confirmados para 12 vagas", na linha toda.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupSpots(game: GroupCardGameUi, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val countStyle = SaqzTheme.typography.support.copy(fontSize = SpotsTextSize, lineHeight = SpotsTextLine)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceSoft, RoundedCornerShape(SpotsRadius))
            .padding(horizontal = SpotsPaddingX, vertical = SpotsPaddingY)
            .testTag(GroupListTags.Spots),
        verticalArrangement = Arrangement.spacedBy(SpotsGap),
    ) {
        if (game.capacity > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(SlotGap),
            ) {
                repeat(game.capacity) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(SlotHeight)
                            .background(
                                if (index < game.confirmedCount) colors.primary else colors.border,
                                RoundedCornerShape(SlotRadius),
                            ),
                    )
                }
            }
        }
        val over = game.overCapacityLabel
        if (over != null) {
            Text(text = over.boldLeadingNumber(colors.textPrimary), style = countStyle, color = colors.textSecondary)
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(TightGap),
            ) {
                Text(
                    text = stringResource(Res.string.groups_spots_going, game.confirmedCount, game.capacity)
                        .boldLeadingNumber(colors.textPrimary),
                    style = countStyle,
                    color = colors.textSecondary,
                )
                Text(
                    text = if (game.openSpots > 0) {
                        pluralStringResource(Res.plurals.groups_open_spots, game.openSpots, game.openSpots)
                    } else {
                        stringResource(Res.string.groups_spots_full)
                    },
                    style = countStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = if (game.openSpots > 0) colors.primary else colors.textSecondary,
                )
            }
        }
    }
}

/** "14 de 18 vão" com o "14" em semibold marinho: o número é o que se lê primeiro. */
private fun String.boldLeadingNumber(color: Color): AnnotatedString {
    val end = indexOfFirst { !it.isDigit() }.let { if (it < 0) length else it }
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = color)) { append(this@boldLeadingNumber.take(end)) }
        append(this@boldLeadingNumber.drop(end))
    }
}

/**
 * A folha do "Confirmar": foto, nome, onde e o selo; as vagas; "Você vai nesse jogo?" e Vou / Não vou. Tocar fora fecha
 * sem responder. Falha mostra o aviso da Início e a folha fica.
 */
@Composable
internal fun GroupConfirmSheet(
    group: GroupCardUi?,
    responding: Boolean,
    errorText: String?,
    onRespond: (AttendanceIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    // Vou e Não vou no rodapé da folha: fica sempre à vista e o resumo do jogo rola por dentro se não couber.
    SaqzBottomSheet(
        open = group != null,
        onClose = onClose,
        modifier = modifier,
        splitFooter = {
            SaqzButton(
                label = stringResource(Res.string.groups_confirm_sheet_yes),
                onClick = { onRespond(AttendanceIntent.Confirm) },
                enabled = !responding,
                fullWidth = true,
                modifier = Modifier.weight(1f).testTag(GroupListTags.ConfirmYes),
            )
            SaqzButton(
                label = stringResource(Res.string.groups_confirm_sheet_no),
                onClick = { onRespond(AttendanceIntent.Decline) },
                variant = SaqzButtonVariant.Secondary,
                enabled = !responding,
                fullWidth = true,
                modifier = Modifier.weight(1f).testTag(GroupListTags.ConfirmNo),
            )
        },
    ) {
        val game = group?.nextGame ?: return@SaqzBottomSheet
        Column(
            modifier = Modifier.fillMaxWidth().testTag(GroupListTags.ConfirmSheet),
            verticalArrangement = Arrangement.spacedBy(metrics.horizontalPadding),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                // O fallback da foto ocupa o que o pai der: o Box fixa os 48x48 para foto e iniciais.
                Box(Modifier.size(SheetPhoto).clip(RoundedCornerShape(SheetPhotoRadius))) {
                    GroupRemotePhotoRoot(photoUrl = group.photoUrl, modifier = Modifier.fillMaxSize()) {
                        GroupInitialsTile(name = group.name, modifier = Modifier.fillMaxSize(), textSize = SheetNameSize)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TightGap)) {
                    Text(
                        text = group.name,
                        style = SaqzTheme.typography.subtitle.copy(fontSize = SheetNameSize, lineHeight = 22.sp),
                        color = colors.textPrimary,
                    )
                    group.place?.let { GroupPlaceLine(it) }
                }
                GroupCalendarBadge(game)
            }
            GroupSpots(game)
            Text(
                text = stringResource(Res.string.groups_confirm_sheet_title),
                style = SaqzTheme.typography.subtitle.copy(fontSize = SheetQuestionSize, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.padding(top = metrics.subGrid),
            )
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = SaqzTheme.typography.support,
                    color = colors.errorForeground,
                    modifier = Modifier.testTag(GroupListTags.ConfirmError),
                )
            }
        }
    }
}

/** O aviso da Início para a resposta que falhou: sem conexão, grupo congelado ou o erro geral. */
@Composable
internal fun groupResponseErrorText(failed: Boolean, planPaused: Boolean, offline: Boolean): String? = when {
    offline -> stringResource(Res.string.home_response_offline)
    planPaused -> stringResource(Res.string.subscription_paused_athlete)
    failed -> stringResource(Res.string.home_response_error)
    else -> null
}

/**
 * 2o — primeiro acesso. Atleta: o azul é entrar pelo convite e a linha tracejada é criar um grupo. Dono com plano
 * ativo ([isPlanOwner]): o inverso. Destaque é o azul da marca com o botão branco; nada de botão lima.
 */
@Composable
internal fun GroupListEmpty(
    onCreate: () -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
    isPlanOwner: Boolean = false,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = metrics.horizontalPadding, end = metrics.horizontalPadding, top = metrics.grid)
            .testTag(GroupListTags.Empty),
        verticalArrangement = Arrangement.spacedBy(metrics.horizontalPadding),
    ) {
        if (isPlanOwner) {
            SaqzHeroCard(kicker = null, title = stringResource(Res.string.groups_empty_owner_title)) {
                SaqzButton(
                    label = stringResource(Res.string.groups_empty_create),
                    onClick = onCreate,
                    variant = SaqzButtonVariant.Inverse,
                    fullWidth = true,
                    modifier = Modifier.padding(top = metrics.subGrid).testTag(GroupListTags.EmptyCreate),
                )
            }
            SaqzDashedRow(
                icon = SaqzIcons.UserPlus,
                label = stringResource(Res.string.groups_empty_join),
                supporting = stringResource(Res.string.groups_first_access_invite_body),
                onClick = onInvite,
                modifier = Modifier.testTag(GroupListTags.EmptyInvite),
            )
        } else {
            SaqzHeroCard(
                kicker = stringResource(Res.string.groups_first_access_kicker),
                title = stringResource(Res.string.groups_first_access_title),
                meta = stringResource(Res.string.groups_first_access_body),
            ) {
                SaqzButton(
                    label = stringResource(Res.string.groups_first_access_paste),
                    onClick = onInvite,
                    variant = SaqzButtonVariant.Inverse,
                    fullWidth = true,
                    modifier = Modifier.padding(top = metrics.subGrid).testTag(GroupListTags.EmptyInvite),
                )
            }
            SaqzDashedRow(
                icon = SaqzIcons.Plus,
                label = stringResource(Res.string.groups_first_access_create),
                supporting = stringResource(Res.string.groups_first_access_create_body),
                onClick = onCreate,
                modifier = Modifier.testTag(GroupListTags.EmptyCreate),
            )
        }
    }
}

/** Falha de carga não está desenhada: vazio com o erro e a saída de `Retry`. */
@Composable
internal fun GroupListFailure(
    error: GroupUiError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupLoadFailure(error, onRetry, modifier.fillMaxSize(), GroupListTags.Failure)
}

/** Carregando: três faixas na forma da linha nova (foto sangrada e duas linhas de texto). */
@Composable
internal fun GroupListSkeleton(modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier.fillMaxWidth().padding(top = metrics.subGrid),
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        repeat(SKELETON_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.surface).groupRowRules(colors.border),
                horizontalArrangement = Arrangement.spacedBy(PhotoTextGap),
            ) {
                SaqzSkeleton(width = PhotoWidth, height = RowMinHeight + metrics.sectionGap, radius = 0.dp)
                Column(
                    modifier = Modifier.padding(vertical = metrics.horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(metrics.grid),
                ) {
                    SaqzSkeleton(width = SkeletonTitleWidth)
                    SaqzSkeleton(width = SkeletonMetaWidth, height = SkeletonMetaHeight)
                }
            }
        }
    }
}
