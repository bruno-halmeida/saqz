package br.com.saqz.composeapp

import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.groups.port.GroupAttendanceSharePort
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraftStorePort
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraftStorePort
import br.com.saqz.groups.presentation.games.editor.GameDraftStorePort

class GroupPhotoRuntimeDependencies(
    val selection: GroupPhotoSelectionPort,
    val encoder: GroupPhotoEncoderPort,
    val previews: GroupPhotoPreviewPort,
)

class SaqzAppDependencies(
    val environment: String,
    val apiBaseUrl: String,
    val auth: NativeAuthPort,
    val links: NativeLinkPort,
    val localState: LocalAccessStatePort,
    val share: NativeSharePort,
    val attendanceShare: GroupAttendanceSharePort,
    val groupPhotos: GroupPhotoRuntimeDependencies,
    val groupLinks: NativeGroupLinkPort,
    val groupState: LocalGroupStatePort,
    val groupDrafts: GroupDraftStorePort,
    val gameDrafts: GameDraftStorePort,
    val monthlyChargeDrafts: MonthlyChargeDraftStorePort,
    val expenseDrafts: ExpenseDraftStorePort,
) {
    init {
        require(environment.isNotBlank()) { "environment must not be blank" }
        require(apiBaseUrl.isNotBlank()) { "API base URL must not be blank" }
    }

}
