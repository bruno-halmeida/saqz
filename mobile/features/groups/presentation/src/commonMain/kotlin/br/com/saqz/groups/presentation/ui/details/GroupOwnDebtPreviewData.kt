package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.presentation.details.GroupOwnDebtUi

/**
 * Os estados do bloco "Minhas cobranças", derivados de [GroupDetailsPreviewData]. Regra de
 * sempre: só o que o `GroupDetailsViewModel` produz — com pendência, `debt` chega JUNTO;
 * sem chave Pix, `pix` e `receiverLabel` somem juntos.
 */
internal object GroupOwnDebtPreviewData {
    private val charges = GroupDetailsPreviewData.ownCharges

    private val debt = GroupOwnDebtUi(
        eyebrow = "Mensalidade · Agosto",
        totalLabel = "R$ 95,00",
        dueLabel = "Venceu em 10/08",
        overdue = true,
        countLabel = "2 cobranças em aberto",
        receiverLabel = "Pix de Lucas Prado",
    )

    /** Duas pendências: a mais antiga vencida, a outra a vencer. */
    val owesTwo = GroupDetailsPreviewData.member.copy(ownCharges = charges.copy(debt = debt))

    /** Uma pendência só, ainda no prazo: chip neutro e sem contagem. */
    val owesOne = GroupDetailsPreviewData.member.copy(
        ownCharges = charges.copy(
            pending = charges.pending.drop(1),
            debt = GroupOwnDebtUi(
                eyebrow = "Jogo avulso",
                totalLabel = "R$ 25,00",
                dueLabel = "Vence em 28/08",
                overdue = false,
                receiverLabel = "Pix de Lucas Prado",
            ),
        ),
    )

    val pixCopied = owesTwo.copy(pixCopied = true)

    /** Grupo sem chave Pix: sem recebedor, sem botão, com a nota de combinar com o admin. */
    val noPix = GroupDetailsPreviewData.member.copy(
        ownCharges = charges.copy(pix = null, debt = debt.copy(receiverLabel = null)),
    )

    val settled = GroupDetailsPreviewData.memberOwnChargesSettled
    val loading = GroupDetailsPreviewData.memberOwnChargesLoading
    val failed = GroupDetailsPreviewData.memberOwnChargesFailed

    /** Dono também joga e também deve (papel de admin não é o contrário de atleta). */
    val adminOwes = GroupDetailsPreviewData.admin.copy(ownCharges = owesTwo.ownCharges)
}
