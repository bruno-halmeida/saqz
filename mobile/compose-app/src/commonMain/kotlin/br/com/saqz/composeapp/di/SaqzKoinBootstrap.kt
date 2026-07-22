@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package br.com.saqz.composeapp.di

import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.composeapp.SaqzAppDependencies
import br.com.saqz.groups.port.GroupAttendanceSharePort
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.toNetworkEnvironment
import org.koin.core.context.startKoin
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import kotlin.native.HiddenFromObjC

private var platformModule: Module? = null
private object SaqzKoinMarker
private val markerModule = module { single { SaqzKoinMarker } }
private val commonModules = listOf(
    markerModule,
    coreNetworkModule,
    platformDraftsModule,
    accessDataModule,
    accessInvalidationModule,
    accessPresentationModule,
    groupsDataModule,
    groupsPresentationModule,
    composePresentationModule,
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

internal fun startSaqzKoin(dependencies: SaqzAppDependencies) {
    startSaqzKoin()
    loadSaqzPlatformDependencies(dependencies)
}

@HiddenFromObjC
fun loadSaqzPlatformDependencies(dependencies: SaqzAppDependencies) {
    checkNotNull(KoinPlatformTools.defaultContext().getOrNull()) { "Koin must be started before loading platform dependencies" }
    platformModule?.let { previous ->
        unloadKoinModules(commonModules + previous)
        loadKoinModules(commonModules)
    }
    platformModule = platformBindingsModule(dependencies).also(::loadKoinModules)
}

internal fun stopSaqzKoin() {
    platformModule = null
    stopKoin()
}

private fun platformBindingsModule(dependencies: SaqzAppDependencies) = module {
    single { dependencies }
    single {
        NetworkConfig(
            environment = dependencies.environment.toNetworkEnvironment(),
            baseUrl = dependencies.apiBaseUrl,
        )
    }
    single {
        SaqzNativePorts(
            auth = dependencies.auth,
            links = dependencies.links,
            localAccessState = dependencies.localState,
            share = dependencies.share,
            attendanceShare = dependencies.attendanceShare,
            groupPhotoSelection = dependencies.groupPhotos.selection,
            groupPhotoEncoder = dependencies.groupPhotos.encoder,
            groupPhotoPreviews = dependencies.groupPhotos.previews,
            groupLinks = dependencies.groupLinks,
            localGroupState = dependencies.groupState,
        )
    }
    single<NativeAuthPort> { get<SaqzNativePorts>().auth }
    single<NativeLinkPort> { get<SaqzNativePorts>().links }
    single<LocalAccessStatePort> { get<SaqzNativePorts>().localAccessState }
    single<NativeSharePort> { get<SaqzNativePorts>().share }
    single<GroupAttendanceSharePort> { get<SaqzNativePorts>().attendanceShare }
    single<GroupPhotoSelectionPort> { get<SaqzNativePorts>().groupPhotoSelection }
    single<GroupPhotoEncoderPort> { get<SaqzNativePorts>().groupPhotoEncoder }
    single<GroupPhotoPreviewPort> { get<SaqzNativePorts>().groupPhotoPreviews }
    single<NativeGroupLinkPort> { get<SaqzNativePorts>().groupLinks }
    single<LocalGroupStatePort> { get<SaqzNativePorts>().localGroupState }
    single {
        SaqzDraftStores(
            groupDrafts = dependencies.groupDrafts,
            gameDrafts = dependencies.gameDrafts,
            monthlyChargeDrafts = dependencies.monthlyChargeDrafts,
            expenseDrafts = dependencies.expenseDrafts,
        )
    }
}
