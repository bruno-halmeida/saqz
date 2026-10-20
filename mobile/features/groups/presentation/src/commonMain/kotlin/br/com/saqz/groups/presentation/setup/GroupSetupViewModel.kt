package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.group.GroupPositionSlotsGateway
import br.com.saqz.groups.domain.group.PositionGameSystem
import br.com.saqz.groups.domain.group.CreateGroupProfileCommand
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTrialAccessPort
import br.com.saqz.groups.domain.group.GroupTimeZone as DomainGroupTimeZone
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.groups.domain.place.PlacesGateway
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupTimeZone
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.model.MonthlyLateJoinPolicy
import br.com.saqz.groups.model.PromotionMode
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.details.hasMensalista
import br.com.saqz.groups.presentation.pix.PixKeyType
import br.com.saqz.groups.presentation.pix.PixKeys
import br.com.saqz.groups.presentation.place.VenueSearch
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class GroupSetupViewModel(
    initialState: GroupSetupState,
    private val savedState: SavedStateHandle,
    private val groupGateway: GroupGateway,
    private val profileGateway: GroupProfileGateway,
    private val timeZonePort: GroupSystemTimeZonePort,
    private val trialAccess: GroupTrialAccessPort? = null,
    placesGateway: PlacesGateway? = null,
    // Só para saber se o grupo cobra mensalidade por membro (ver `loadRosterHasMensalista`).
    private val athleteGateway: AthleteGateway? = null,
    // DEB-102: vagas por posição; sem o gateway a tela fica como antes (só o total de jogadores).
    positionSlotsGateway: GroupPositionSlotsGateway? = null,
) : MviViewModel<GroupSetupState, GroupSetupIntent, GroupSetupEffect>(
    initialState.withSavedText(savedState),
) {

    private var loadGeneration = 0
    private var versionToken: GroupVersionToken? = null
    private var timeZone: DomainGroupTimeZone? = null
    private var retryDelete = false

    /** Pix como veio do servidor: salvar sem mexer nele manda estes dois valores intactos. */
    private var originalPix: Pair<String?, String?> = null to null
    private val positions = GroupPositionSlotsEditor(positionSlotsGateway, viewModelScope, { state.value }, ::update)
    private val venueSearch = VenueSearch(viewModelScope, placesGateway)

    init {
        viewModelScope.launch {
            venueSearch.state.collect { search -> update { it.copy(venueSearch = search) } }
        }
        venueSearch.refreshAvailability()
        timeZonePort.detect { result ->
            timeZone = (result as? GroupSystemTimeZoneResult.Available)?.value?.toDomain()
        }
        if (state.value.mode is GroupSetupMode.Edit) {
            load()
        } else {
            // Criação (DEB-102): o 5-1 já pede as vagas por posição; sem o gateway a tela segue só com o total.
            update { it.copy(isLoading = false, positionSlotsReady = positions.hasGateway) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override fun handleIntent(intent: GroupSetupIntent) {
        when (intent) {
            is GroupSetupIntent.UpdateName ->
                onTextChange(KeyName, intent.value, GroupTextLimits.NameMax) { copy(name = it) }
            is GroupSetupIntent.UpdateDescription ->
                onTextChange(KeyDescription, intent.value, GroupTextLimits.DescriptionMax) {
                    copy(description = it)
                }
            is GroupSetupIntent.UpdatePixKey -> onPixKeyChange(intent.value)
            is GroupSetupIntent.SelectPixType -> onPixTypeChange(intent.value)
            is GroupSetupIntent.UpdateCustomLevel ->
                onTextChange(KeyCustomLevel, intent.value, GroupTextLimits.CustomLevelMax) {
                    copy(customLevel = it)
                }
            is GroupSetupIntent.UpdateVenueName -> {
                onTextChange(KeyVenueName, intent.value, GroupTextLimits.VenueNameMax) { value ->
                    withVenue { copy(name = value) }
                }
                venueSearch.onQuery(intent.value)
            }
            is GroupSetupIntent.SelectPlaceSuggestion -> venueSearch.select(intent.suggestion) { place ->
                // Um lugar do Google vira só os dois textos do formulário; `id` e `court` seguem os de antes.
                val name = place.name.withoutControlChars().takeCodePoints(GroupTextLimits.VenueNameMax)
                val address = place.address.withoutControlChars().takeCodePoints(GroupTextLimits.VenueAddressMax)
                savedState[KeyVenueName] = name
                savedState[KeyVenueAddress] = address
                onFormChange { withVenue { copy(name = name, address = address) } }
            }
            GroupSetupIntent.DismissPlaceSuggestions -> venueSearch.dismiss()
            is GroupSetupIntent.UpdateVenueAddress ->
                onTextChange(KeyVenueAddress, intent.value, GroupTextLimits.VenueAddressMax) { value ->
                    withVenue { copy(address = value) }
                }
            is GroupSetupIntent.SelectModality ->
                onFormChange(closeSheet = true) { copy(modality = intent.value) }.also { positions.suggestWhenMissing() }
            is GroupSetupIntent.SelectComposition -> onFormChange { copy(composition = intent.value) }
            is GroupSetupIntent.SelectLevel -> onFormChange(closeSheet = true) { copy(level = intent.value) }
            is GroupSetupIntent.SelectPlayStyle ->
                onFormChange { copy(playStyle = intent.value) }.also { positions.suggestWhenMissing() }
            is GroupSetupIntent.UpdateCapacity -> onFormChange {
                copy(defaultCapacity = intent.value.coerceAtLeast(GroupSetupDefaults.MinCapacity))
            }
            is GroupSetupIntent.UpdatePositionSlots -> {
                discardCreationKey()
                positions.edit(intent.position, intent.value)
            }
            GroupSetupIntent.SuggestPositionSlots -> positions.suggest()
            is GroupSetupIntent.UpdateDefaultGameFee -> onFormChange { copy(defaultGameFeeCents = intent.cents) }
            is GroupSetupIntent.SelectConfirmationLead ->
                onFormChange { copy(defaultConfirmationLeadMinutes = intent.minutes) }
            is GroupSetupIntent.ToggleMensalistaPriority ->
                onGameConfigChange { copy(mensalistaPriority = intent.value) }
            is GroupSetupIntent.SelectPromotionMode ->
                onGameConfigChange { copy(promotionMode = intent.value) }
            is GroupSetupIntent.SelectMonthlyLateJoinPolicy -> {
                onFormChange { copy(monthlyLateJoinPolicy = intent.value) }
                savedState[KeyMonthlyLateJoinPolicy] = intent.value.name
            }
            is GroupSetupIntent.SelectDuration -> handleDurationIntent(intent)
            is GroupSetupIntent.ToggleRecurring -> onRecurringChange(intent.value)
            is GroupSetupIntent.OpenSheet -> onOpenSheet(intent.sheet)
            GroupSetupIntent.CloseSheet -> update { it.copy(sheet = null) }
            is GroupSetupIntent.PickSlotWeekday ->
                update { it.copy(slotDraft = it.slotDraft.copy(weekdays = setOf(intent.weekday))) }
            is GroupSetupIntent.ToggleSlotWeekday -> update { it.withSlotWeekdayToggled(intent.weekday) }
            is GroupSetupIntent.PickSlotTime ->
                update { it.copy(slotDraft = it.slotDraft.copy(hour = intent.hour, minute = intent.minute)) }
            GroupSetupIntent.ConfirmSlot -> onConfirmSlot()
            is GroupSetupIntent.RemoveSlot -> onFormChange { copy(regularSlots = regularSlots - intent.slot) }
            GroupSetupIntent.PickPhoto -> emit(GroupSetupEffect.PickPhoto)
            GroupSetupIntent.Submit -> onSubmit()
            GroupSetupIntent.Retry -> onRetry()
            GroupSetupIntent.ConfirmDelete -> onConfirmDelete()
            GroupSetupIntent.DismissSubscriptionRequired -> update { it.copy(subscriptionRequired = false) }
        }
    }

    private fun onRetry() {
        when {
            retryDelete -> onConfirmDelete()
            versionToken == null && state.value.isEditing -> load()
            else -> onSubmit()
        }
    }

    private fun load() {
        val mode = state.value.mode as? GroupSetupMode.Edit ?: return
        val generation = ++loadGeneration
        update {
            it.copy(isLoading = true, gatewayError = null, saveFailed = false, rosterHasMensalista = false, memberCount = null)
        }
        viewModelScope.launch {
            when (val result = profileGateway.readProfile(GroupId(mode.groupId))) {
                is SaqzResult.Failure -> showFailure(generation, result.error.toUiError())
                is SaqzResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    val group = result.value.group
                    versionToken = result.value.versionToken
                    originalPix = group.profile?.pixKey to group.profile?.pixLabel
                    val loadedPix = LoadedPix.of(group.profile?.pixKey, group.profile?.pixLabel)
                    update {
                        it.copy(
                            isLoading = false,
                            gatewayError = null,
                            saveFailed = false,
                            canDelete = group.role == GroupRole.OWNER,
                            canManageGameConfig = group.role != GroupRole.ATHLETE,
                            durationMinutes = group.profile?.regularSlots?.firstOrNull()?.durationMinutes
                                ?: it.durationMinutes,
                            form = group.toForm(),
                            // Sem isso, salvar a edição de um grupo com horários os apagaria:
                            // desligado, `slotsForCommand` manda a lista vazia.
                            recurring = group.profile?.regularSlots.orEmpty().isNotEmpty(),
                            pixKey = loadedPix.key,
                            pixLabel = group.profile?.pixLabel,
                            pixType = loadedPix.type,
                        ).withSavedText(savedState)
                    }
                    positions.loadInto(GroupId(mode.groupId), group.role != GroupRole.ATHLETE) { generation == loadGeneration }
                    val needsMonthlyPolicy = group.needsRosterToShowMonthlyPolicy()
                    val isOwner = group.role == GroupRole.OWNER
                    if (needsMonthlyPolicy || isOwner) {
                        loadRoster(generation, GroupId(mode.groupId), needsMonthlyPolicy, isOwner)
                    }
                }
            }
        }
    }

    /**
     * Na prática a mensalidade é definida por membro, no editor de membro, e o grupo quase nunca tem valor
     * padrão. Por isso o seletor da política de quem vira mensalista depois do vencimento segue a regra do
     * "Mensalistas" da checklist da Início: o grupo cobra mensalidade se tem valor padrão **ou** algum
     * mensalista com valor próprio (`hasMensalista`). Roda depois de o formulário aparecer e só quando pode
     * mudar o resultado; falha cala, como na Início, e o seletor fica escondido.
     *
     * O dono lê o mesmo roster para a folha de exclusão: perdem o acesso os membros ativos fora ele, os mesmos
     * que recebem o aviso de grupo excluído. Falha cala e a folha fala sem número.
     */
    private suspend fun loadRoster(generation: Int, groupId: GroupId, monthlyPolicy: Boolean, owner: Boolean) {
        val gateway = athleteGateway ?: return
        val result = gateway.roster(groupId, AthleteRosterFilter())
        if (generation != loadGeneration) return
        val roster = (result as? SaqzResult.Success)?.value ?: return
        update {
            it.copy(
                rosterHasMensalista = if (monthlyPolicy) roster.hasMensalista() else it.rosterHasMensalista,
                memberCount = if (owner) roster.count { entry -> entry.active && entry.role != GroupRole.OWNER } else it.memberCount,
            )
        }
    }

    private fun onSubmit() {
        val current = state.value
        if (current.isLoading || current.isSaving || current.isDeleting) return
        val errors = validate(current)
        if (errors.isNotEmpty()) {
            update { it.copy(errors = errors, saveFailed = false, gatewayError = null) }
            return
        }
        if (current.isEditing) {
            update { it.copy(isSaving = true, saveFailed = false, gatewayError = null, errors = emptySet()) }
            retryDelete = false
            saveEdit()
        } else {
            update { it.copy(errors = emptySet(), saveFailed = false, gatewayError = null) }
            createGroup()
        }
    }

    private fun saveEdit() {
        val mode = state.value.mode as GroupSetupMode.Edit
        val token = versionToken
        if (token == null) {
            showOperationFailure(GroupUiError.Unknown)
            return
        }
        viewModelScope.launch {
            val commandForm = state.value.toDomainForm(originalPix)
            // ponytail: seam de update; o formulário completo usa o endpoint de perfil e
            // converte seus erros tipados em estado visível antes de emitir Saved.
            when (val result = profileGateway.updateProfile(
                UpdateGroupProfileCommand(GroupId(mode.groupId), token, commandForm),
            )) {
                is SaqzResult.Success -> {
                    versionToken = result.value.versionToken
                    if (savePositionSlots(GroupId(mode.groupId), result.value.versionToken)) {
                        update { it.copy(isSaving = false, saveFailed = false, gatewayError = null) }
                        emit(GroupSetupEffect.Saved)
                    }
                }
                is SaqzResult.Failure -> showSaveFailure(result.error)
            }
        }
    }

    /**
     * DEB-102: depois do perfil, grava as posições com a versão nova do grupo, só se mudaram. Devolve `false` quando
     * falhou (a tela já mostra o erro e o "tentar de novo" refaz o salvamento inteiro com a versão em mãos).
     */
    private suspend fun savePositionSlots(groupId: GroupId, token: GroupVersionToken): Boolean {
        val current = state.value
        if (!current.canManageGameConfig) return true
        val desired = if (current.showsPositionSlots) current.positionSlots else emptyMap()
        val result = positions.saveIfChanged(groupId, token, desired, current.form.playStyle.toPositionSystem()) ?: return true
        if (result is SaqzResult.Failure) showSaveFailure(result.error)
        if (result is SaqzResult.Success) versionToken = result.value
        return result is SaqzResult.Success
    }

    private fun createGroup() {
        val current = state.value
        val commandKey = current.creationCommandKey ?: newCommandKey()
        val zone = timeZone
        if (current.isSaving || zone == null) {
            if (zone == null) showOperationFailure(GroupUiError.Unknown)
            return
        }
        if (current.creationCommandKey == null) savedState[KeyCreationCommand] = commandKey
        val commandForm = current.toDomainForm()
        update {
            it.copy(
                isSaving = true,
                saveFailed = false,
                gatewayError = null,
                creationCommandKey = commandKey,
            )
        }
        viewModelScope.launch {
            // ponytail: seam de create; createProfile persiste a carga completa do formulário
            // e devolve o id autoritativo para o efeito de saída.
            when (val result = profileGateway.createProfile(
                CreateGroupProfileCommand(commandKey, zone, commandForm),
            )) {
                is SaqzResult.Success -> {
                    // O grupo já existe: se as posições falharem, a chave fica e "tentar de novo" reaproveita o mesmo grupo.
                    val desired = if (current.showsPositionSlots) current.positionSlots else emptyMap()
                    val saved = positions.saveForNewGroup(result.value.id, desired)
                    if (saved is SaqzResult.Failure) {
                        showSaveFailure(saved.error)
                        return@launch
                    }
                    discardCreationKey()
                    update {
                        it.copy(
                            isSaving = false,
                            saveFailed = false,
                            gatewayError = null,
                            creationCommandKey = null,
                        )
                    }
                    SaqzAnalytics.event("group_created")
                    emit(GroupSetupEffect.Created(result.value.id.value))
                }
                is SaqzResult.Failure -> showSaveFailure(result.error)
            }
        }
    }

    private fun onConfirmDelete() {
        val mode = state.value.mode as? GroupSetupMode.Edit ?: return
        if (state.value.isDeleting) return
        retryDelete = true
        update { it.copy(isDeleting = true, isSaving = false, sheet = null, gatewayError = null, saveFailed = false) }
        viewModelScope.launch {
            // ponytail: seam de delete; 403/404 continuam GroupProfileError até a borda da UI.
            when (val result = groupGateway.delete(GroupId(mode.groupId))) {
                is SaqzResult.Success -> {
                    update { it.copy(isDeleting = false) }
                    emit(GroupSetupEffect.Deleted)
                }
                is SaqzResult.Failure -> showOperationFailure(result.error.toUiError())
            }
        }
    }

    /** Falha ao gravar o grupo: aviso genérico, ou a folha do plano. */
    private fun showSaveFailure(error: GroupProfileError) = showOperationFailure(error.toUiError())

    private fun showOperationFailure(error: GroupUiError) {
        if (error == GroupUiError.SubscriptionRequired) {
            showSubscriptionRequired()
            return
        }
        update {
            it.copy(
                isLoading = false,
                isSaving = false,
                isDeleting = false,
                saveFailed = true,
                gatewayError = error,
            )
        }
    }

    /** Sem plano ativo "tentar de novo" nunca resolve: o diálogo oferece a assinatura. */
    private fun showSubscriptionRequired() {
        val groupId = (state.value.mode as? GroupSetupMode.Edit)?.groupId
        viewModelScope.launch {
            val trialEnded = groupId?.let { trialAccess?.read(GroupId(it)) }?.trialEnded == true
            update {
                it.copy(
                    isLoading = false,
                    isSaving = false,
                    isDeleting = false,
                    saveFailed = false,
                    gatewayError = null,
                    subscriptionRequired = true,
                    trialEnded = trialEnded,
                )
            }
        }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        showOperationFailure(error)
    }

    private fun discardCreationKey() {
        savedState.remove<String>(KeyCreationCommand)
    }

    private fun onOpenSheet(sheet: GroupSetupSheet) {
        val index = (sheet as? GroupSetupSheet.Slot)?.index
        val slot = index?.let { state.value.form.regularSlots.getOrNull(it) }
        update { current -> current.copy(sheet = sheet, slotDraft = slot?.toDraft() ?: current.slotDraft) }
    }

    private fun onConfirmSlot() {
        val confirmed = state.value.withSlotConfirmed() ?: return
        discardCreationKey()
        update { confirmed }
    }

    private fun handleDurationIntent(intent: GroupSetupIntent) {
        discardCreationKey()
        update { it.withDurationIntent(intent) }
    }

    private fun onRecurringChange(recurring: Boolean) {
        discardCreationKey()
        update { current -> current.copy(recurring = recurring, creationCommandKey = null).revalidated() }
    }

    private fun onTextChange(
        key: String,
        value: String,
        maxLength: Int,
        edit: GroupSetupForm.(String) -> GroupSetupForm,
    ) {
        val capped = value.withoutControlChars().takeCodePoints(maxLength)
        savedState[key] = capped
        onFormChange { edit(capped) }
    }

    /** Chave Pix digitada ou colada: normaliza pelo tipo escolhido (sem tipo, só limpa e corta). */
    private fun onPixKeyChange(value: String) {
        val type = state.value.pixType
        val cleaned = value.withoutControlChars().takeCodePoints(GroupPixTextLimits.KeyMax)
        val key = if (type == null) cleaned else PixKeys.normalize(type, cleaned)
        savedState[KeyPixKey] = key
        discardCreationKey()
        update { it.copy(pixKey = key, pixTouched = true, creationCommandKey = null).revalidated() }
    }

    private fun onPixTypeChange(type: PixKeyType) {
        savedState[KeyPixType] = type.name
        discardCreationKey()
        update { current ->
            val key = current.pixKey?.let { PixKeys.normalize(type, it) }
            if (key != null) savedState[KeyPixKey] = key
            current.copy(pixType = type, pixKey = key, pixTouched = true, creationCommandKey = null).revalidated()
        }
    }

    private fun onFormChange(
        closeSheet: Boolean = false,
        transform: GroupSetupForm.() -> GroupSetupForm,
    ) {
        discardCreationKey()
        update {
            it.withForm(transform)
                .copy(creationCommandKey = null, sheet = if (closeSheet) null else it.sheet)
                .revalidated()
        }
    }

    private fun onGameConfigChange(transform: GroupSetupForm.() -> GroupSetupForm) {
        onFormChange(transform = transform)
        val form = state.value.form
        savedState[KeyMensalistaPriority] = form.mensalistaPriority
        savedState[KeyPromotionMode] = form.promotionMode.name
    }
}

private fun GroupSetupState.withForm(transform: GroupSetupForm.() -> GroupSetupForm) = copy(form = form.transform())

internal fun GroupSetupState.revalidated() = if (errors.isEmpty()) this else copy(errors = validate(this))

private fun GroupSetupState.withSavedText(handle: SavedStateHandle): GroupSetupState = copy(
    form = form.withSavedText(handle),
    pixKey = handle.get<String>(KeyPixKey) ?: pixKey,
    pixType = handle.get<String>(KeyPixType)?.let { saved -> PixKeyType.entries.firstOrNull { it.name == saved } } ?: pixType,
    // Chave ou tipo restaurados do SavedStateHandle só existem se a pessoa mexeu no Pix antes.
    pixTouched = pixTouched || handle.get<String>(KeyPixKey) != null || handle.get<String>(KeyPixType) != null,
    creationCommandKey = handle.get<String>(KeyCreationCommand),
)

private fun GroupSetupForm.withSavedText(handle: SavedStateHandle): GroupSetupForm {
    val name = handle.get<String>(KeyName)
    val description = handle.get<String>(KeyDescription)
    val customLevel = handle.get<String>(KeyCustomLevel)
    val venueName = handle.get<String>(KeyVenueName)
    val venueAddress = handle.get<String>(KeyVenueAddress)
    val promotionMode = handle.get<String>(KeyPromotionMode)
        ?.let { runCatching { PromotionMode.valueOf(it) }.getOrNull() }
    // Só restaura se o backend mandou o campo: sem ele a tela não mostra o seletor nem envia nada.
    val lateJoinPolicy = monthlyLateJoinPolicy?.let {
        handle.get<String>(KeyMonthlyLateJoinPolicy)
            ?.let { saved -> MonthlyLateJoinPolicy.entries.firstOrNull { it.name == saved } }
            ?: it
    }
    val venue = when {
        venueName == null && venueAddress == null -> defaultVenue
        else -> (defaultVenue ?: EmptyVenue).copy(
            name = venueName ?: defaultVenue?.name.orEmpty(),
            address = venueAddress ?: defaultVenue?.address.orEmpty(),
        ).orNullWhenCleared()
    }
    return copy(
        name = name ?: this.name,
        description = description ?: this.description,
        customLevel = customLevel ?: this.customLevel,
        defaultVenue = venue,
        mensalistaPriority = handle.get<Boolean>(KeyMensalistaPriority) ?: this.mensalistaPriority,
        promotionMode = promotionMode ?: this.promotionMode,
        monthlyLateJoinPolicy = lateJoinPolicy,
    )
}

private fun GroupSetupForm.withVenue(transform: GroupVenueForm.() -> GroupVenueForm) =
    copy(defaultVenue = (defaultVenue ?: EmptyVenue).transform().orNullWhenCleared())

private fun GroupPlayStyle?.toPositionSystem(): PositionGameSystem? = when (this) {
    GroupPlayStyle.SIX_ZERO -> PositionGameSystem.SIX_ZERO
    GroupPlayStyle.FIVE_ONE -> PositionGameSystem.FIVE_ONE
    else -> null
}

private fun GroupSetupState.toDomainForm(original: Pair<String?, String?> = null to null): br.com.saqz.groups.domain.group.GroupSetupForm {
    val edit = mode is GroupSetupMode.Edit
    // Sem mexer no Pix, os dois valores antigos seguem intactos (chave antiga em texto livre inclusive).
    val key = if (pixTouched || !edit) pixKey?.trim() else original.first
    val label = when {
        !edit -> null
        !pixTouched -> original.second
        key.isNullOrEmpty() -> ""
        else -> pixType?.label.orEmpty()
    }
    return form.toDomain(
        slots = slotsForCommand,
        pixKey = key?.takeIf { it.isNotEmpty() || edit },
        pixLabel = label?.trim()?.takeIf { it.isNotEmpty() || edit },
    )
}

/** Pix aberto para edição: o tipo vem do `pixLabel` se for um dos quatro, senão se deduz do formato da chave. */
private class LoadedPix(val key: String?, val type: PixKeyType?) {
    companion object {
        fun of(key: String?, label: String?): LoadedPix {
            val type = PixKeyType.fromLabel(label) ?: PixKeys.deduce(key)
            // Com tipo, o campo mostra a chave já normalizada; os valores antigos seguem em `originalPix`.
            return LoadedPix(if (type != null && key != null) PixKeys.normalize(type, key) else key, type)
        }
    }
}

private fun GroupSetupForm.toDomain(
    slots: List<GroupRegularSlotForm> = regularSlots,
    pixKey: String? = null,
    pixLabel: String? = null,
) = br.com.saqz.groups.domain.group.GroupSetupForm(
    name = name,
    modality = modality?.let { br.com.saqz.groups.domain.group.GroupModality.valueOf(it.name) },
    composition = composition?.let { br.com.saqz.groups.domain.group.GroupComposition.valueOf(it.name) },
    description = description,
    city = city,
    level = level?.let { br.com.saqz.groups.domain.group.GroupLevel.valueOf(it.name) },
    customLevel = customLevel,
    playStyle = playStyle?.let { br.com.saqz.groups.domain.group.GroupPlayStyle.valueOf(it.name) },
    customPlayStyle = customPlayStyle,
    defaultVenue = defaultVenue?.let { br.com.saqz.groups.domain.group.GroupVenue(it.id, it.name, it.address, it.court) },
    regularSlots = slots.map {
        br.com.saqz.groups.domain.group.GroupRegularSlot(
            it.id,
            br.com.saqz.groups.domain.group.GroupWeekday.valueOf(it.weekday.name),
            it.startTime,
            it.durationMinutes,
        )
    },
    defaultCapacity = defaultCapacity,
    defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
    defaultGameFeeCents = defaultGameFeeCents,
    monthlyFeeCents = monthlyFeeCents,
    monthlyDueDay = monthlyDueDay,
    monthlyLateJoinPolicy = monthlyLateJoinPolicy?.let {
        br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy.valueOf(it.name)
    },
    mensalistaPriority = mensalistaPriority,
    promotionMode = br.com.saqz.groups.domain.group.PromotionMode.valueOf(promotionMode.name),
    pixKey = pixKey?.trim(),
    pixLabel = pixLabel?.trim(),
).cleaned()

private fun br.com.saqz.groups.domain.group.Group.toForm() = GroupSetupForm(
    name = name,
    modality = profile?.modality?.let { GroupModality.valueOf(it.name) },
    composition = profile?.composition?.let { GroupComposition.valueOf(it.name) },
    description = profile?.description,
    city = profile?.city,
    level = profile?.level?.let { GroupLevel.valueOf(it.name) },
    customLevel = profile?.customLevel,
    playStyle = profile?.playStyle?.let { GroupPlayStyle.valueOf(it.name) },
    customPlayStyle = profile?.customPlayStyle,
    defaultVenue = profile?.defaultVenue?.let { GroupVenueForm(it.id, it.name, it.address, it.court) },
    regularSlots = profile?.regularSlots.orEmpty().map {
        GroupRegularSlotForm(it.id, GroupWeekday.valueOf(it.weekday.name), it.startTime, it.durationMinutes)
    },
    // Grupo antigo com padrão 2 ou 3 abre no mínimo; só muda no servidor quando salvar de novo.
    defaultCapacity = profile?.defaultCapacity?.coerceAtLeast(GroupSetupDefaults.MinCapacity),
    defaultConfirmationLeadMinutes = profile?.defaultConfirmationLeadMinutes,
    defaultGameFeeCents = financeDefaults?.defaultGameFeeCents,
    monthlyFeeCents = financeDefaults?.monthlyFeeCents,
    monthlyDueDay = financeDefaults?.monthlyDueDay,
    monthlyLateJoinPolicy = financeDefaults?.monthlyLateJoinPolicy?.let { MonthlyLateJoinPolicy.valueOf(it.name) },
    mensalistaPriority = gameConfig.mensalistaPriority,
    promotionMode = PromotionMode.valueOf(gameConfig.promotionMode.name),
)

/**
 * Só o gestor recebe a política (`financeDefaults`), e com valor padrão do grupo o seletor já aparece sem
 * olhar os membros: buscar o roster só adianta quando o grupo tem a política e não tem o valor padrão.
 */
private fun br.com.saqz.groups.domain.group.Group.needsRosterToShowMonthlyPolicy(): Boolean {
    val finance = financeDefaults ?: return false
    return role != GroupRole.ATHLETE && finance.monthlyLateJoinPolicy != null && finance.monthlyFeeCents == null
}

private fun GroupTimeZone.toDomain() = DomainGroupTimeZone(id)

private fun GroupRegularSlotForm.toDraft(): GroupSlotDraft {
    val parts = startTime.split(':')
    return GroupSlotDraft(weekday, parts.getOrNull(0)?.toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
}


private val EmptyVenue = GroupVenueForm(name = "", address = "")

@OptIn(ExperimentalUuidApi::class)
private fun newCommandKey(): String = Uuid.random().toString()

private const val KeyName = "group-setup-name"
private const val KeyDescription = "group-setup-description"
private const val KeyPixKey = "group-setup-pix-key"
private const val KeyPixType = "group-setup-pix-type"
private const val KeyCustomLevel = "group-setup-custom-level"
private const val KeyVenueName = "group-setup-venue-name"
private const val KeyVenueAddress = "group-setup-venue-address"
private const val KeyMensalistaPriority = "group-setup-mensalista-priority"
private const val KeyPromotionMode = "group-setup-promotion-mode"
private const val KeyMonthlyLateJoinPolicy = "group-setup-monthly-late-join-policy"
private const val KeyCreationCommand = "group-setup-create-command-key"
