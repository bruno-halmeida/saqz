package br.com.saqz.subscriptions.domain.trial

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class TrialStatus {
    Available,
    Active,
    Expired,
    Ineligible,
    Subscribed,
}

/** Server-authoritative trial access, either for the authenticated owner or a group member. */
data class TrialAccess(
    val status: TrialStatus,
    val startedAt: String?,
    val endsAt: String?,
    val serverTime: String,
    val readOnly: Boolean,
    val canCreateGroup: Boolean,
    val maxGroups: Int,
    val maxAthletes: Int?,
    val isOwner: Boolean,
    val appUrl: String?,
    val offerMode: String = "ON",
    val canRedeemCoupon: Boolean = false,
    val selectedCouponCode: String? = null,
    val trialDays: Int = 14,
    val preauthorized: Boolean = false,
    /** Dias de calendário até o fim do teste (0 no último dia); só no teste ativo do dono. */
    val daysLeft: Int? = null,
    /** O que o dono já fez no teste; só no teste ativo do dono. */
    val usage: TrialUsage? = null,
    /** A folha de contagem já abriu hoje em algum aparelho do dono. */
    val countdownSeenToday: Boolean = false,
    /** Primeiro instante em que um jogo do grupo já não pode começar; só na rota do grupo, para dono e admins. */
    val gamesUntil: String? = null,
)

data class TrialUsage(val games: Int, val confirmedAthletes: Int, val cashCents: Long) {
    val isEmpty: Boolean get() = games == 0 && confirmedAthletes == 0 && cashCents == 0L
}

sealed interface TrialError : SaqzError {
    data object CouponUnavailable : TrialError
    data object OfferUnavailable : TrialError
    data object NotFound : TrialError
    data class Data(val error: DataError) : TrialError
}

interface TrialGateway {
    suspend fun applyCoupon(code: String): SaqzResult<TrialAccess, TrialError> = SaqzResult.Failure(TrialError.OfferUnavailable)

    suspend fun ownerTrial(): SaqzResult<TrialAccess, TrialError>

    /** Avisa o servidor de que a folha de contagem abriu hoje, para não abrir de novo no mesmo dia. */
    suspend fun markCountdownSeen(): SaqzResult<Unit, TrialError> = SaqzResult.Success(Unit)

    suspend fun groupTrial(groupId: GroupId): SaqzResult<TrialAccess, TrialError>
}
