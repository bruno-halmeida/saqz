package br.com.saqz.composeapp.notifications

import br.com.saqz.composeapp.SaqzPlatformDependencies
import br.com.saqz.composeapp.di.startSaqzKoin
import coil3.PlatformContext
import org.koin.mp.KoinPlatformTools

/** A ação do push pode acordar o app em segundo plano, antes de existir tela (e Koin). */
fun respondPushAttendance(
    dependencies: SaqzPlatformDependencies,
    groupId: String,
    gameId: String,
    confirm: Boolean,
    done: (PushAttendanceOutcome) -> Unit,
) {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) startSaqzKoin(dependencies, PlatformContext.INSTANCE)
    KoinPlatformTools.defaultContext().get().get<PushAttendance>().respond(groupId, gameId, confirm, done)
}
