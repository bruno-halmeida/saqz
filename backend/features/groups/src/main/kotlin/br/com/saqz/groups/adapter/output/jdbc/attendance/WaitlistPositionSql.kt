package br.com.saqz.groups.adapter.output.jdbc.attendance

/**
 * Posição na fila como o app mostra ("1º na fila"). A ordem é a mesma de `EARLIEST_WAITLISTED`, que é
 * quem sobe primeiro: mensalista na frente enquanto o prazo está aberto (e antes do início) e a prioridade ligada, depois
 * ordem de chegada (`waitlist_sequence`). A sequência gravada nunca é renumerada, então não serve de
 * posição (rodada multi-aparelho de 09/10/2026: "2º na fila" sendo o único da lista).
 */
internal object WaitlistPositionSql {
    /** 0 para quem tem prioridade de mensalista neste momento, 1 para o resto. */
    fun tier(attendance: String, membership: String, game: String, group: String): String =
        "CASE WHEN $game.confirmation_deadline >= now() AND $game.confirmation_deadline < $game.starts_at AND $group.mensalista_priority " +
            "AND $membership.membership_type='MENSALISTA' AND $attendance.guest_seq=0 THEN 0 ELSE 1 END"

    /**
     * Expressão escalar: NULL fora da fila; senão 1 + quantos estão na frente de [attendance].
     * [membership] é a adesão de quem está em [attendance]; [game] e [group] são os aliases já presentes na consulta.
     */
    fun of(attendance: String, membership: String, game: String, group: String): String {
        val mine = tier(attendance, membership, game, group)
        val theirs = tier("w", "wm", game, group)
        return """
            CASE WHEN $attendance.status='WAITLISTED' THEN (
                SELECT count(*) + 1 FROM game_attendance w
                LEFT JOIN group_memberships wm ON wm.group_id=w.group_id AND wm.user_id=w.member_user_id
                WHERE w.game_id=$attendance.game_id AND w.status='WAITLISTED'
                  AND (w.member_user_id<>$attendance.member_user_id OR w.guest_seq<>$attendance.guest_seq)
                  -- Jogo com posições (DEB-102): "sua vez na fila" é a fila da sua posição (position nula = jogo sem posições).
                  AND w.position IS NOT DISTINCT FROM $attendance.position
                  AND (($theirs) < ($mine) OR (($theirs) = ($mine) AND w.waitlist_sequence < $attendance.waitlist_sequence))
            ) END
        """.trimIndent()
    }
}
