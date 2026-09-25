package br.com.saqz.composeapp.notifications

import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onSuccess
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.TokenResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** O que a notificação mostra depois de responder presença sem abrir o app. */
enum class PushAttendanceOutcome { Confirmed, Waitlisted, Declined, Closed, Failed, NoResponse }

/** Botões "Confirmar" / "Não vou" do push: mesmo gateway (auth, retry, idempotência) da tela do jogo. */
class PushAttendance(
    private val gateway: AttendanceGateway,
    private val tokens: IdTokenProvider,
    private val scope: CoroutineScope,
) {
    /** [recipient] é o `firebase_subject` que o backend pôs no push; só o mesmo usuário logado responde. */
    @OptIn(ExperimentalUuidApi::class)
    fun respond(groupId: String, gameId: String, recipient: String, confirm: Boolean, done: (PushAttendanceOutcome) -> Unit) {
        val intent = if (confirm) AttendanceIntent.Confirm else AttendanceIntent.Decline
        scope.launch {
            // ponytail: o teto cabe na janela do goAsync do Android (~10 s); WorkManager se a rede pedir mais.
            val outcome = withTimeoutOrNull(RESPONSE_TIMEOUT_MS) {
                if (subject() != recipient) return@withTimeoutOrNull PushAttendanceOutcome.Failed
                gateway.respond(GroupId(groupId), gameId, SelfAttendanceCommand(Uuid.random().toString(), intent))
                    .onSuccess { SaqzAnalytics.attendanceAnswered("push", confirm) }
                    .toOutcome()
            }
            // Estourou o teto: o servidor pode ter gravado; não afirmar falha nem convidar a repetir.
            done(outcome ?: PushAttendanceOutcome.NoResponse)
        }
    }

    private suspend fun subject(): String? = suspendCancellableCoroutine { continuation ->
        tokens.token(forceRefresh = false) {
            if (continuation.isActive) continuation.resume((it as? TokenResult.Available)?.value?.jwtSubject())
        }
    }

    private companion object {
        const val RESPONSE_TIMEOUT_MS = 8_000L
    }
}

/** `sub` do ID token do Firebase, sem verificar assinatura: é só para não agir com a conta errada, o backend valida. */
@OptIn(ExperimentalEncodingApi::class)
internal fun String.jwtSubject(): String? = runCatching {
    val payload = split('.')[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
    Json.parseToJsonElement(Base64.UrlSafe.decode(payload).decodeToString()).jsonObject["sub"]?.jsonPrimitive?.content
}.getOrNull()

internal fun SaqzResult<VersionedAttendanceMutation, AttendanceError>.toOutcome(): PushAttendanceOutcome = when (this) {
    is SaqzResult.Success -> when (value.value.attendance.status) {
        AttendanceStatus.Confirmed -> PushAttendanceOutcome.Confirmed
        AttendanceStatus.Waitlisted -> PushAttendanceOutcome.Waitlisted
        AttendanceStatus.Declined -> PushAttendanceOutcome.Declined
    }
    is SaqzResult.Failure -> when (error) {
        AttendanceError.DeadlinePassed, AttendanceError.Frozen -> PushAttendanceOutcome.Closed
        else -> PushAttendanceOutcome.Failed
    }
}
