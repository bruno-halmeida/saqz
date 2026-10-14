package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
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
) : MviViewModel<GroupSetupState, GroupSetupIntent, GroupSetupEffect>(
    initialState.withSavedText(savedState),
) {

    private var loadGeneration = 0
    private var versionToken: GroupVersionToken? = null
    private var timeZone: DomainGroupTimeZone? = null
    private var retryDelete = false
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
            update { it.copy(isLoading = false) }
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
            is GroupSetupIntent.UpdatePixKey ->
                onPixTextChange(KeyPixKey, intent.value, GroupPixTextLimits.KeyMax) { copy(pixKey = it) }
            is GroupSetupIntent.UpdatePixLabel ->
                onPixTextChange(KeyPixLabel, intent.value, GroupPixTextLimits.LabelMax) {
                    copy(pixLabel = it)
                }
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
            is GroupSetupIntent.SelectModality -> onFormChange(closeSheet = true) { copy(modality = intent.value) }
            is GroupSetupIntent.SelectComposition -> onFormChange { copy(composition = intent.value) }
            is GroupSetupIntent.SelectLevel -> onFormChange(closeSheet = true) { copy(level = intent.value) }
            is GroupSetupIntent.SelectPlayStyle -> onFormChange { copy(playStyle = intent.value) }
            is GroupSetupIntent.UpdateCapacity -> onFormChange { copy(defaultCapacity = intent.value) }
            is GroupSetupIntent.SelectConfirmationLead ->
                onFormChange { copy(defaultConfirmationLeadMinutes = intent.minutes) }
            is GroupSetupIntent.ToggleMensalistaPriority ->
                onGameConfigChange { copy(mensalistaPriority = intent.value) }
            is GroupSetupIntent.SelectPromotionMode ->
                onGameConfigChange { copy(promotionMode = intent.value) }
            is GroupSetupIntent.ToggleAutoConfirm ->
                onGameConfigChange { copy(autoConfirmEnabled = intent.value) }
            is GroupSetupIntent.SelectMonthlyLateJoinPolicy -> {
                onFormChange { copy(monthlyLateJoinPolicy = intent.value) }
                savedState[KeyMonthlyLateJoinPolicy] = intent.value.name
            }
            is GroupSetupIntent.SelectDuration -> onDurationChange(intent.minutes)
            is GroupSetupIntent.ToggleRecurring -> onRecurringChange(intent.value)
            is GroupSetupIntent.OpenSheet -> onOpenSheet(intent.sheet)
            GroupSetupIntent.CloseSheet -> update { it.copy(sheet = null) }
            is GroupSetupIntent.PickSlotWeekday ->
                update { it.copy(slotDraft = it.slotDraft.copy(weekday = intent.weekday)) }
            is GroupSetupIntent.PickSlotTime ->
                update { it.copy(slotDraft = it.slotDraft.copy(hour = intent.hour, minute = intent.minute)) }
            GroupSetupIntent.ConfirmSlot -> onConfirmSlot()
            is GroupSetupIntent.RemoveSlot -> onFormChange { copy(regularSlots = regularSlots - intent.slot) }
            GroupSetupIntent.PickPhoto -> emit(GroupSetupEffect.PickPhoto)
            GroupSetupIntent.Submit -> onSubmit()
            GroupSetupIntent.Retry -> onRetry()
            GroupSetupIntent.ConfirmDelete -> onConfirmDelete()
            GroupSetupIntent.SaveDraft -> onSaveDraft()
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
        update { it.copy(isLoading = true, gatewayError = null, saveFailed = false, rosterHasMensalista = false) }
        viewModelScope.launch {
            when (val result = profileGateway.readProfile(GroupId(mode.groupId))) {
                is SaqzResult.Failure -> showFailure(generation, result.error.toUiError())
                is SaqzResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    val group = result.value.group
                    versionToken = result.value.versionToken
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
                            pixKey = group.profile?.pixKey,
                            pixLabel = group.profile?.pixLabel,
                        ).withSavedText(savedState)
                    }
                    if (group.needsRosterToShowMonthlyPolicy()) {
                        loadRosterHasMensalista(generation, GroupId(mode.groupId))
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
     */
    private suspend fun loadRosterHasMensalista(generation: Int, groupId: GroupId) {
        val gateway = athleteGateway ?: return
        val result = gateway.roster(groupId, AthleteRosterFilter())
        if (generation != loadGeneration) return
        val roster = (result as? SaqzResult.Success)?.value ?: return
        update { it.copy(rosterHasMensalista = roster.hasMensalista()) }
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
            val commandForm = state.value.toDomainForm()
            // ponytail: seam de update; o formulário completo usa o endpoint de perfil e
            // converte seus erros tipados em estado visível antes de emitir Saved.
            when (val result = profileGateway.updateProfile(
                UpdateGroupProfileCommand(GroupId(mode.groupId), token, commandForm),
            )) {
                is SaqzResult.Success -> {
                    versionToken = result.value.versionToken
                    update { it.copy(isSaving = false, saveFailed = false, gatewayError = null) }
                    emit(GroupSetupEffect.Saved)
                }
                is SaqzResult.Failure -> showSaveFailure(result.error)
            }
        }
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

    /**
     * Falha ao gravar o grupo. Um campo que o servidor recusou e o formulário não barrou (o endereço da
     * quadra no backend v.0.0.17) vira erro do próprio campo, com o formulário como estava; o resto cai
     * no aviso genérico, ou na folha do plano.
     */
    private fun showSaveFailure(error: GroupProfileError) {
        val fieldErrors = (error as? GroupProfileError.Validation)?.toSetupFieldErrors().orEmpty()
        if (fieldErrors.isEmpty()) {
            showOperationFailure(error.toUiError())
            return
        }
        update {
            it.copy(isLoading = false, isSaving = false, saveFailed = false, gatewayError = null, errors = fieldErrors)
        }
    }

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

    private fun onSaveDraft() {
        discardCreationKey()
        retryDelete = false
        update { it.copy(saveFailed = false, gatewayError = null, creationCommandKey = null) }
        emit(GroupSetupEffect.DraftSaved)
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
        val current = state.value
        val sheet = current.sheet as? GroupSetupSheet.Slot ?: return
        val draft = current.slotDraft
        val slot = GroupRegularSlotForm(
            weekday = draft.weekday,
            startTime = formatTime(draft.hour, draft.minute),
            durationMinutes = current.durationMinutes,
        )
        val slots = current.form.regularSlots
        val updated = when {
            sheet.index == null || sheet.index !in slots.indices -> slots + slot
            else -> slots.toMutableList().also { it[sheet.index] = slot }
        }
        discardCreationKey()
        update {
            it.copy(sheet = null, creationCommandKey = null)
                .withForm { copy(regularSlots = updated) }
                .revalidated()
        }
    }

    private fun onDurationChange(minutes: Int) {
        discardCreationKey()
        update { current ->
            current.copy(durationMinutes = minutes, creationCommandKey = null)
                .withForm { copy(regularSlots = regularSlots.map { it.copy(durationMinutes = minutes) }) }
                .revalidated()
        }
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

    private fun onPixTextChange(
        key: String,
        value: String,
        maxLength: Int,
        edit: GroupSetupState.(String) -> GroupSetupState,
    ) {
        val capped = value.withoutControlChars().takeCodePoints(maxLength)
        savedState[key] = capped
        discardCreationKey()
        update { current -> edit.invoke(current, capped).copy(creationCommandKey = null).revalidated() }
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
        savedState[KeyAutoConfirm] = form.autoConfirmEnabled
    }
}

private fun GroupSetupState.withForm(transform: GroupSetupForm.() -> GroupSetupForm) = copy(form = form.transform())

private fun GroupSetupState.revalidated() = if (errors.isEmpty()) this else copy(errors = validate(this))

private fun GroupSetupState.withSavedText(handle: SavedStateHandle): GroupSetupState = copy(
    form = form.withSavedText(handle),
    pixKey = handle.get<String>(KeyPixKey) ?: pixKey,
    pixLabel = handle.get<String>(KeyPixLabel) ?: pixLabel,
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
        autoConfirmEnabled = handle.get<Boolean>(KeyAutoConfirm) ?: this.autoConfirmEnabled,
    )
}

private fun GroupSetupForm.withVenue(transform: GroupVenueForm.() -> GroupVenueForm) =
    copy(defaultVenue = (defaultVenue ?: EmptyVenue).transform().orNullWhenCleared())

private fun GroupSetupState.toDomainForm() = form.toDomain(
    slots = slotsForCommand,
    pixKey = pixKey?.trim()?.takeIf { it.isNotEmpty() || mode is GroupSetupMode.Edit },
    pixLabel = pixLabel?.trim()?.takeIf { it.isNotEmpty() || mode is GroupSetupMode.Edit },
)

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
    autoConfirmEnabled = autoConfirmEnabled,
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
    defaultCapacity = profile?.defaultCapacity,
    defaultConfirmationLeadMinutes = profile?.defaultConfirmationLeadMinutes,
    defaultGameFeeCents = financeDefaults?.defaultGameFeeCents,
    monthlyFeeCents = financeDefaults?.monthlyFeeCents,
    monthlyDueDay = financeDefaults?.monthlyDueDay,
    monthlyLateJoinPolicy = financeDefaults?.monthlyLateJoinPolicy?.let { MonthlyLateJoinPolicy.valueOf(it.name) },
    mensalistaPriority = gameConfig.mensalistaPriority,
    promotionMode = PromotionMode.valueOf(gameConfig.promotionMode.name),
    autoConfirmEnabled = gameConfig.autoConfirmEnabled,
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

private fun formatTime(hour: Int, minute: Int) = "${hour.pad()}:${minute.pad()}"
private fun Int.pad() = toString().padStart(2, '0')

private val EmptyVenue = GroupVenueForm(name = "", address = "")

@OptIn(ExperimentalUuidApi::class)
private fun newCommandKey(): String = Uuid.random().toString()

private const val KeyName = "group-setup-name"
private const val KeyDescription = "group-setup-description"
private const val KeyPixKey = "group-setup-pix-key"
private const val KeyPixLabel = "group-setup-pix-label"
private const val KeyCustomLevel = "group-setup-custom-level"
private const val KeyVenueName = "group-setup-venue-name"
private const val KeyVenueAddress = "group-setup-venue-address"
private const val KeyMensalistaPriority = "group-setup-mensalista-priority"
private const val KeyPromotionMode = "group-setup-promotion-mode"
private const val KeyAutoConfirm = "group-setup-auto-confirm"
private const val KeyMonthlyLateJoinPolicy = "group-setup-monthly-late-join-policy"
private const val KeyCreationCommand = "group-setup-create-command-key"
