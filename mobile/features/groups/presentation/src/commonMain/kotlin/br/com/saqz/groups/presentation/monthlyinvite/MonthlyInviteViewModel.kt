package br.com.saqz.groups.presentation.monthlyinvite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.UpdateAthleteCommand
import br.com.saqz.groups.domain.communication.GroupWhatsAppGateway
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.membership.MonthlyInviteError
import br.com.saqz.groups.domain.membership.MonthlyInviteGateway
import br.com.saqz.groups.domain.membership.MonthlyInviteLinkState
import br.com.saqz.groups.domain.membership.MonthlyInvitePerson
import br.com.saqz.groups.domain.membership.MonthlyInvitePhoneCheck
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tela "Convidar mensalista" (CONV-010). A lista "Quem vai receber" vive só aqui até o envio; o lote leva um
 * `requestId` que só muda quando a lista muda, então toque duplo (ou reenvio da rede) não duplica nada.
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("TooManyFunctions")
class MonthlyInviteViewModel(
    private val groupId: String,
    private val gateway: MonthlyInviteGateway,
    private val groupGateway: GroupGateway,
    private val athleteGateway: AthleteGateway,
    private val whatsAppGateway: GroupWhatsAppGateway,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : MviViewModel<MonthlyInviteState, MonthlyInviteIntent, MonthlyInviteEffect>(MonthlyInviteState()) {
    private var loadGeneration = 0
    private var checkGeneration = 0
    private var undo: Pair<Int, MonthlyInvitePersonUi>? = null

    private var requestId: String
        get() = saved.get<String>(KEY_REQUEST) ?: Uuid.random().toString().also { saved[KEY_REQUEST] = it }
        set(value) {
            saved[KEY_REQUEST] = value
        }

    init {
        load()
    }

    override fun handleIntent(intent: MonthlyInviteIntent) {
        when (intent) {
            MonthlyInviteIntent.Retry -> load()
            MonthlyInviteIntent.Refresh -> if (!isFirstAppearance()) load(soft = true)
            MonthlyInviteIntent.OpenSheet -> update { it.copy(sheet = MonthlyInviteSheet()) }
            MonthlyInviteIntent.CloseSheet -> update { it.copy(sheet = null) }
            is MonthlyInviteIntent.UpdateName -> updateSheet { it.copy(name = intent.value, nameError = false) }
            is MonthlyInviteIntent.UpdatePhone -> updatePhone(intent.value)
            is MonthlyInviteIntent.Add -> add(intent.keepOpen)
            MonthlyInviteIntent.Promote -> promote()
            is MonthlyInviteIntent.Remove -> remove(intent.index)
            MonthlyInviteIntent.Undo -> restore()
            MonthlyInviteIntent.ToggleMessage -> update { it.copy(messageOpen = !it.messageOpen) }
            MonthlyInviteIntent.Send -> send()
            MonthlyInviteIntent.CopyMessage -> copy(withMessage = true)
            MonthlyInviteIntent.CopyLink -> copy(withMessage = false)
            MonthlyInviteIntent.ConfigureFee -> emit(MonthlyInviteEffect.OpenFeeSettings(groupId))
            MonthlyInviteIntent.OpenPlans -> emit(MonthlyInviteEffect.OpenPlans)
            MonthlyInviteIntent.DismissToast,
            MonthlyInviteIntent.DismissActionFailed,
            MonthlyInviteIntent.DismissSubscriptionRequired,
            -> dismiss(intent)
        }
    }

    private fun dismiss(intent: MonthlyInviteIntent) = when (intent) {
        MonthlyInviteIntent.DismissToast -> {
            undo = null
            update { it.copy(toast = null) }
        }
        MonthlyInviteIntent.DismissActionFailed -> update { it.copy(actionFailed = false) }
        else -> update { it.copy(subscriptionRequired = false) }
    }

    /** [soft]: volta de Editar grupo, sem esqueleto; a lista em montagem fica. */
    private fun load(soft: Boolean = false) {
        val generation = ++loadGeneration
        if (!soft) update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val id = GroupId(groupId)
            val available = async { whatsAppGateway.availability() }
            val group = async { groupGateway.read(id) }
            val profile = async { athleteGateway.ownProfile() }
            val invites = gateway.list(id)
            val groupResult = group.await()
            val profileResult = profile.await()
            val availableResult = available.await()
            if (generation != loadGeneration) return@launch
            if (invites !is SaqzResult.Success || groupResult !is SaqzResult.Success || profileResult !is SaqzResult.Success) {
                update { it.copy(isLoading = false, loadFailed = !soft || it.loadFailed) }
                return@launch
            }
            update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    groupName = groupResult.value.group.name,
                    senderName = profileResult.value.displayName,
                    defaultFee = invites.value.defaultFee,
                    linkUrl = invites.value.link.url.takeIf { invites.value.link.state == MonthlyInviteLinkState.ACTIVE },
                    // Sem resposta, o envio tenta e o backend diz se o WhatsApp está desligado.
                    whatsAppAvailable = (availableResult as? SaqzResult.Success)?.value ?: true,
                )
            }
        }
    }

    private fun updateSheet(transform: (MonthlyInviteSheet) -> MonthlyInviteSheet) =
        update { state -> state.sheet?.let { state.copy(sheet = transform(it)) } ?: state }

    private fun updatePhone(raw: String) {
        val digits = MonthlyInviteRules.phoneDigits(raw)
        val generation = ++checkGeneration
        val complete = digits.length == MonthlyInviteRules.PHONE_DIGITS
        val format = if (complete) MonthlyInviteRules.phoneError(digits) else null
        val duplicate = if (complete) state.value.people.firstOrNull { it.phone == digits }?.name else null
        updateSheet {
            it.copy(
                phone = digits,
                phoneError = format ?: duplicate?.let { MonthlyInvitePhoneError.Duplicate },
                duplicateOf = duplicate,
                invitedName = null,
                match = null,
            )
        }
        if (!complete || format != null || duplicate != null) return
        viewModelScope.launch {
            val result = gateway.check(GroupId(groupId), digits)
            if (generation != checkGeneration || state.value.sheet?.phone != digits) return@launch
            val found = (result as? SaqzResult.Success)?.value ?: return@launch
            val name = found.name.orEmpty()
            updateSheet { sheet ->
                when (found.result) {
                    MonthlyInvitePhoneCheck.ALREADY_MENSALISTA -> sheet.copy(match = MonthlyInviteMatch.Mensalista(name))
                    MonthlyInvitePhoneCheck.ALREADY_AVULSO -> found.userId?.let {
                        sheet.copy(match = MonthlyInviteMatch.Avulso(name, it))
                    } ?: sheet
                    MonthlyInvitePhoneCheck.ALREADY_INVITED ->
                        sheet.copy(phoneError = MonthlyInvitePhoneError.AlreadyInvited, invitedName = name)
                    MonthlyInvitePhoneCheck.FREE, MonthlyInvitePhoneCheck.UNKNOWN -> sheet
                }
            }
        }
    }

    private fun add(keepOpen: Boolean) {
        val sheet = state.value.sheet ?: return
        val nameOk = MonthlyInviteRules.isValidName(sheet.name)
        val phoneError = sheet.phoneError ?: MonthlyInviteRules.phoneError(sheet.phone)
        if (!nameOk || phoneError != null) {
            updateSheet { it.copy(nameError = !nameOk, phoneError = phoneError) }
            return
        }
        if (sheet.match != null) return
        val person = MonthlyInvitePersonUi(sheet.name.trim(), sheet.phone)
        listChanged()
        checkGeneration++
        update { it.copy(people = it.people + person, sheet = if (keepOpen) MonthlyInviteSheet() else null) }
    }

    /** "Promover a mensalista": o mesmo PATCH da edição de membro, com a mensalidade do grupo; nunca manda WhatsApp. */
    private fun promote() {
        val sheet = state.value.sheet ?: return
        val match = sheet.match as? MonthlyInviteMatch.Avulso ?: return
        if (sheet.promoting) return
        updateSheet { it.copy(promoting = true) }
        viewModelScope.launch {
            val id = GroupId(groupId)
            val entry = (athleteGateway.roster(id, AthleteRosterFilter(includeInactive = true)) as? SaqzResult.Success)
                ?.value?.firstOrNull { it.userId == match.userId }
            val promoted = entry != null && athleteGateway.updateAthlete(
                UpdateAthleteCommand(
                    groupId = id,
                    userId = entry.userId,
                    position = entry.position,
                    membershipType = AthleteMembershipType.MENSALISTA,
                    active = entry.active,
                    nickname = entry.nickname,
                    secondaryPosition = entry.secondaryPosition,
                    level = entry.level,
                    preferredSide = entry.preferredSide,
                    heightCm = entry.heightCm,
                ),
            ) is SaqzResult.Success
            update {
                if (promoted) {
                    it.copy(sheet = null, toast = MonthlyInviteToast.Promoted(MonthlyInviteRules.firstName(match.name)))
                } else {
                    it.copy(sheet = it.sheet?.copy(promoting = false), actionFailed = true)
                }
            }
        }
    }

    private fun remove(index: Int) {
        val person = state.value.people.getOrNull(index) ?: return
        undo = index to person
        listChanged()
        update {
            it.copy(
                people = it.people.filterIndexed { i, _ -> i != index },
                toast = MonthlyInviteToast.Removed(MonthlyInviteRules.firstName(person.name)),
            )
        }
    }

    private fun restore() {
        val (index, person) = undo ?: return
        undo = null
        listChanged()
        update {
            val list = it.people.toMutableList().apply { add(index.coerceAtMost(size), person) }
            it.copy(people = list, toast = null)
        }
    }

    /** Lista mudou: o próximo envio é outro lote, e as recusas do envio anterior já não batem com as posições. */
    private fun listChanged() {
        requestId = Uuid.random().toString()
        update { it.copy(rowErrors = emptyMap()) }
    }

    private fun send() {
        val current = state.value
        if (!current.canSend) return
        update { it.copy(sending = true) }
        viewModelScope.launch {
            val result = gateway.send(GroupId(groupId), requestId, current.people.map { MonthlyInvitePerson(it.name, it.phone) })
            when (result) {
                is SaqzResult.Success -> {
                    requestId = Uuid.random().toString()
                    update {
                        it.copy(
                            sending = false,
                            people = emptyList(),
                            planLimit = null,
                            linkUrl = result.value.url,
                            messageOpen = false,
                            toast = MonthlyInviteToast.Sent(result.value.enqueued),
                        )
                    }
                }
                is SaqzResult.Failure -> update { it.copy(sending = false).withFailure(result.error) }
            }
        }
    }

    private fun MonthlyInviteState.withFailure(error: MonthlyInviteError): MonthlyInviteState = when (error) {
        is MonthlyInviteError.InvalidPeople -> copy(rowErrors = error.errors.associate { it.index to it.reason })
        is MonthlyInviteError.PlanLimit -> copy(planLimit = error.limit)
        MonthlyInviteError.MonthlyFeeRequired -> copy(defaultFee = null)
        MonthlyInviteError.WhatsAppUnavailable -> copy(whatsAppAvailable = false)
        MonthlyInviteError.SubscriptionRequired -> copy(subscriptionRequired = true)
        else -> copy(actionFailed = true)
    }

    /** Modo sem WhatsApp: pedir o link conta como envio e renova a validade (CONV-011). */
    private fun copy(withMessage: Boolean) {
        if (state.value.copying) return
        update { it.copy(copying = true) }
        viewModelScope.launch {
            when (val result = gateway.copyLink(GroupId(groupId))) {
                is SaqzResult.Success -> {
                    val s = state.value
                    val url = result.value.url
                    emit(MonthlyInviteEffect.Copy(if (withMessage) monthlyInviteMessage(null, s.senderName, s.groupName, url) else url))
                    update { it.copy(copying = false, linkUrl = url, toast = MonthlyInviteToast.Copied) }
                }
                is SaqzResult.Failure -> update { it.copy(copying = false).withFailure(result.error) }
            }
        }
    }

    private companion object {
        const val KEY_REQUEST = "monthlyInviteRequestId"
    }
}
