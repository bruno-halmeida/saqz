package br.com.saqz.groups.application.communication

import java.time.Instant
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

/**
 * Aviso ao dono e admins quando alguém pede para entrar num grupo com "Aprovar pedidos para entrar" (canal
 * ENTRY_REQUEST). Chamado na mesma transação do pedido; pedido repetido (ainda pendente) não gera outro aviso.
 */
fun interface EntryRequestNotice {
    fun requested(groupId: UUID, userId: UUID)
}

/**
 * Aviso direto à pessoa cujo pedido de entrada foi aprovado (CONV-007), no canal AUTO_CONFIRMATION que os apps das
 * lojas já conhecem. Chamado na mesma transação da aprovação; [requestedAt] identifica o pedido, então aprovar de
 * novo o mesmo pedido não avisa duas vezes (um pedido novo, depois de sair de novo, avisa).
 */
fun interface EntryApprovalNotice {
    fun approved(groupId: UUID, userId: UUID, requestedAt: Instant)
}

/**
 * Aviso direto à pessoa promovida a administrador (MEM-001 / DEB-060), no canal AUTO_CONFIRMATION que os apps das
 * lojas já conhecem. Chamado na mesma transação da troca de papel; quem é rebaixado não recebe aviso.
 */
fun interface AdministratorPromotionNotice {
    fun promoted(groupId: UUID, userId: UUID)
}

fun administratorPromotedBody(groupName: String): String = "Você agora é administrador de $groupName."

fun entryApprovedBody(groupName: String): String = "Você entrou no $groupName."

fun entryRequestBody(who: String, groupName: String): String = "$who pediu para entrar no $groupName."

fun firstMemberBody(who: String, groupName: String): String =
    "$who entrou no $groupName. A galera começou a chegar."

fun firstConfirmationBody(who: String, localDate: LocalDate, localTime: LocalTime): String =
    "$who confirmou presença no jogo de ${gameSchedule(localDate, localTime)}. A lista começou a fechar sozinha."

/** Aviso direto de mudança de presença; [guestName] preenchido quando o aviso é do anfitrião sobre um convidado. */
fun attendanceConfirmedBody(localDate: LocalDate, localTime: LocalTime, venue: String, guestName: String? = null): String =
    (if (guestName == null) "Sua vaga está confirmada" else "A vaga de $guestName está confirmada") +
        " no jogo de ${gameSchedule(localDate, localTime)}, em $venue."

fun attendanceRemovedBody(localDate: LocalDate, localTime: LocalTime, guestName: String? = null): String =
    (if (guestName == null) "Sua vaga" else "A vaga de $guestName") +
        " no jogo de ${gameSchedule(localDate, localTime)} foi removida pela organização."

/** Sem nome do grupo: na exclusão da conta do dono o nome vira "Grupo excluído" e o aviso guardado não o recoloca. */
const val GROUP_DELETED_BODY = "Um dos seus grupos foi excluído pelo organizador."
