package br.com.saqz.androidapp

import android.app.Activity
import android.content.Context
import br.com.saqz.androidapp.access.AndroidAuthAdapter
import br.com.saqz.androidapp.access.AndroidBranchSessionClient
import br.com.saqz.androidapp.access.AndroidEncryptedAccessStateStore
import br.com.saqz.androidapp.access.AndroidGoogleCredentialClient
import br.com.saqz.androidapp.access.AndroidIntentLinkPort
import br.com.saqz.androidapp.access.AndroidLinkAdapter
import br.com.saqz.androidapp.access.AndroidLocalGroupStateAdapter
import br.com.saqz.androidapp.access.AndroidLocalAccessStateAdapter
import br.com.saqz.androidapp.access.AndroidShareAdapter
import br.com.saqz.androidapp.access.AndroidShareLauncher
import br.com.saqz.androidapp.access.BranchSdkSessionClient
import br.com.saqz.androidapp.access.CredentialManagerGoogleClient
import br.com.saqz.androidapp.access.FirebaseSdkAuthClient
import br.com.saqz.androidapp.groups.photo.AndroidGroupPhotoAdapters
import br.com.saqz.androidapp.groups.draft.AndroidGroupDraftAdapters
import br.com.saqz.composeapp.SaqzAppDependencies
import br.com.saqz.composeapp.GroupPhotoRuntimeDependencies
import kotlinx.coroutines.CoroutineScope

internal data class AndroidAppComposition(
    val dependencies: SaqzAppDependencies,
    val links: AndroidIntentLinkPort,
    val photos: AndroidGroupPhotoAdapters? = null,
)

internal fun interface AndroidAppCompositionFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
        activity: () -> Activity,
    ): AndroidAppComposition
}

internal object MainActivityComposition {
    @Volatile
    var factoryOverride: AndroidAppCompositionFactory? = null

    fun factory(): AndroidAppCompositionFactory = factoryOverride ?: ProductionAndroidAppCompositionFactory
}

private object ProductionAndroidAppCompositionFactory : AndroidAppCompositionFactory {
    override fun create(
        context: Context,
        scope: CoroutineScope,
        activity: () -> Activity,
    ): AndroidAppComposition {
        val firebase = AndroidFirebaseBootstrap.initialize(context)
        val links = AndroidLinkAdapter(ActivityBranchSessionClient(activity))
        val auth = AndroidAuthAdapter(
            firebase = FirebaseSdkAuthClient(firebase),
            google = ActivityGoogleCredentialClient(activity, scope),
        )
        val store = AndroidEncryptedAccessStateStore(context.applicationContext)
        val localState = AndroidLocalAccessStateAdapter(store)
        val share = AndroidShareAdapter(ActivityShareLauncher(activity))
        val photos = AndroidGroupPhotoAdapters.create(context.applicationContext, scope)
        val drafts = AndroidGroupDraftAdapters.create(context.applicationContext)
        return AndroidAppComposition(
            dependencies = SaqzAppDependencies(
                environment = BuildConfig.ENVIRONMENT,
                apiBaseUrl = BuildConfig.API_BASE_URL,
                auth = auth,
                links = links,
                localState = localState,
                share = share,
                groupPhotos = GroupPhotoRuntimeDependencies(
                    selection = photos.selection,
                    encoder = photos.encoder,
                    previews = photos.previews,
                ),
                groupLinks = links,
                groupState = AndroidLocalGroupStateAdapter(store),
                groupDrafts = drafts.setup,
                gameDrafts = drafts.game,
                monthlyChargeDrafts = drafts.monthly,
                expenseDrafts = drafts.expense,
            ),
            links = links,
            photos = photos,
        )
    }
}

private class ActivityBranchSessionClient(
    private val activity: () -> Activity,
) : AndroidBranchSessionClient {
    override fun initialize(url: String?, callback: (Map<String, String?>) -> Unit) =
        BranchSdkSessionClient(activity()).initialize(url, callback)

    override fun reinitialize(url: String?, callback: (Map<String, String?>) -> Unit) =
        BranchSdkSessionClient(activity()).reinitialize(url, callback)
}

private class ActivityGoogleCredentialClient(
    private val activity: () -> Activity,
    private val scope: CoroutineScope,
) : AndroidGoogleCredentialClient {
    override fun requestIdToken(done: (br.com.saqz.androidapp.access.AndroidProviderResult<String>) -> Unit) =
        client().requestIdToken(done)

    override fun clearCredentialState(done: (br.com.saqz.androidapp.access.AndroidProviderResult<Unit>) -> Unit) =
        client().clearCredentialState(done)

    private fun client() = CredentialManagerGoogleClient(
        context = activity(),
        serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
        scope = scope,
    )
}

private class ActivityShareLauncher(
    private val activity: () -> Activity,
) : AndroidShareLauncher {
    override fun launch(text: String) {
        br.com.saqz.androidapp.access.AndroidSharesheetLauncher(activity()).launch(text)
    }
}
