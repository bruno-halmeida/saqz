package br.com.saqz.groups.presentation.ui.finance.review

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.finance.ReviewDecision

/** Uma pessoa da revisão (FIN-016): o estado escolhido e, no Já pagou, o meio. */
@Immutable
data class ReviewRowUi(
    val key: String,
    val memberId: String,
    val guestSeq: Int,
    val name: String,
    val hostName: String?,
    val noShow: Boolean,
    val leftGroup: Boolean,
    val decision: ReviewDecision,
    val method: PaidMethod = PaidMethod.Pix,
)

@Immutable
data class UnchargedReviewState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** "Terça, 20/10". */
    val gameDay: String = "",
    val venueName: String? = null,
    /** "20/10": a data em que o Já pagou entra no caixa. */
    val gameDate: String = "",
    val amountInput: String = "",
    val suggested: Boolean = false,
    val amountInvalid: Boolean = false,
    val rows: List<ReviewRowUi> = emptyList(),
    val menuKey: String? = null,
    /** A linha cujo menu "Como pagou" está aberto (DEB-152). */
    val methodMenuKey: String? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val chargeCount: Int get() = rows.count { it.decision == ReviewDecision.Charge }
    val paidCount: Int get() = rows.count { it.decision == ReviewDecision.Paid }
    val skipCount: Int get() = rows.count { it.decision == ReviewDecision.Skip }
}

sealed interface UnchargedReviewIntent {
    data object Retry : UnchargedReviewIntent
    data class AmountChanged(val value: String) : UnchargedReviewIntent
    data class OpenMenu(val key: String) : UnchargedReviewIntent
    data object DismissMenu : UnchargedReviewIntent
    data class Choose(val key: String, val decision: ReviewDecision) : UnchargedReviewIntent

    /** O toque no meio do Já pagou abre o menu "Como pagou" (DEB-152). */
    data class OpenMethodMenu(val key: String) : UnchargedReviewIntent
    data object DismissMethodMenu : UnchargedReviewIntent
    data class ChooseMethod(val key: String, val method: PaidMethod) : UnchargedReviewIntent
    data object Confirm : UnchargedReviewIntent
}

sealed interface UnchargedReviewEffect {
    /** Lote confirmado: volta ao acerto ou ao caixa, que recarregam. */
    data object Done : UnchargedReviewEffect
}

/** Um jogo da lista, quando a pendência do caixa tem mais de um. */
@Immutable
data class UnchargedGameUi(
    val gameId: String,
    val title: String,
    val subtitle: String,
    val peopleCount: Int,
)

@Immutable
data class UnchargedGamesState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val games: List<UnchargedGameUi> = emptyList(),
)

sealed interface UnchargedGamesIntent {
    data object Refresh : UnchargedGamesIntent
    data class Open(val gameId: String) : UnchargedGamesIntent
}

sealed interface UnchargedGamesEffect {
    data class OpenReview(val groupId: String, val gameId: String) : UnchargedGamesEffect
}
