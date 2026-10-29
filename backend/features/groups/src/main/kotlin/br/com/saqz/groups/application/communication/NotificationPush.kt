package br.com.saqz.groups.application.communication

import java.time.Instant
import java.util.UUID

enum class PushDelivery { SENT, INVALID_TOKEN, INVALID_START_TOKEN, RETRY }

/** Jogo da janela de presença no formato do card da Live Activity (contrato com `SaqzGameAttributes`, VUL-268). */
data class LiveActivityGame(val venue: String, val startsAt: Instant, val confirmed: Int, val capacity: Int, val waitlisted: Int)

/** [recipient] é o `firebase_subject` de quem recebe: o app só age no push se for o usuário logado. */
data class NotificationPush(
    val notificationId: Long, val groupId: UUID, val title: String, val body: String, val channel: String, val recipient: String,
    val gameId: UUID? = null,
    /** O jogo tem vagas por posição (5-1, DEB-107): o "Vou" do push abre a tela de escolher posição nos apps 1.0.10+. */
    val positions: Boolean = false,
    /** Fim da janela de presença das 24 h: o app expira a notificação nessa hora. */
    val windowEndsAt: Instant? = null,
    /** Só na janela de presença: os dados do card da Live Activity. */
    val liveActivity: LiveActivityGame? = null,
    /** Token de push-to-start do iPhone que vai receber; com [liveActivity], o push abre a Live Activity. */
    val liveActivityStartToken: String? = null,
    /** Agrupa no celular os avisos de pagamento do mesmo grupo (FIN-014: "um por pagamento, que o celular agrupa"). */
    val thread: String? = null,
    /** Destino do toque nos avisos de cobrança do app (FIN-013): o pagamento daquele jogo ou daquela mensalidade. */
    val settleGameId: UUID? = null,
    val settleBillingMonth: java.time.LocalDate? = null,
    /** Tipo do aviso do recebimento (`paymentKind`, V200): aditivo, o app antigo ignora. */
    val paymentKind: String? = null,
    /** Marca do evento (`event_kind`): `GAME_FEE_UNDEFINED` abre o "Valor por jogo" no app novo (DEB-146). */
    val eventKind: String? = null,
)
fun interface NotificationPushSender { fun send(token: String, message: NotificationPush): PushDelivery }
