package br.com.saqz.webapp

import br.com.saqz.composeapp.AccessRuntimeDependencies
import br.com.saqz.composeapp.GroupPhotoRuntimeDependencies
import br.com.saqz.composeapp.GroupsRuntimeDependencies
import br.com.saqz.composeapp.SaqzPlatformDependencies
import br.com.saqz.composeapp.di.SaqzDraftStores
import br.com.saqz.webapp.access.WebAccessStateStore
import br.com.saqz.webapp.access.WebAuthAdapter
import br.com.saqz.webapp.access.WebLinkAdapter
import br.com.saqz.webapp.access.WebLocalAccessStateAdapter
import br.com.saqz.webapp.access.WebLocalGroupStateAdapter
import br.com.saqz.webapp.access.WebProfilePhotoAdapter
import br.com.saqz.webapp.access.WebShareAdapter
import br.com.saqz.webapp.analytics.WebAnalyticsSink
import br.com.saqz.webapp.groups.WebAttendanceShareAdapter
import br.com.saqz.webapp.groups.WebGroupDraftStores
import br.com.saqz.webapp.groups.WebGroupOnboardingMemory
import br.com.saqz.webapp.groups.WebGroupPhotoAdapters
import br.com.saqz.webapp.groups.WebInviteAdapters
import br.com.saqz.webapp.groups.WebMapAdapter
import br.com.saqz.webapp.notifications.WebNotificationPort
import br.com.saqz.webapp.receivables.WebReceiptDocumentPicker
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Composition root do hospedeiro web: o espelho de `AndroidAppComposition` / `SaqzIOSApp`. */
internal class WebAppComposition(
    val dependencies: SaqzPlatformDependencies,
    val links: WebLinkAdapter,
) {
    companion object {
        fun create(config: WebConfig): WebAppComposition {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            // O próprio origin do web app também é um host confiável: a página de links manda
            // o iPhone para cá com o mesmo código na URL.
            val links = WebLinkAdapter(allowedHosts = setOf(config.linksHost, window.location.host))
            val store = WebAccessStateStore()
            val photos = WebGroupPhotoAdapters()
            val profilePhoto = WebProfilePhotoAdapter(photos, scope)
            val invites = WebInviteAdapters(scope)
            val drafts = WebGroupDraftStores()
            val dependencies = SaqzPlatformDependencies(
                environment = config.environment,
                apiBaseUrl = config.apiBaseUrl,
                appVersion = config.appVersion,
                access = AccessRuntimeDependencies(
                    auth = WebAuthAdapter(scope),
                    links = links,
                    localState = WebLocalAccessStateAdapter(store),
                    share = WebShareAdapter(scope),
                    profilePhoto = profilePhoto,
                    profilePhotoSelection = profilePhoto,
                ),
                groups = GroupsRuntimeDependencies(
                    map = WebMapAdapter(),
                    attendanceShare = WebAttendanceShareAdapter(scope),
                    photos = GroupPhotoRuntimeDependencies(
                        selection = photos.selection,
                        encoder = photos.encoder,
                        previews = photos.previews,
                    ),
                    links = links,
                    state = WebLocalGroupStateAdapter(store),
                    inviteUrlStore = invites.urlStore,
                    inviteShare = invites.share,
                    inviteClipboard = invites.clipboard,
                    onboardingMemory = WebGroupOnboardingMemory(),
                ),
                drafts = SaqzDraftStores(
                    groupDrafts = drafts.setup,
                    gameDrafts = drafts.game,
                    monthlyChargeDrafts = drafts.monthly,
                    expenseDrafts = drafts.expense,
                ),
                notifications = WebNotificationPort(),
                financialDocuments = WebReceiptDocumentPicker(scope),
                analytics = WebAnalyticsSink(config.environment),
                // Sem loja no navegador: a contratação é pela web (Asaas), pelo portão de assinatura.
                appStorePurchases = null,
                googlePlayPurchases = null,
            )
            return WebAppComposition(dependencies, links)
        }
    }
}
