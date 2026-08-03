package br.com.saqz.groups.presentation.membereditor

import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Grafo isolado do VUL-144, instalado pelo bootstrap junto da rota 3g. */
fun memberEditorPresentationModule(): Module = module {
    viewModel { params ->
        MemberEditorViewModel(
            groupId = params.get(),
            userId = params.get(),
            savedState = params.get(),
            athleteGateway = get<AthleteGateway>(),
            membershipGateway = get<GroupMembershipGateway>(),
            groupGateway = get<GroupGateway>(),
        )
    }
}
