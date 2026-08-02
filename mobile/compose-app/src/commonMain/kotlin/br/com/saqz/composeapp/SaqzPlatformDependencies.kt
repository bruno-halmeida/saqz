package br.com.saqz.composeapp

import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.access.domain.port.NativeProfilePhotoPort
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.composeapp.di.SaqzDraftStores
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
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
)

class GroupsRuntimeDependencies(
    val attendanceShare: NativeAttendanceSharePort,
    val photos: GroupPhotoRuntimeDependencies,
    val links: NativeGroupLinkPort,
    val state: LocalGroupStatePort,
)

class SaqzPlatformDependencies(
    val environment: String,
    val apiBaseUrl: String,
    val access: AccessRuntimeDependencies,
    val groups: GroupsRuntimeDependencies,
    val drafts: SaqzDraftStores,
) {
    init {
        require(environment.isNotBlank()) { "environment must not be blank" }
        require(apiBaseUrl.isNotBlank()) { "API base URL must not be blank" }
    }

}
