package br.com.saqz.groups.data.di

import br.com.saqz.groups.data.athlete.KtorAthleteGateway
import br.com.saqz.groups.data.attendance.KtorAttendanceGateway
import br.com.saqz.groups.data.attendance.share.KtorAttendanceSharingGateway
import br.com.saqz.groups.data.finance.KtorAthleteFinanceGateway
import br.com.saqz.groups.data.finance.KtorOrganizerFinanceGateway
import br.com.saqz.groups.data.game.KtorGameGateway
import br.com.saqz.groups.data.group.KtorGroupGateway
import br.com.saqz.groups.data.membership.KtorGroupMembershipGateway
import br.com.saqz.groups.data.photo.KtorGroupPhotoGateway
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingGateway
import br.com.saqz.groups.domain.finance.AthleteFinanceGateway
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import org.koin.core.module.Module
import org.koin.dsl.module

/** Bindings da camada remota da feature de grupos. */
fun groupsDataModule(): Module = module {
    single<KtorGroupGateway> { KtorGroupGateway(get()) }
    single<GroupGateway> { get<KtorGroupGateway>() }
    single<GroupProfileGateway> { get<KtorGroupGateway>() }
    single<AthleteGateway> { KtorAthleteGateway(get()) }
    single<GameGateway> { KtorGameGateway(get()) }
    single<GroupMembershipGateway> { KtorGroupMembershipGateway(get()) }
    single<AthleteFinanceGateway> { KtorAthleteFinanceGateway(get()) }
    single<OrganizerFinanceGateway> { KtorOrganizerFinanceGateway(get()) }
    single<AttendanceGateway> { KtorAttendanceGateway(get()) }
    single<AttendanceSharingGateway> { KtorAttendanceSharingGateway(get()) }
    single<GroupPhotoGateway> { KtorGroupPhotoGateway(get()) }
}
