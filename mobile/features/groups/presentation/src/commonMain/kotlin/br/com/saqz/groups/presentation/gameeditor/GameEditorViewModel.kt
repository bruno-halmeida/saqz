package br.com.saqz.groups.presentation.gameeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrlPlain
import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.core.common.formatting.sanitizeBrlInput
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupTrialAccessInfo
import br.com.saqz.groups.domain.group.GroupTrialAccessPort
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.domain.place.PlacesGateway
import br.com.saqz.groups.model.GameEditorDraft
import br.com.saqz.groups.model.GameEditorForm
import br.com.saqz.groups.model.GameEditorMode
import br.com.saqz.groups.presentation.GameCapacityLimits
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.place.VenueSearch
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import br.com.saqz.groups.presentation.communication.NotificationPromptAudience
import br.com.saqz.groups.presentation.communication.NotificationPromptOffer
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Suppress("LargeClass", "LongParameterList")
class GameEditorViewModel(
    val groupId: String,
    private val gameId: String?,
    private val savedState: SavedStateHandle,
    private val gameGateway: GameGateway,
    private val groupGateway: GroupGateway,
    private val profileGateway: GroupProfileGateway,
    private val trialAccess: GroupTrialAccessPort? = null,
    placesGateway: PlacesGateway? = null,
    /** Jogo criado é a hora de explicar os avisos ao organizador (pedidos de entrada, confirmações, prazo). */
    private val notificationPrompt: NotificationPromptOffer? = null,
) : MviViewModel<GameEditorState, GameEditorIntent, GameEditorEffect>(
    initialState = GameEditorState(isLoading = true),
) {
    private var loadGeneration = 0
    private var pendingDraft: PendingDraft? = restorePendingDraft()
    private val venueSearch = VenueSearch(viewModelScope, placesGateway)

    init {
        viewModelScope.launch {
            venueSearch.state.collect { search -> update { it.copy(venueSearch = search) } }
        }
        venueSearch.refreshAvailability()
        load()
    }

    override fun handleIntent(intent: GameEditorIntent) {
        when (intent) {
            GameEditorIntent.Retry -> load()
            GameEditorIntent.Submit -> submit()
            GameEditorIntent.OpenDateTimePicker -> Unit
            is GameEditorIntent.SaveDateTime -> updateForm {
                copy(localDate = intent.date, localTime = intent.time)
            }
            is GameEditorIntent.SelectDuration -> updateForm { copy(durationMinutes = intent.minutes) }
            is GameEditorIntent.UpdateVenueName,
            is GameEditorIntent.SelectPlaceSuggestion,
            GameEditorIntent.DismissPlaceSuggestions,
            is GameEditorIntent.UpdateVenueAddress,
            -> handleVenueIntent(intent)
            is GameEditorIntent.UpdateCapacity -> updateForm {
                copy(capacity = intent.capacity.coerceAtLeast(MIN_CAPACITY))
            }
            is GameEditorIntent.UpdateGameFee -> updateForm {
                val text = sanitizeBrlInput(intent.text)
                copy(gameFeeText = text, gameFeeCents = parseBrlToCents(text))
            }
            is GameEditorIntent.SelectConfirmationLead -> updateForm {
                copy(confirmationLeadMinutes = intent.minutes)
            }
            is GameEditorIntent.ToggleRecurring -> if (state.value.recurrenceOffered) {
                updateForm { copy(recurring = intent.value) }
            }
            GameEditorIntent.DismissConflict -> update { it.copy(hasConflict = false, conflictGameId = null) }
            GameEditorIntent.OpenExistingGame -> openExistingGame()
            GameEditorIntent.DismissSubscriptionRequired -> update { it.copy(subscriptionRequired = false) }
            GameEditorIntent.RefreshPlan -> refreshPlan()
        }
    }

    /** Quadra do jogo: digitação, sugestões do Google Places e endereço. */
    private fun handleVenueIntent(intent: GameEditorIntent) {
        when (intent) {
            is GameEditorIntent.UpdateVenueName -> {
                updateForm {
                    copy(venue = (venue ?: emptyVenue()).copy(name = intent.name), venueEditable = true)
                }
                venueSearch.onQuery(intent.name)
            }
            is GameEditorIntent.SelectPlaceSuggestion -> venueSearch.select(intent.suggestion) { place ->
                // Um lugar do Google não é uma quadra salva do grupo: sem `venueId`, só os textos.
                updateForm {
                    copy(
                        venue = GameVenue(
                            name = place.name.take(VENUE_NAME_LIMIT),
                            address = place.address.take(VENUE_ADDRESS_LIMIT),
                        ),
                        venueEditable = true,
                    )
                }
            }
            GameEditorIntent.DismissPlaceSuggestions -> venueSearch.dismiss()
            is GameEditorIntent.UpdateVenueAddress -> updateForm {
                copy(venue = (venue ?: emptyVenue()).copy(address = intent.address), venueEditable = true)
            }
            else -> Unit
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update {
            it.copy(
                isLoading = true,
                loadFailed = false,
                error = null,
                isSaving = false,
                saveFailed = false,
                recurrenceFailed = false,
                hasConflict = false,
                conflictGameId = null,
            )
        }
        viewModelScope.launch {
            val groupResult = groupGateway.read(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            if (groupResult is SaqzResult.Failure) {
                showLoadFailure(generation, groupResult.error.toUiError())
                return@launch
            }
            val group = (groupResult as SaqzResult.Success).value.group
            val trial = trialAccess?.read(GroupId(groupId))
            val profile = group.profile
            val defaultForm = defaultForm(profile, group.name)
                .withGameFee(group.financeDefaults?.defaultGameFeeCents)
            if (gameId == null) {
                update {
                    it.copy(
                        isLoading = false,
                        groupName = group.name,
                        zoneId = group.timeZone.id,
                        form = restoreForm(defaultForm),
                        // Perfil incompleto não aceita o `PUT` do grupo: aí o toggle nem aparece.
                        recurrenceOffered = profile?.modality != null && profile.composition != null,
                        regularSlots = profile?.regularSlots.orEmpty(),
                        groupHasVenue = profile?.defaultVenue != null,
                    ).withTrial(trial)
                }
                return@launch
            }
            val gameResult = gameGateway.read(GroupId(groupId), gameId)
            if (generation != loadGeneration) return@launch
            when (gameResult) {
                is SaqzResult.Failure -> showLoadFailure(generation, gameResult.error.toUiError())
                is SaqzResult.Success -> {
                    val game = gameResult.value.game
                    val serverForm = defaultForm.copy(
                        title = game.title,
                        localDate = game.localDate,
                        localTime = game.localTime,
                        durationMinutes = game.durationMinutes,
                        gameFeeCents = game.gameFeeCents,
                        gameFeeText = game.gameFeeCents?.let(::formatBrlPlain).orEmpty(),
                        venue = game.venue,
                        // Jogo antigo com 2 ou 3 vagas abre no mínimo; salvar grava o mínimo e a tela avisa.
                        capacity = game.capacity.coerceAtLeast(MIN_CAPACITY),
                        confirmationLeadMinutes = confirmationLeadMinutes(
                            game.startsAt,
                            game.confirmationDeadline,
                        ) ?: defaultForm.confirmationLeadMinutes,
                    )
                    update {
                        it.copy(
                            isLoading = false,
                            loadFailed = false,
                        groupName = group.name,
                        zoneId = group.timeZone.id,
                        form = restoreForm(serverForm),
                        groupHasVenue = profile?.defaultVenue != null,
                            versionToken = gameResult.value.version.value,
                            confirmedCount = game.confirmedCount,
                            legacyCapacity = game.capacity.takeIf { capacity -> capacity < MIN_CAPACITY },
                            originalStart = "${game.localDate}T${game.localTime}",
                        ).withTrial(trial)
                    }
                }
            }
        }
    }

    private fun defaultForm(profile: GroupProfile?, groupName: String) = GameEditorFields(
        title = if (gameId == null) groupName else "",
        durationMinutes = profile?.regularSlots?.firstOrNull()?.durationMinutes
            ?: profile?.defaultDurationMinutes ?: DEFAULT_DURATION,
        venue = profile?.defaultVenue?.toGameVenue(),
        // O padrão do grupo pode ser antigo (2 ou 3): o jogo novo parte do mínimo de hoje.
        capacity = (profile?.defaultCapacity ?: DEFAULT_CAPACITY).coerceAtLeast(MIN_CAPACITY),
        confirmationLeadMinutes = profile?.defaultConfirmationLeadMinutes ?: DEFAULT_LEAD,
    )

    private fun submit() {
        val current = state.value
        val errors = validateGameEditor(current.form)
        if (errors.isNotEmpty()) {
            update { it.copy(validationErrors = errors, submitAttempts = it.submitAttempts + 1) }
            return
        }
        if (current.isSaving || current.planBlocked) return
        val commandKey = savedState.get<String>(KeyCommand) ?: Uuid.random().toString().also {
            savedState[KeyCommand] = it
        }
        val draft = buildDraft(current, commandKey)
        val command = buildCommand(draft)
        val attempt = if (pendingDraft != null) {
            LastAttempt.ResumePendingDraft(commandKey)
        } else if (gameId == null) {
            LastAttempt.Create(commandKey)
        } else {
            val versionToken = current.versionToken ?: return
            LastAttempt.Edit(gameId, versionToken, commandKey, publishAfterEdit = false)
        }
        update {
            it.copy(isSaving = true, saveFailed = false, recurrenceFailed = false, hasConflict = false, conflictGameId = null)
        }
        viewModelScope.launch {
            val result = when (attempt) {
                is LastAttempt.Create -> createAndPublish(command)
                is LastAttempt.ResumePendingDraft -> resumePendingDraft(attempt.commandKey, command)
                is LastAttempt.Edit -> editAndMaybePublish(attempt, command)
            }
            when (result) {
                is SaqzResult.Success -> {
                    // O jogo já existe e está publicado; o rascunho pendente fica guardado para o
                    // retry resumir sem criar outro jogo caso só a repetição falhe.
                    val recurrence = saveGroupDefaults(groupId, current, gameId == null, groupGateway, profileGateway)
                    if (recurrence is SaqzResult.Failure) {
                        update { it.copy(isSaving = false, recurrenceFailed = true, error = recurrence.error.toUiError()) }
                        return@launch
                    }
                    clearPendingDraft()
                    savedState.remove<String>(KeyCommand)
                    update { it.copy(isSaving = false, saveFailed = false) }
                    if (gameId == null) notificationPrompt?.offer(NotificationPromptAudience.Organizer)
                    emit(GameEditorEffect.Saved(created = gameId == null))
                }
                is SaqzResult.Failure -> showSubmitFailure(result.error)
            }
        }
    }

    private fun showSubmitFailure(error: GameError) {
        when (error) {
            is GameError.Conflict -> update {
                it.copy(
                    isSaving = false,
                    hasConflict = true,
                    conflictGameId = error.conflictGameId,
                )
            }
            is GameError.AfterPlanEnd -> update {
                // O servidor é a autoridade: o limite dele passa a valer e a tela bloqueia como se tivesse sabido antes.
                val known = it.copy(planLimit = error.startsBefore ?: it.planLimit, isSaving = false).withPlanBlock()
                if (known.planBlocked) known else known.copy(saveFailed = true, error = error.toUiError())
            }
            GameError.VersionConflict -> if (gameId == null) {
                update { it.copy(isSaving = false, saveFailed = true, error = error.toUiError()) }
            } else {
                load()
            }
            else -> if (error.toUiError() == GroupUiError.SubscriptionRequired) {
                // Sem plano ativo "tentar de novo" nunca resolve: o diálogo oferece a assinatura.
                update { it.copy(isSaving = false, subscriptionRequired = true) }
            } else {
                update { it.copy(isSaving = false, saveFailed = true, error = error.toUiError()) }
            }
        }
    }

    private fun refreshPlan() {
        val current = state.value
        if (current.isLoading || current.zoneId.isBlank()) return
        viewModelScope.launch {
            val trial = trialAccess?.read(GroupId(groupId)) ?: return@launch
            update { it.withTrial(trial) }
        }
    }

    private fun openExistingGame() {
        val current = state.value
        current.conflictGameId?.let {
            emit(GameEditorEffect.OpenGameDetail(it))
            return
        }
        viewModelScope.launch {
            val result = gameGateway.list(GroupId(groupId))
            if (result is SaqzResult.Success) {
                result.value.firstOrNull {
                    it.id != gameId &&
                        it.localDate == current.form.localDate &&
                        it.localTime == current.form.localTime
                }?.let { emit(GameEditorEffect.OpenGameDetail(it.id)) }
            }
        }
    }

    private suspend fun createAndPublish(
        command: GameWriteCommand,
    ): SaqzResult<VersionedGame, GameError> = when (
        val created = gameGateway.create(GroupId(groupId), command)
    ) {
        is SaqzResult.Failure -> created
        is SaqzResult.Success -> {
            savePendingDraft(created.value)
            publishIfDraft(created.value)
        }
    }

    private suspend fun resumePendingDraft(
        commandKey: String,
        command: GameWriteCommand,
    ): SaqzResult<VersionedGame, GameError> {
        val pending = pendingDraft ?: return SaqzResult.Failure(GameError.Data(DataError.Connectivity))
        return when (val current = gameGateway.read(GroupId(groupId), pending.gameId)) {
            is SaqzResult.Failure -> current
            is SaqzResult.Success -> {
                val serverFingerprint = gameFingerprint(current.value.game)
                val submittedFingerprint = pending.submittedStateFingerprint
                refreshPendingDraft(current.value)
                if (submittedFingerprint == null || submittedFingerprint != serverFingerprint) {
                    SaqzResult.Failure(GameError.VersionConflict)
                } else if (
                    current.value.game.status == GameStatus.Published &&
                    commandFingerprint(command, current.value.game.gameFeeCents) == submittedFingerprint
                ) {
                    current
                } else {
                    editAndMaybePublish(
                        LastAttempt.Edit(
                            current.value.game.id,
                            current.value.version.value,
                            commandKey,
                            publishAfterEdit = current.value.game.status != GameStatus.Published,
                        ),
                        command.withResolvedGameFee(current.value.game.gameFeeCents),
                    )
                }
            }
        }
    }

    private suspend fun editAndMaybePublish(
        attempt: LastAttempt.Edit,
        command: GameWriteCommand,
    ): SaqzResult<VersionedGame, GameError> {
        val edited = gameGateway.edit(
            GroupId(groupId), attempt.gameId, GameVersionToken(attempt.versionToken), command,
        )
        if (!attempt.publishAfterEdit) return edited
        return when (edited) {
            is SaqzResult.Failure -> edited
            is SaqzResult.Success -> {
                savePendingDraft(edited.value)
                publishIfDraft(edited.value)
            }
        }
    }

    private fun savePendingDraft(versioned: VersionedGame) {
        pendingDraft = PendingDraft(
            versioned.game.id,
            versioned.version.value,
            gameFingerprint(versioned.game),
        )
        savedState[KeyPendingGameId] = versioned.game.id
        savedState[KeyPendingVersion] = versioned.version.value
        savedState[KeyPendingFingerprint] = gameFingerprint(versioned.game)
    }

    private fun refreshPendingDraft(versioned: VersionedGame) {
        pendingDraft = pendingDraft?.copy(versionToken = versioned.version.value)
        savedState[KeyPendingVersion] = versioned.version.value
    }

    private fun clearPendingDraft() {
        pendingDraft = null
        savedState.remove<String>(KeyPendingGameId)
        savedState.remove<String>(KeyPendingVersion)
        savedState.remove<String>(KeyPendingFingerprint)
    }

    private suspend fun publishIfDraft(
        versioned: VersionedGame,
    ): SaqzResult<VersionedGame, GameError> = if (versioned.game.status == GameStatus.Published) {
        SaqzResult.Success(versioned)
    } else {
        gameGateway.lifecycle(
            GroupId(groupId),
            versioned.game.id,
            versioned.version,
            GameLifecycleAction.Publish,
        )
    }

    private fun buildDraft(state: GameEditorState, commandKey: String): GameEditorDraft {
        val startsAt = localStart(state.form.localDate, state.form.localTime, state.zoneId)
        val deadline = startsAt?.let { start ->
            runCatching { (Instant.parse(start) - state.form.confirmationLeadMinutes.minutes).toString() }.getOrNull()
        }
        return GameEditorDraft(
            groupId = groupId,
            gameId = gameId,
            seriesId = null,
            commandKey = commandKey,
            version = state.versionToken?.let(::GameVersionToken),
            mode = GameEditorMode.ONE_TIME,
            form = GameEditorForm(
                title = state.form.title,
                venue = state.form.venue,
                localDate = state.form.localDate,
                localTime = state.form.localTime,
                zoneId = state.zoneId,
                startsAt = startsAt.orEmpty(),
                durationMinutes = state.form.durationMinutes.toString(),
                capacity = state.form.capacity.toString(),
                confirmationDeadline = deadline.orEmpty(),
                gameFeeCents = state.form.gameFeeCents,
            ),
        )
    }

    private fun showLoadFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun updateForm(transform: GameEditorFields.() -> GameEditorFields) {
        update { state ->
            val form = state.form.transform()
            if (form.copy(venueEditable = false) != state.form.copy(venueEditable = false)) {
                savedState.remove<String>(KeyCommand)
            }
            persistForm(form)
            state.copy(
                form = form,
                validationErrors = emptySet(),
                recurrenceFailed = false,
            ).withPlanBlock()
        }
    }

    private fun persistForm(form: GameEditorFields) {
        savedState[KeyDate] = form.localDate
        savedState[KeyTitle] = form.title
        savedState[KeyTime] = form.localTime
        savedState[KeyDuration] = form.durationMinutes
        savedState[KeyCapacity] = form.capacity
        savedState[KeyLead] = form.confirmationLeadMinutes
        savedState[KeyRecurring] = form.recurring
        savedState[KeyGameFee] = form.gameFeeText
        form.venue?.let {
            savedState[KeyVenueName] = it.name
            savedState[KeyVenueAddress] = it.address
        }
    }

    private fun restoreForm(form: GameEditorFields): GameEditorFields = form.copy(
        title = savedState.get<String>(KeyTitle) ?: form.title,
        localDate = savedState.get<String>(KeyDate) ?: form.localDate,
        localTime = savedState.get<String>(KeyTime) ?: form.localTime,
        durationMinutes = savedState.get<Int>(KeyDuration) ?: form.durationMinutes,
        capacity = savedState.get<Int>(KeyCapacity) ?: form.capacity,
        confirmationLeadMinutes = savedState.get<Int>(KeyLead) ?: form.confirmationLeadMinutes,
        recurring = savedState.get<Boolean>(KeyRecurring) ?: form.recurring,
        gameFeeText = savedState.get<String>(KeyGameFee) ?: form.gameFeeText,
        gameFeeCents = if (savedState.get<String>(KeyGameFee) != null) {
            parseBrlToCents(savedState.get<String>(KeyGameFee).orEmpty())
        } else {
            form.gameFeeCents
        },
        venue = savedState.get<String>(KeyVenueName)?.let { name ->
            (form.venue ?: emptyVenue()).copy(
                name = name,
                address = savedState.get<String>(KeyVenueAddress).orEmpty(),
            )
        } ?: form.venue,
    )

    private fun restorePendingDraft(): PendingDraft? {
        val gameId = savedState.get<String>(KeyPendingGameId) ?: return null
        val version = savedState.get<String>(KeyPendingVersion) ?: return null
        return PendingDraft(gameId, version, savedState.get<String>(KeyPendingFingerprint))
    }

    private sealed interface LastAttempt {
        val commandKey: String
        data class Create(override val commandKey: String) : LastAttempt
        data class ResumePendingDraft(override val commandKey: String) : LastAttempt
        data class Edit(
            val gameId: String,
            val versionToken: String,
            override val commandKey: String,
            val publishAfterEdit: Boolean,
        ) : LastAttempt
    }

    private companion object {
        const val MIN_CAPACITY = GameCapacityLimits.Min
        const val VENUE_NAME_LIMIT = 120
        const val VENUE_ADDRESS_LIMIT = 300
        const val DEFAULT_CAPACITY = 12
        const val DEFAULT_DURATION = 120
        const val DEFAULT_LEAD = 360
        const val KeyCommand = "game-editor-command-key"
        const val KeyDate = "game-editor-date"
        const val KeyTitle = "game-editor-title"
        const val KeyTime = "game-editor-time"
        const val KeyDuration = "game-editor-duration"
        const val KeyCapacity = "game-editor-capacity"
        const val KeyLead = "game-editor-confirmation-lead"
        const val KeyRecurring = "game-editor-recurring"
        const val KeyGameFee = "game-editor-game-fee"
        const val KeyVenueName = "game-editor-venue-name"
        const val KeyVenueAddress = "game-editor-venue-address"
        const val KeyPendingGameId = "game-editor-pending-game-id"
        const val KeyPendingVersion = "game-editor-pending-version"
        const val KeyPendingFingerprint = "game-editor-pending-fingerprint"
    }
}

private fun buildCommand(draft: GameEditorDraft): GameWriteCommand =
    GameWriteCommand(
        requestId = draft.commandKey,
        title = draft.form.title.takeIf(String::isNotBlank),
        venue = draft.form.venue,
        localDate = draft.form.localDate,
        localTime = draft.form.localTime,
        zoneId = draft.form.zoneId,
        startsAt = draft.form.startsAt,
        durationMinutes = draft.form.durationMinutes.toIntOrNull(),
        capacity = draft.form.capacity.toIntOrNull(),
        confirmationDeadline = draft.form.confirmationDeadline,
        // O campo já vem com o padrão do grupo: o que está nele vale, e vazio é jogo sem cobrança.
        gameFeeCents = draft.form.gameFeeCents,
        useDefaultGameFee = false,
    )

/** Fatos do plano lidos do servidor; o bloqueio do fim do plano depende do jogo e do limite. */
private fun GameEditorState.withTrial(trial: GroupTrialAccessInfo?): GameEditorState {
    val active = trial?.planActive == true
    // Servidor antigo não manda `gamesUntil`: vale o fim do teste, arredondado para 0h do dia como o servidor faz.
    // Teste vencido não bloqueia: o servidor responde SUBSCRIPTION_REQUIRED e o diálogo oferece a assinatura.
    val ended = trial?.trialEnded == true || trial?.readOnly == true
    val limit = trial?.gamesUntil?.takeUnless { ended }
        ?: trial?.endsAt?.takeUnless { active || ended }?.let { startOfDay(it, zoneId) }
    return copy(
        planLimit = limit,
        planLimitIsTrial = !active && (trial?.activeTrialDays != null || trial?.gamesUntil == null),
        isOwner = trial?.isOwner == true,
        planActive = active,
        trialEnded = trial?.trialEnded == true,
    ).withPlanBlock()
}

private fun GameEditorState.withPlanBlock(): GameEditorState {
    val limit = planLimit
    val lastDay = limit?.let { lastDayBefore(it, zoneId) }
    val starts = if (form.hasDateTime && zoneId.isNotBlank()) localStart(form.localDate, form.localTime, zoneId) else null
    val changed = originalStart == null || originalStart != "${form.localDate}T${form.localTime}"
    val blocked = limit != null && starts != null && changed &&
        runCatching { Instant.parse(starts) >= Instant.parse(limit) }.getOrDefault(false)
    return copy(planLastDay = lastDay, planBlocked = blocked)
}

private fun localStart(date: String, time: String, zoneId: String): String? = runCatching {
    LocalDateTime.parse("${date}T$time").toInstant(TimeZone.of(zoneId)).toString()
}.getOrNull()

/** 0h do dia de [instant] no fuso do grupo. */
private fun startOfDay(instant: String, zoneId: String): String? = runCatching {
    val zone = TimeZone.of(zoneId)
    val day = Instant.parse(instant).toLocalDateTime(zone).date
    day.atStartOfDayIn(zone).toString()
}.getOrNull()

/** O último dia inteiro antes de [limit], no fuso do grupo (aaaa-mm-dd). */
private fun lastDayBefore(limit: String, zoneId: String): String? = runCatching {
    (Instant.parse(limit) - 1.milliseconds).toLocalDateTime(TimeZone.of(zoneId)).date.toString()
}.getOrNull()

private data class PendingDraft(
    val gameId: String,
    val versionToken: String,
    val submittedStateFingerprint: String?,
)

private fun commandFingerprint(command: GameWriteCommand, resolvedDefaultGameFeeCents: Long?): String = listOf(
    command.title,
    command.venue?.venueId,
    command.venue?.name,
    command.venue?.address,
    command.venue?.court,
    command.localDate,
    command.localTime,
    command.zoneId,
    command.startsAt,
    command.durationMinutes,
    command.capacity,
    command.confirmationDeadline,
    if (command.useDefaultGameFee) resolvedDefaultGameFeeCents else command.gameFeeCents,
).fingerprint()

private fun gameFingerprint(game: Game): String = listOf(
    game.title,
    game.venue.venueId,
    game.venue.name,
    game.venue.address,
    game.venue.court,
    game.localDate,
    game.localTime,
    game.zoneId,
    game.startsAt,
    game.durationMinutes,
    game.capacity,
    game.confirmationDeadline,
    game.gameFeeCents,
).fingerprint()

private fun GameWriteCommand.withResolvedGameFee(resolvedGameFeeCents: Long?): GameWriteCommand =
    if (!useDefaultGameFee) this else copy(
        gameFeeCents = resolvedGameFeeCents,
        useDefaultGameFee = false,
    )

private fun List<Any?>.fingerprint(): String = joinToString(FINGERPRINT_SEPARATOR) { it?.toString()?.trim().orEmpty() }

private const val FINGERPRINT_SEPARATOR = "\u001F"

private fun confirmationLeadMinutes(startsAt: String, deadline: String): Int? = runCatching {
    (Instant.parse(startsAt) - Instant.parse(deadline)).inWholeMinutes.toInt()
}.getOrNull()

private fun GameEditorFields.withGameFee(cents: Long?) =
    copy(gameFeeCents = cents, gameFeeText = cents?.let(::formatBrlPlain).orEmpty())

private fun emptyVenue() = GameVenue(name = "", address = "")

private fun GroupVenue.toGameVenue(): GameVenue = GameVenue(id, name, address, court)
