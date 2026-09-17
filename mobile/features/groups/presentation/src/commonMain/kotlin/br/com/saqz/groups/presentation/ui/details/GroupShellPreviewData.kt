package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsState

/**
 * Os estados da casca do detalhe (topo, mural, galera/gestão, quadra, sair) para previews,
 * testes e capturas. Tudo deriva de [GroupDetailsPreviewData] com `.copy(...)`.
 */
internal object GroupShellPreviewData {
    val member = GroupDetailsPreviewData.member

    val memberNoGame = GroupDetailsPreviewData.memberNoGame

    val memberNoGameMapFailed = memberNoGame.copy(mapFailed = true)

    /** Produção de hoje, antes do V2: sem aviso, sem prévia de membros e sem contagem. */
    val memberBare = member.copy(latestNotice = null, memberPreview = emptyList(), memberCount = 0)

    /** 40 caracteres: o nome corta com reticências na barra. */
    val memberLongName = member.copy(
        header = GroupDetailsPreviewData.memberHeader.copy(name = "Vôlei Misto de Amigos do CERET Tatuapé 2"),
    )

    /** O gestor com as metas no formato que o V2 produz. */
    val manager = GroupDetailsPreviewData.admin.copy(
        cashbox = CashboxUi(summary = "Saldo R$ 380,00"),
        scheduleSummary = "Terça e Quinta · 19h30",
    )

    /** O resumo financeiro falhou: a linha do caixa continua, sem meta. */
    val managerNoCashSummary = manager.copy(cashbox = CashboxUi())

    val managerNewGroup = GroupDetailsPreviewData.adminNoGame.copy(
        memberCount = 1,
        scheduleSummary = null,
        cashbox = CashboxUi(summary = "Saldo R$ 0,00"),
    )

    /** Admin que não é dono: o único gestor que vê "Sair do grupo". */
    val managerNotOwner = manager.copy(isOwner = false)

    val loading = GroupDetailsState()

    val loadFailed = GroupDetailsState(isLoading = false, loadFailed = true)
}
