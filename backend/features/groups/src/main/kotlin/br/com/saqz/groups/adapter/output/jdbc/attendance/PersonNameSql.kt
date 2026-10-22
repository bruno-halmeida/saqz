package br.com.saqz.groups.adapter.output.jdbc.attendance

/**
 * Nome de pessoa nas listas de um jogo (MEM-004, DEB-136), montado na hora: apelido do grupo ("Como te chamam"), apelido
 * do perfil, nome. Convidado (`guest_seq > 0`) fica com o nome que o anfitrião digitou. Cobranças não usam isto: lá vale o
 * nome copiado em `group_charges.member_display_name`.
 */
internal object PersonNameSql {
    /** Expressão escalar com o nome de [user] no grupo [group]; NULL se a conta não existe. */
    fun member(group: String, user: String): String =
        "(SELECT coalesce(nullif(btrim(pn_gm.nickname),''), nullif(btrim(pn_u.nickname),''), pn_u.display_name) " +
            "FROM access_users pn_u LEFT JOIN group_memberships pn_gm ON pn_gm.group_id=$group AND pn_gm.user_id=pn_u.id " +
            "WHERE pn_u.id=$user)"

    /** Nome de quem está na linha [attendance] de `game_attendance`; a cópia gravada é só rede. */
    fun attendance(attendance: String): String =
        "(CASE WHEN $attendance.guest_seq>0 THEN $attendance.member_display_name " +
            "ELSE coalesce(${member("$attendance.group_id", "$attendance.member_user_id")}, $attendance.member_display_name) END)"
}
