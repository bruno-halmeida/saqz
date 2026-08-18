@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package br.com.saqz.composeapp.di

import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.access.domain.port.NativeProfilePhotoPort
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.composeapp.SaqzPlatformDependencies
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.data.di.groupsDataModule
import br.com.saqz.groups.data.di.inviteJourneyDataModule
import br.com.saqz.groups.data.di.inviteManagementDataModule
import br.com.saqz.groups.invite.GroupInviteCoordinator
import br.com.saqz.groups.invite.groupsInviteModule
import br.com.saqz.groups.port.DefaultGroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupInviteUrlStorePort
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupNowPort
import br.com.saqz.groups.port.DefaultGroupNowPort
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.port.NativeInviteClipboardPort
import br.com.saqz.groups.port.NativeInviteSharePort
import br.com.saqz.groups.presentation.di.athleteRegistrationPresentationModule
import br.com.saqz.groups.presentation.di.groupsPresentationModule
import br.com.saqz.groups.presentation.di.inviteLandingPresentationModule
import br.com.saqz.groups.presentation.di.inviteManagementPresentationModule
import br.com.saqz.groups.presentation.membereditor.memberEditorPresentationModule
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.authenticatedImageLoaderModule
import br.com.saqz.network.toNetworkEnvironment
import br.com.saqz.profile.data.di.profileDataModule
import br.com.saqz.profile.presentation.edit.di.editProfilePresentationModule
import br.com.saqz.profile.presentation.own.di.ownProfilePresentationModule
import br.com.saqz.profile.presentation.photo.di.profilePhotoPresentationModule
import br.com.saqz.subscriptions.presentation.myplan.di.myPlanPresentationModule
import coil3.PlatformContext
import org.koin.core.context.startKoin
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import kotlin.native.HiddenFromObjC

private var platformModules: List<Module> = emptyList()
private object SaqzKoinMarker
private val markerModule = module { single { SaqzKoinMarker } }
private val commonModules = listOf(
    markerModule,
    coreNetworkModule,
    platformDraftsModule,
    accessDataModule,
    accessInvalidationModule,
    accessPresentationModule,
    composePresentationModule,
    subscriptionsDataModule,
    myPlanPresentationModule(),
    subscriptionsCustomerInfoModule,
    groupCreationEntitlementModule,
    profileDataModule(),
    editProfilePresentationModule(),
    ownProfilePresentationModule(),
    groupsDataModule(),
    groupsPresentationModule(),
    inviteJourneyDataModule(),
    inviteManagementDataModule(),
    inviteManagementPresentationModule(),
    inviteLandingPresentationModule(),
    athleteRegistrationPresentationModule(),
    memberEditorPresentationModule(),
    groupsInviteModule(),
)

internal fun startSaqzKoin() {
    KoinPlatformTools.defaultContext().getOrNull()?.let { existing ->
        check(existing.getOrNull<SaqzKoinMarker>() != null) { "A different Koin application is already running" }
        return
    }

    startKoin {
        modules(commonModules)
    }
}

@HiddenFromObjC
fun installSaqzKoinModules() {
    val koin = checkNotNull(KoinPlatformTools.defaultContext().getOrNull()) { "Koin must be started before installing Saqz modules" }
    if (koin.getOrNull<SaqzKoinMarker>() != null) return
    loadKoinModules(commonModules)
}

internal fun startSaqzKoin(
    dependencies: SaqzPlatformDependencies,
    imageLoaderContext: PlatformContext? = null,
) {
    startSaqzKoin()
    loadSaqzPlatformDependencies(dependencies, imageLoaderContext)
}

@HiddenFromObjC
fun loadSaqzPlatformDependencies(
    dependencies: SaqzPlatformDependencies,
    imageLoaderContext: PlatformContext? = null,
) {
    val koin = checkNotNull(KoinPlatformTools.defaultContext().getOrNull()) {
        "Koin must be started before loading platform dependencies"
    }
    if (platformModules.isNotEmpty()) {
        koin.getOrNull<GroupInviteCoordinator>()?.stop()
        unloadKoinModules(commonModules + platformModules)
        loadKoinModules(commonModules)
    }
    platformModules = listOf(
        platformBindingsModule(dependencies),
        profilePhotoPresentationModule(
            selection = dependencies.access.profilePhotoSelection,
            initialPhotoUrl = null,
        ),
        imageLoaderContext?.let(::authenticatedImageLoaderModule),
    ).filterNotNull().also(::loadKoinModules)
    koin.get<GroupInviteCoordinator>().start()
}

internal fun stopSaqzKoin() {
    if (platformModules.isNotEmpty()) {
        KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<GroupInviteCoordinator>()?.stop()
    }
    platformModules = emptyList()
    stopKoin()
}

private fun platformBindingsModule(dependencies: SaqzPlatformDependencies) = module {
    single {
        NetworkConfig(
            environment = dependencies.environment.toNetworkEnvironment(),
            baseUrl = dependencies.apiBaseUrl,
        )
    }
    single { SaqzNativePorts(access = dependencies.access, groups = dependencies.groups) }
    single<NativeAuthPort> { get<SaqzNativePorts>().access.auth }
    single<NativeLinkPort> { get<SaqzNativePorts>().access.links }
    single<LocalAccessStatePort> { get<SaqzNativePorts>().access.localState }
    single<NativeSharePort> { get<SaqzNativePorts>().access.share }
    single<NativeProfilePhotoPort> { get<SaqzNativePorts>().access.profilePhoto }
    single<NativeAttendanceSharePort> { get<SaqzNativePorts>().groups.attendanceShare }
    single<GroupPhotoSelectionPort> { get<SaqzNativePorts>().groups.photos.selection }
    single<GroupPhotoEncoderPort> { get<SaqzNativePorts>().groups.photos.encoder }
    single<GroupPhotoPreviewPort> { get<SaqzNativePorts>().groups.photos.previews }
    single<NativeGroupLinkPort> { get<SaqzNativePorts>().groups.links }
    single<LocalGroupStatePort> { get<SaqzNativePorts>().groups.state }
    single<GroupInviteUrlStorePort> { get<SaqzNativePorts>().groups.inviteUrlStore }
    single<NativeInviteSharePort> { get<SaqzNativePorts>().groups.inviteShare }
    single<NativeInviteClipboardPort> { get<SaqzNativePorts>().groups.inviteClipboard }
    single<GroupSystemTimeZonePort> { DefaultGroupSystemTimeZonePort() }
    single<GroupNowPort> { DefaultGroupNowPort() }
    single { dependencies.drafts }
}
