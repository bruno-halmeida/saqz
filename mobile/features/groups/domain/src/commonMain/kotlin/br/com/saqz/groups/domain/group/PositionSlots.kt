package br.com.saqz.groups.domain.group

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthletePosition

/** Sistema de jogo para a sugestão de vagas (DEB-102). 6-0 não usa posições. */
enum class PositionGameSystem { FIVE_ONE, SIX_ZERO }

data class PositionSlot(val position: AthletePosition, val slots: Int)

/** Vagas por posição do grupo; [slots] vazio = grupo sem posições (só o total, como antes). */
data class GroupPositionSlots(
    val gameSystem: PositionGameSystem?,
    val slots: List<PositionSlot>,
) {
    val total: Int get() = slots.sumOf { it.slots }
    val isEnabled: Boolean get() = slots.isNotEmpty()
}

data class VersionedGroupPositionSlots(val value: GroupPositionSlots, val versionToken: GroupVersionToken)

interface GroupPositionSlotsGateway {
    suspend fun read(groupId: GroupId): SaqzResult<VersionedGroupPositionSlots, GroupProfileError>

    /** Troca a configuração (lista vazia desliga as posições); [versionToken] é a versão do grupo. */
    suspend fun write(
        groupId: GroupId,
        versionToken: GroupVersionToken,
        config: GroupPositionSlots,
    ): SaqzResult<VersionedGroupPositionSlots, GroupProfileError>

    /** Sugestão do backend pelo total de vagas (a tabela mora lá, num lugar só); 6-0 volta sem posições. */
    suspend fun suggestion(
        system: PositionGameSystem,
        total: Int,
    ): SaqzResult<GroupPositionSlots, GroupProfileError>
}
