package br.com.saqz.composeapp

import br.com.saqz.access.domain.port.ClipboardLinkPort
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.access.domain.port.NativeProfilePhotoPort
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.composeapp.analytics.AnalyticsSink
import br.com.saqz.composeapp.di.SaqzDraftStores
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.port.GroupOnboardingMemoryPort
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.port.GroupInviteUrlStorePort
import br.com.saqz.groups.port.NativeInviteClipboardPort
import br.com.saqz.groups.port.NativeInviteSharePort
import br.com.saqz.profile.domain.ProfilePhotoSelectionPort

class GroupPhotoRuntimeDependencies(
    val selection: GroupPhotoSelectionPort,
    val encoder: GroupPhotoEncoderPort,
    val previews: GroupPhotoPreviewPort,
)

class AccessRuntimeDependencies(
    val auth: NativeAuthPort,
    val links: NativeLinkPort,
    val localState: LocalAccessStatePort,
    val share: NativeSharePort,
    val profilePhoto: NativeProfilePhotoPort,
    val profilePhotoSelection: ProfilePhotoSelectionPort,
    val clipboardLink: ClipboardLinkPort,
)

// Host-owned adapters are explicit constructor requirements, never silent no-op defaults.
@Suppress("LongParameterList")
class GroupsRuntimeDependencies(
    val map: br.com.saqz.groups.domain.map.GroupMapPort,
    val attendanceShare: NativeAttendanceSharePort,
    val photos: GroupPhotoRuntimeDependencies,
    val links: NativeGroupLinkPort,
    val state: LocalGroupStatePort,
    val inviteUrlStore: GroupInviteUrlStorePort,
    val inviteShare: NativeInviteSharePort,
    val inviteClipboard: NativeInviteClipboardPort,
    val onboardingMemory: GroupOnboardingMemoryPort,
)

@Suppress("LongParameterList")
class SaqzPlatformDependencies(
    val environment: String,
    val apiBaseUrl: String,
    /** "android" ou "ios": vai em toda chamada à API (X-Saqz-Platform). */
    val platform: String,
    /** Versão publicada, "1.0.3" (versionName / CFBundleShortVersionString): X-Saqz-App-Version e rodapé do Perfil. */
    val appVersion: String,
    /** Build, "14" (versionCode / CFBundleVersion): X-Saqz-App-Build e rodapé do Perfil; `null` onde não existe. */
    val appBuild: String?,
    val access: AccessRuntimeDependencies,
    val groups: GroupsRuntimeDependencies,
    val drafts: SaqzDraftStores,
    val notifications: br.com.saqz.groups.domain.communication.NativeNotificationPort,
    val financialDocuments: br.com.saqz.receivables.domain.port.ReceiptDocumentPicker,
    val analytics: AnalyticsSink,
    /** StoreKit no iOS; `null` explícito no Android. */
    val appStorePurchases: br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort?,
    /** Play Billing no Android; `null` explícito no iOS. */
    val googlePlayPurchases: br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesPort?,
) {
    init {
        require(environment.isNotBlank()) { "environment must not be blank" }
        require(apiBaseUrl.isNotBlank()) { "API base URL must not be blank" }
    }

    /** A versão como a loja mostra, "1.0.3 (14)"; só "1.0.3" onde não há build. É o rodapé do Perfil. */
    internal val appVersionLabel: String
        get() = if (appBuild.isNullOrBlank()) appVersion else "$appVersion ($appBuild)"
}
