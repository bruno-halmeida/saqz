package br.com.saqz.groups.presentation.entryrequest

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.group.GroupFinanceDefaults

/**
 * Folha "Aceitar como" do pedido de entrada (DEB-112), a mesma em Convidar e em Membros. Só abre quando o grupo tem
 * mensalidade padrão; [feeRequired] diz que o servidor recusou o mensalista porque o padrão sumiu (o pedido fica).
 */
@Immutable
data class AcceptAsUi(
    val requestId: String,
    val name: String,
    val feeCents: Long,
    val dueDay: Int,
    val selected: AthleteMembershipType = AthleteMembershipType.AVULSO,
    val feeRequired: Boolean = false,
)

/** Valor e dia padrão do grupo, ou nulo quando falta um dos dois: aí "Aceitar" segue em um toque, como avulso. */
data class MonthlyFeeDefaultUi(val feeCents: Long, val dueDay: Int)

fun GroupFinanceDefaults?.monthlyFeeDefault(): MonthlyFeeDefaultUi? {
    val cents = this?.monthlyFeeCents ?: return null
    val day = monthlyDueDay ?: return null
    return MonthlyFeeDefaultUi(cents, day)
}

fun MonthlyFeeDefaultUi.sheetFor(requestId: String, name: String) = AcceptAsUi(requestId, name, feeCents, dueDay)
