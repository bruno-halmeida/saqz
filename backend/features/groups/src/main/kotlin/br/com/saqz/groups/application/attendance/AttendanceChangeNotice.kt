package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus

/**
 * Aviso direto à pessoa (ao anfitrião, quando o afetado é convidado) quando OUTRA parte muda a presença dela:
 * a fila a promoveu ou o organizador a confirmou/tirou. Chamado a cada evento gravado, na mesma transação.
 */
fun interface AttendanceChangeNotice {
    fun changed(event: AttendanceEvent, kind: AttendanceChangeKind)
}

enum class AttendanceChangeKind { CONFIRMED, REMOVED }

/**
 * Quando um evento merece aviso. Fora dele: a própria pessoa respondeu (SELF), a confirmação automática do
 * mensalista (SYSTEM sem estar na espera, que tem aviso próprio), a saída do grupo e a queda dos convidados
 * junto com o anfitrião (SYSTEM → DECLINED), a repetição sem mudança de estado e quem age sobre si mesmo ou sobre
 * o próprio convidado (ex.: "Vou" depois do prazo, que a fila confirma na hora).
 */
internal fun AttendanceEvent.changeKind(): AttendanceChangeKind? = when {
    oldStatus == newStatus || actorId == memberId -> null
    newStatus == AttendanceStatus.CONFIRMED -> when (source) {
        AttendanceSource.SELF -> null
        AttendanceSource.SYSTEM -> AttendanceChangeKind.CONFIRMED.takeIf { oldStatus == AttendanceStatus.WAITLISTED }
        AttendanceSource.ORGANIZER -> AttendanceChangeKind.CONFIRMED
    }
    newStatus == AttendanceStatus.DECLINED && source == AttendanceSource.ORGANIZER &&
        (oldStatus == AttendanceStatus.CONFIRMED || oldStatus == AttendanceStatus.WAITLISTED) -> AttendanceChangeKind.REMOVED
    else -> null
}
