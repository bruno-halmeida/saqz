package br.com.saqz.groups.presentation.details

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.port.GroupOnboardingMemory

/** Os cinco itens de "Deixe o grupo redondo", na ordem em que aparecem. */
enum class GroupChecklistItem { WhatsApp, Mensalistas, Pix, Rules, Recurrence }

@Immutable
data class GroupChecklistRowUi(val item: GroupChecklistItem, val done: Boolean)

/** A checklist só existe enquanto falta algum item; o primeiro não feito é o "Agora". */
@Immutable
data class GroupChecklistUi(val rows: List<GroupChecklistRowUi>) {
    val current: GroupChecklistItem? = rows.firstOrNull { !it.done }?.item
}

/**
 * Derivação da checklist a partir do que o grupo já tem. Nada é marcado à mão, exceto o que não
 * dá para ler do grupo: "regras abertas uma vez" e o "deixar para depois", que vêm da memória
 * local do aparelho. Com todos feitos, ou durante o "deixar para depois", devolve `null`.
 *
 * Sem [whatsAppBinding] (WhatsApp desligado no servidor) o item do WhatsApp nem existe: não
 * aparece e não conta para a checklist ficar completa.
 */
internal fun groupChecklist(
    group: Group,
    rosterHasMensalista: Boolean,
    whatsApp: GroupWhatsAppStatus?,
    memory: GroupOnboardingMemory,
    nowEpochMillis: Long,
    whatsAppBinding: Boolean,
): GroupChecklistUi? {
    val snoozed = memory.snoozedUntilEpochMillis?.let { it > nowEpochMillis } == true
    if (snoozed) return null
    val rows = listOfNotNull(
        GroupChecklistRowUi(GroupChecklistItem.WhatsApp, whatsApp == GroupWhatsAppStatus.ACTIVE).takeIf { whatsAppBinding },
        GroupChecklistRowUi(
            GroupChecklistItem.Mensalistas,
            group.financeDefaults?.monthlyFeeCents != null || rosterHasMensalista,
        ),
        GroupChecklistRowUi(GroupChecklistItem.Pix, !group.profile?.pixKey.isNullOrBlank()),
        GroupChecklistRowUi(GroupChecklistItem.Rules, memory.rulesOpened),
        GroupChecklistRowUi(GroupChecklistItem.Recurrence, group.profile?.regularSlots?.isNotEmpty() == true),
    )
    return if (rows.all { it.done }) null else GroupChecklistUi(rows)
}

/**
 * Alguém já é mensalista com mensalidade definida: o item "Mensalistas" conta como feito. O
 * roster não diz quem é o dono, e o dono pode nascer mensalista sem valor nenhum; o valor é o
 * que mostra que a mensalidade foi pensada.
 */
internal fun List<AthleteRosterEntry>.hasMensalista(): Boolean =
    any { it.membershipType == AthleteMembershipType.MENSALISTA && it.monthlyFeeCents != null }
