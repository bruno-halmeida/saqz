package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.attendance.PositionChoice
import br.com.saqz.groups.domain.attendance.PositionLoad

/** Resultado de recortar o agregado para uma posição; [Invalid] é posição pedida sem vaga configurada no jogo. */
internal sealed interface PositionScope {
    data class Scoped(val aggregate: AttendanceAggregate) : PositionScope
    data object Invalid : PositionScope
}

/**
 * Jogo com posições (DEB-102): fixa a posição da linha e recorta [capacity]/[confirmedCount] para ela.
 * - Quem já está no jogo (CONFIRMED/WAITLISTED) mantém a posição que tem; repetir "Vou" com outra não muda nada (PRES-009).
 * - Quem entra (sem resposta ou DECLINED) usa [requested] ou, sem ela (apps 1.0.6 a 1.0.9), [PositionChoice.resolve].
 * - DECLINED e decisões sobre linha existente usam a posição da própria linha.
 * [useProfile] é falso para convidado, que não tem perfil: sem posição pedida vai para a posição com mais vagas livres.
 */
internal fun AttendanceAggregate.scopedTo(
    loads: List<PositionLoad>,
    requested: AthletePosition?,
    useProfile: Boolean = true,
): PositionScope {
    val existing = current?.takeIf { it.status != br.com.saqz.groups.domain.attendance.AttendanceStatus.DECLINED }?.position
    val chosen = existing ?: PositionChoice.resolve(
        loads, requested,
        profilePosition.takeIf { useProfile }, profileSecondaryPosition.takeIf { useProfile },
    ) ?: return PositionScope.Invalid
    val load = loads.firstOrNull { it.position == chosen } ?: return PositionScope.Invalid
    return PositionScope.Scoped(copy(position = chosen, capacity = load.slots, confirmedCount = load.confirmed))
}
