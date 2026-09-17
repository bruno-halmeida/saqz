package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.presentation.details.GroupAgendaRowUi
import br.com.saqz.groups.presentation.details.GroupAgendaStatus

/**
 * Os estados da agenda nas capturas e nos testes do bloco. Derivam dos estados-base com
 * `.copy(...)`; o jogo do hero (`game-1`) nunca aparece aqui, como no ViewModel.
 */
internal object GroupAgendaPreviewData {
    val pending = GroupAgendaRowUi(
        gameId = "game-2",
        day = "30",
        month = "JUL",
        title = "Quinta · 19h30",
        meta = "6 de 12 confirmados",
        status = GroupAgendaStatus.Pending,
        statusLabel = "Sem resposta",
        contentDescription = "Quinta, 30/07 às 19h30, Sem resposta",
    )
    val going = GroupAgendaRowUi(
        gameId = "game-3",
        day = "04",
        month = "AGO",
        title = "Terça · 19h30",
        meta = "3 de 12 · Arena Mooca",
        status = GroupAgendaStatus.Going,
        statusLabel = "Você vai",
        contentDescription = "Terça, 04/08 às 19h30, Você vai",
    )
    val waitlisted = GroupAgendaRowUi(
        gameId = "game-4",
        day = "06",
        month = "AGO",
        title = "Quinta · 19h30",
        meta = "Lotado · 12 de 12",
        status = GroupAgendaStatus.Waitlisted,
        statusLabel = "Na espera",
        contentDescription = "Quinta, 06/08 às 19h30, Na espera",
    )
    val out = GroupAgendaRowUi(
        gameId = "game-5",
        day = "11",
        month = "AGO",
        title = "Terça · 19h30",
        meta = "2 de 12 confirmados",
        status = GroupAgendaStatus.Out,
        statusLabel = "Não vai",
        contentDescription = "Terça, 11/08 às 19h30, Não vai",
    )
    val later = GroupAgendaRowUi(
        gameId = "game-6",
        day = "13",
        month = "AGO",
        title = "Quinta · 19h30",
        meta = "0 de 12 confirmados",
        status = GroupAgendaStatus.Pending,
        statusLabel = "Sem resposta",
        contentDescription = "Quinta, 13/08 às 19h30, Sem resposta",
    )
    val draft = GroupAgendaRowUi(
        gameId = "game-7",
        day = "06",
        month = "AGO",
        title = "Quinta · 19h30",
        meta = "Só você vê até publicar",
        status = GroupAgendaStatus.Draft,
        statusLabel = "Rascunho",
        contentDescription = "Quinta, 06/08 às 19h30, Rascunho",
    )

    /** Membro, três linhas, três respostas: sem resposta, vai e na espera. */
    val member = GroupDetailsPreviewData.member.copy(agenda = listOf(pending, going, waitlisted))

    /** Membro com cinco jogos: três à vista e "Ver mais 2 jogos". */
    val memberMore = GroupDetailsPreviewData.member.copy(agenda = listOf(pending, going, waitlisted, out, later))

    /** Gestor: "Marcar jogo" no cabeçalho e o rascunho que só ele vê. */
    val adminDraft = GroupDetailsPreviewData.admin.copy(agenda = listOf(pending, going, draft))
}
