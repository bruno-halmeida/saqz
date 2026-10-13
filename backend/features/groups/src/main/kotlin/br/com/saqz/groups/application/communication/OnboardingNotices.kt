package br.com.saqz.groups.application.communication

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Aviso ao dono e admins quando alguém entra no grupo por conta própria (canal FIRST_MEMBER). Chamado na
 * mesma transação da entrada; só vale para o primeiro atleta do grupo e nunca pode quebrar a entrada.
 */
fun interface FirstMemberNotice {
    fun joined(groupId: UUID, userId: UUID)
}

/**
 * Aviso ao dono e admins na primeira confirmação de presença de um não-dono (canal FIRST_CONFIRMATION).
 * Chamado na mesma transação da resposta; [eventId] é o evento recém-gravado, que não conta como "anterior".
 */
fun interface FirstConfirmationNotice {
    fun confirmed(groupId: UUID, gameId: UUID, memberId: UUID, eventId: UUID)
}

fun firstMemberBody(who: String, groupName: String): String =
    "$who entrou no $groupName. A galera começou a chegar."

fun firstConfirmationBody(who: String, localDate: LocalDate, localTime: LocalTime): String =
    "$who confirmou presença no jogo de ${gameSchedule(localDate, localTime)}. A lista começou a fechar sozinha."
