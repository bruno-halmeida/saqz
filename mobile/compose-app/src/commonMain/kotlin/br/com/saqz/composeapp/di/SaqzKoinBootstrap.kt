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

internal fun startSaqzKoin(dependencies: SaqzPlatformDependencies) {
    startSaqzKoin()
    loadSaqzPlatformDependencies(dependencies)
}

@HiddenFromObjC
fun loadSaqzPlatformDependencies(dependencies: SaqzPlatformDependencies) {
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
    single { dependencies.drafts }
}
