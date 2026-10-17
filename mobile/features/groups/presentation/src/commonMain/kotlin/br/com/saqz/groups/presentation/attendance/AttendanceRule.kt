package br.com.saqz.groups.presentation.attendance

import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import kotlin.time.Instant

/**
 * Regra de presença do grupo (decisão de 13/10/2026), espelho da política do servidor, que tem a
 * palavra final. Serve à previsão otimista do "Vou" e ao motivo mostrado quando a resposta cai na espera:
 * - depois do prazo, em ordem de chegada o "Vou" confirma se houver vaga e senão espera; em escolha manual espera sempre;
 * - antes do prazo, em ordem de chegada: com prioridade o avulso espera; sem ela, todos entram se houver vaga;
 * - antes do prazo, em escolha manual: com prioridade só o mensalista entra direto; sem ela, todos esperam.
 * Jogo com prazo no início (marcado em cima da hora, DEB-045) não tem janela de prioridade: vale [priorityWindow].
 */
internal object AttendanceRule {
    /** A prioridade do grupo só vale em jogo cujo prazo vem antes do início; senão todos entram por ordem de chegada. */
    fun priorityWindow(mensalistaPriority: Boolean, confirmationDeadline: Instant?, startsAt: Instant?): Boolean =
        mensalistaPriority && (confirmationDeadline == null || startsAt == null || confirmationDeadline < startsAt)

    /** Mesma regra com os instantes ainda em texto ISO (modelos da Início e do grupo); texto ilegível mantém a prioridade. */
    fun priorityWindow(mensalistaPriority: Boolean, confirmationDeadline: String, startsAt: String): Boolean =
        priorityWindow(
            mensalistaPriority,
            runCatching { Instant.parse(confirmationDeadline) }.getOrNull(),
            runCatching { Instant.parse(startsAt) }.getOrNull(),
        )

    fun entersDirectly(
        membershipType: AthleteMembershipType?,
        mensalistaPriority: Boolean,
        promotionMode: PromotionMode,
        deadlinePassed: Boolean,
    ): Boolean {
        // Depois do prazo o "Vou" passa pela espera e a promoção segue o grupo: em ordem de chegada o
        // servidor sobe na hora se houver vaga (a previsão só falta olhar a lotação); manual espera o gestor.
        if (deadlinePassed) return promotionMode == PromotionMode.FIFO
        val mensalista = membershipType != AthleteMembershipType.AVULSO
        return when (promotionMode) {
            PromotionMode.FIFO -> mensalista || !mensalistaPriority
            PromotionMode.MANUAL -> mensalista && mensalistaPriority
        }
    }

    /** Previsão do status depois do toque; recusar é sempre recusa e quem já está confirmado continua. */
    fun predict(
        intent: AttendanceIntent,
        current: AttendanceStatus?,
        membershipType: AthleteMembershipType?,
        mensalistaPriority: Boolean,
        promotionMode: PromotionMode,
        deadlinePassed: Boolean,
        full: Boolean,
    ): AttendanceStatus = when {
        intent == AttendanceIntent.Decline -> AttendanceStatus.Declined
        current == AttendanceStatus.Confirmed -> AttendanceStatus.Confirmed
        !entersDirectly(membershipType, mensalistaPriority, promotionMode, deadlinePassed) -> AttendanceStatus.Waitlisted
        full -> AttendanceStatus.Waitlisted
        else -> AttendanceStatus.Confirmed
    }

    /** Por que a pessoa está na espera: define a explicação mostrada no hero e no aviso. */
    fun waitlistKind(
        membershipType: AthleteMembershipType?,
        mensalistaPriority: Boolean,
        promotionMode: PromotionMode,
        deadlinePassed: Boolean,
    ): HomeWaitlistKind = when {
        promotionMode == PromotionMode.MANUAL -> HomeWaitlistKind.Manual
        deadlinePassed -> HomeWaitlistKind.AfterDeadline
        membershipType == AthleteMembershipType.AVULSO && mensalistaPriority -> HomeWaitlistKind.AvulsoList
        else -> HomeWaitlistKind.Reserva
    }
}
