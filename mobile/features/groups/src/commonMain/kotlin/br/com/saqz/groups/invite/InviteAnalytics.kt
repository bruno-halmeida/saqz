package br.com.saqz.groups.invite

import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.groups.domain.membership.InviteError

/** Em que chamada o convite falhou: a prévia ou o resgate. */
enum class InviteStep(internal val value: String) { Preview("preview"), Redeem("redeem") }

/** Por onde o convite saiu do app. O valor é texto fixo, que o R8 não troca como faz com nome de classe. */
enum class InviteShareMethod(internal val value: String) {
    CopyLink("copy_link"),
    CopyCode("copy_code"),
    Message("message"),
    Qr("qr"),
    QrSaved("qr_saved"),
    MonthlyLink("monthly_link"),
}

/**
 * Evento `invite_shared` (Firebase): o convite foi copiado ou entregue ao compartilhamento do aparelho. Confirma que
 * saiu do app, não que a mensagem chegou. Sem código de convite e sem id de grupo: só a forma usada.
 */
fun trackInviteShared(method: InviteShareMethod) {
    SaqzAnalytics.event("invite_shared", "method" to method.value)
}

/**
 * Evento `invite_invalid` (Firebase): por que um convite foi recusado por regra do servidor. Falha de rede não
 * entra. Sem código de convite, sem id de grupo ou de usuário: só o motivo, o `code` do backend e o passo.
 */
fun trackInviteFailure(error: InviteError, step: InviteStep) {
    val (reason, code) = when (error) {
        is InviteError.InvalidOrExpired -> "invalid" to error.code
        is InviteError.Expired -> "expired" to "INVITE_EXPIRED"
        InviteError.GroupDeleted -> "group_deleted" to "INVITE_GROUP_DELETED"
        is InviteError.RateLimited -> "attempt_limit" to "INVITE_ATTEMPT_LIMIT"
        InviteError.PlanLimit -> "athlete_limit" to "ATHLETE_LIMIT_EXCEEDED"
        InviteError.GroupNotReceiving -> "not_receiving" to "SUBSCRIPTION_REQUIRED"
        is InviteError.DataFailure -> return
    }
    SaqzAnalytics.event("invite_invalid", "reason" to reason, "error_code" to code, "step" to step.value)
}
