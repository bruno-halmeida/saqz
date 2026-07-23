package br.com.saqz.groups.domain.attendance.share

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import kotlin.jvm.JvmInline

@JvmInline
value class AttendanceLinkCode(val value: String)

@JvmInline
value class AttendanceLinkUrl(val value: String)

data class AttendanceLinkDestination(val groupId: GroupId, val gameId: String)

data class AttendanceSharePerson(
    val displayName: String,
    val waitlistPosition: Long? = null,
)

data class AttendanceShareSnapshot(
    val title: String,
    val startsAt: String,
    val timeZone: String,
    val venue: String,
    val capacity: Int,
    val confirmed: List<AttendanceSharePerson>,
    val waitlisted: List<AttendanceSharePerson>,
    val declined: List<AttendanceSharePerson>,
)

sealed interface AttendanceSharingError : SaqzError {
    data object InvalidOrExpired : AttendanceSharingError
    data class AttemptLimit(val retryAfterSeconds: Int?) : AttendanceSharingError
    data class DataFailure(val error: DataError) : AttendanceSharingError
}

interface AttendanceSharingGateway {
    suspend fun rotateLink(groupId: GroupId, gameId: String): SaqzResult<AttendanceLinkUrl, AttendanceSharingError>
    suspend fun resolveLink(code: AttendanceLinkCode): SaqzResult<AttendanceLinkDestination, AttendanceSharingError>
    suspend fun readSnapshot(groupId: GroupId, gameId: String): SaqzResult<AttendanceShareSnapshot, AttendanceSharingError>
}

data class AttendanceShareImageSection(
    val title: String,
    val countLabel: String,
    val emptyLabel: String,
    val entries: List<String>,
)

data class AttendanceShareImage(
    val title: String,
    val scheduleLine: String,
    val venueLine: String,
    val capacityLine: String,
    val sections: List<AttendanceShareImageSection>,
    val heightUnits: Int,
)

sealed interface NativeAttendanceShareResult {
    data object Success : NativeAttendanceShareResult
    data object Cancelled : NativeAttendanceShareResult
    data object Failure : NativeAttendanceShareResult
}

// This port is implemented by native (Swift/Android) share adapters and exchanges the raw
// link String at the boundary; gateway/domain callers use AttendanceLinkUrl end-to-end and
// unwrap it here. Keeping the FFI signature primitive avoids leaking domain value objects
// across the native boundary and keeps the exported Objective-C surface unambiguous.
interface NativeAttendanceSharePort {
    fun shareLink(url: String, done: (NativeAttendanceShareResult) -> Unit)
    fun shareImage(image: AttendanceShareImage, done: (NativeAttendanceShareResult) -> Unit)
}
