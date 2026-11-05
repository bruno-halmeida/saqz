package br.com.saqz.groups.adapter.output.jdbc.attendance

/**
 * Ordem dos confirmados nas listas do jogo (pedido do dono em 05/11/2026: antes saía por nome): quem entrou na lista primeiro
 * vem primeiro, na tela do jogo, na Início e no grupo do WhatsApp. A chave é o `seq` do último evento que pôs a linha em
 * `CONFIRMED`: o "Vou" que confirma direto, a subida da espera ou o "Colocar" do organizador. Quem desiste e volta entra de
 * novo no fim; quem sobe da espera entra quando sobe. Toda mudança de status grava evento (`RespondAttendance`,
 * `AttendancePromotion`, `ReleaseMemberAttendance`), e mudar só a posição não muda o status, então não mexe no lugar.
 */
internal object ConfirmationOrderSql {
    /** Expressão escalar: NULL fora dos confirmados (ou sem evento, o que só acontece em dado montado à mão); senão o `seq`. */
    fun of(attendance: String): String =
        "(CASE WHEN $attendance.status='CONFIRMED' THEN (SELECT max(co_e.seq) FROM attendance_events co_e " +
            "WHERE co_e.game_id=$attendance.game_id AND co_e.member_user_id=$attendance.member_user_id " +
            "AND co_e.guest_seq=$attendance.guest_seq AND co_e.new_status='CONFIRMED') END)"
}
