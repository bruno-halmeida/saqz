package br.com.saqz.composeapp.di

import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.groups.port.GroupAttendanceSharePort
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort

class SaqzNativePorts(
    val auth: NativeAuthPort,
    val links: NativeLinkPort,
    val localAccessState: LocalAccessStatePort,
    val share: NativeSharePort,
    val attendanceShare: GroupAttendanceSharePort,
    val groupPhotoSelection: GroupPhotoSelectionPort,
    val groupPhotoEncoder: GroupPhotoEncoderPort,
    val groupPhotoPreviews: GroupPhotoPreviewPort,
    val groupLinks: NativeGroupLinkPort,
    val localGroupState: LocalGroupStatePort,
)
