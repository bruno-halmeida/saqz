package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gamedetail.GameDetailHeaderUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceControls
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTags
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTexts
import br.com.saqz.groups.presentation.ui.components.HeroResponseLine
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_detail_confirmation_deadline
import br.com.saqz.groups.resources.game_detail_response_kicker
import br.com.saqz.groups.resources.game_response_confirmed
import br.com.saqz.groups.presentation.ui.components.lateNoticeText
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.resources.game_response_declined
import br.com.saqz.groups.resources.game_response_no
import br.com.saqz.groups.resources.game_response_answered_title
import br.com.saqz.groups.resources.game_response_question
import br.com.saqz.groups.resources.game_response_request_failed
import br.com.saqz.groups.resources.subscription_paused_athlete
import br.com.saqz.groups.resources.game_response_yes
import br.com.saqz.groups.resources.home_attendance_cancel
import br.com.saqz.groups.resources.home_attendance_change
import org.jetbrains.compose.resources.stringResource

internal object GameDetailAttendanceTags {
    const val Block = "game-detail-attendance"
    const val Going = "game-detail-attendance-going"
    const val NotGoing = "game-detail-attendance-not-going"
    const val Change = "game-detail-attendance-change"
    const val Cancel = "game-detail-attendance-cancel"
    const val Error = "game-detail-attendance-error"
}

/**
 * A resposta de presença na tela do jogo — o único lugar para responder a um jogo que não é o
 * próximo (a Início e o grupo só mostram o próximo). Mesmo cartão azul e mesmos controles do hero
 * (`HeroAttendanceControls`), sem o "Ver o jogo" da espera: este já é o jogo. Some quando o jogo
 * não está publicado.
 */
@Composable
internal fun GameDetailResponse(
    header: GameDetailHeaderUi,
    response: GameDetailResponseUi,
    onIntent: (GameDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
    spotsOpen: Boolean = false,
    promotionMode: PromotionMode = PromotionMode.FIFO,
) {
    // O estado já só liga `visible` em jogo publicado; o tom do cabeçalho fecha a porta se os dois divergirem.
    if (!response.visible || header.statusTone != GameDetailStatusTone.Published) return
    SaqzHeroCard(
        kicker = stringResource(Res.string.game_detail_response_kicker),
        // Respondido: o estado abaixo já diz o que vale; perguntar de novo contradiz ("Você não vai jogar.").
        title = stringResource(
            if (response.status == null) Res.string.game_response_question else Res.string.game_response_answered_title,
        ),
        modifier = modifier.testTag(GameDetailAttendanceTags.Block),
    ) {
        HeroResponseLine(
            confirmationOpen = header.confirmationOpen,
            responseOpen = header.responseOpen,
            closedByDeadline = header.closedByDeadline,
            openText = stringResource(Res.string.game_detail_confirmation_deadline, header.deadlineLabel()),
        )
        HeroAttendanceControls(
            status = response.status,
            confirmationOpen = header.canRespond,
            lateNotice = if (!header.confirmationOpen) lateNoticeText(promotionMode) else null,
            responding = response.responding,
            responseFailed = response.failed || response.planPaused,
            responseOffline = response.offline,
            waitlistKind = response.waitlistKind,
            spotsOpen = spotsOpen,
            waitlistPosition = response.waitlistPosition,
            onRespond = { onIntent(GameDetailIntent.Respond(it)) },
            onViewGame = null,
            texts = HeroAttendanceTexts(
                yes = stringResource(Res.string.game_response_yes),
                no = stringResource(Res.string.game_response_no),
                confirmed = stringResource(Res.string.game_response_confirmed),
                declined = stringResource(Res.string.game_response_declined),
                change = stringResource(Res.string.home_attendance_change),
                cancel = stringResource(Res.string.home_attendance_cancel),
                error = stringResource(
                    if (response.planPaused) Res.string.subscription_paused_athlete else Res.string.game_response_request_failed,
                ),
            ),
            tags = GameDetailHeroAttendanceTags,
        )
    }
}

private val GameDetailHeroAttendanceTags = HeroAttendanceTags(
    yes = GameDetailAttendanceTags.Going,
    no = GameDetailAttendanceTags.NotGoing,
    change = GameDetailAttendanceTags.Change,
    cancel = GameDetailAttendanceTags.Cancel,
    error = GameDetailAttendanceTags.Error,
)

@Preview
@Composable
private fun GameDetailResponsePendingPreview() = SaqzTheme {
    GameDetailResponse(GameDetailPreviewData.header, GameDetailResponsePreviewData.pending, {})
}
