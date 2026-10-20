package br.com.saqz.groups.presentation.setup

import br.com.saqz.domain.GroupId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.GroupPositionSlots
import br.com.saqz.groups.domain.group.GroupPositionSlotsGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.PositionGameSystem
import br.com.saqz.groups.domain.group.PositionSlot

/**
 * Vagas por posição do formulário do grupo (DEB-102), fora da ViewModel: lê, pede a sugestão e grava só quando mudou.
 * Sem o gateway tudo devolve `null` e a tela segue só com o total de jogadores.
 */
internal class GroupPositionSlotsEditor(
    private val gateway: GroupPositionSlotsGateway?,
    private val scope: CoroutineScope,
    private val current: () -> GroupSetupState,
    private val change: ((GroupSetupState) -> GroupSetupState) -> Unit,
) {
    /** O que o servidor tem hoje; `null` até a leitura dar certo. */
    private var loaded: Map<AthletePosition, Int>? = null

    val isLoaded: Boolean get() = loaded != null

    val hasGateway: Boolean get() = gateway != null

    /**
     * Grupo recém-criado: grava as posições com a versão que o servidor devolve na leitura (o grupo acabou de nascer sem
     * posições). `null` = nada a gravar.
     */
    suspend fun saveForNewGroup(
        groupId: GroupId,
        desired: Map<AthletePosition, Int>,
    ): SaqzResult<Unit, GroupProfileError>? {
        val api = gateway
        if (api == null || desired.isEmpty()) return null
        val token = when (val read = api.read(groupId)) {
            is SaqzResult.Failure -> return SaqzResult.Failure(read.error)
            is SaqzResult.Success -> read.value.versionToken
        }
        val slots = AthletePosition.entries.mapNotNull { position -> desired[position]?.let { PositionSlot(position, it) } }
        return when (val result = api.write(groupId, token, GroupPositionSlots(PositionGameSystem.FIVE_ONE, slots))) {
            is SaqzResult.Success -> SaqzResult.Success(Unit)
            is SaqzResult.Failure -> SaqzResult.Failure(result.error)
        }
    }

    suspend fun load(groupId: GroupId): Map<AthletePosition, Int>? {
        val config = (gateway?.read(groupId) as? SaqzResult.Success)?.value?.value ?: return null
        return config.slots.toMap().also { loaded = it }
    }

    /** Só quem gere o grupo lê as vagas por posição; falha cala e a tela segue só com o total. */
    suspend fun loadInto(groupId: GroupId, manages: Boolean, stillCurrent: () -> Boolean) {
        if (!manages) return
        val slots = load(groupId) ?: return
        if (!stillCurrent()) return
        change { it.copy(positionSlots = slots, positionSlotsReady = true) }
        suggestWhenMissing()
    }

    /** 5-1 sem posições (grupo antigo ou recém-passado para 5-1): já mostra a sugestão do total, que é obrigatória. */
    fun suggestWhenMissing() {
        val state = current()
        if (state.showsPositionSlots && state.positionSlots.isEmpty()) suggest()
    }

    fun edit(position: AthletePosition, value: Int) = change { it.withPositionSlot(position, value).revalidated() }

    /** Pede ao servidor a sugestão para o total de jogadores do grupo (editável depois). */
    fun suggest() {
        if (current().isSuggestingPositions) return
        val total = current().positionsTarget
        change { it.copy(isSuggestingPositions = true, positionSuggestionFailed = false) }
        scope.launch {
            val slots = (gateway?.suggestion(PositionGameSystem.FIVE_ONE, total) as? SaqzResult.Success)?.value?.slots?.toMap()
            change { it.withSuggestion(slots).revalidated() }
        }
    }

    /**
     * Grava [desired] com a versão nova do grupo, só se mudou. `null` = nada a gravar; senão o resultado, com o token
     * novo em caso de sucesso.
     */
    suspend fun saveIfChanged(
        groupId: GroupId,
        token: GroupVersionToken,
        desired: Map<AthletePosition, Int>,
        systemWhenOff: PositionGameSystem?,
    ): SaqzResult<GroupVersionToken, GroupProfileError>? {
        val before = loaded
        val api = gateway
        if (before == null || api == null || desired == before) return null
        val config = GroupPositionSlots(
            gameSystem = if (desired.isEmpty()) systemWhenOff else PositionGameSystem.FIVE_ONE,
            slots = AthletePosition.entries.mapNotNull { position -> desired[position]?.let { PositionSlot(position, it) } },
        )
        return when (val result = api.write(groupId, token, config)) {
            is SaqzResult.Success -> {
                loaded = desired
                SaqzResult.Success(result.value.versionToken)
            }
            is SaqzResult.Failure -> SaqzResult.Failure(result.error)
        }
    }
}

private fun List<PositionSlot>.toMap(): Map<AthletePosition, Int> =
    filter { it.slots > 0 }.associate { it.position to it.slots }

/** Troca as vagas de uma posição (0 tira a posição). */
internal fun GroupSetupState.withPositionSlot(position: AthletePosition, value: Int): GroupSetupState {
    val capped = value.coerceIn(0, GroupPositionDefaults.MaxPerPosition)
    val slots = if (capped == 0) positionSlots - position else positionSlots + (position to capped)
    return copy(positionSlots = slots, positionSuggestionFailed = false, creationCommandKey = null)
}

/** Aplica a sugestão do servidor; `null` = falhou, e as posições ficam como estavam. */
internal fun GroupSetupState.withSuggestion(slots: Map<AthletePosition, Int>?): GroupSetupState =
    if (slots == null) copy(isSuggestingPositions = false, positionSuggestionFailed = true)
    else copy(isSuggestingPositions = false, positionSlots = slots)
