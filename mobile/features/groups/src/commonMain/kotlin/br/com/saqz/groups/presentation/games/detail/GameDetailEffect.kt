package br.com.saqz.groups.presentation.games.detail

import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.attendance.share.AttendanceShareImageModel
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkUrl

sealed interface GameDetailEffect {
    data class OpenEdit(val groupId:String,val gameId:String):GameDetailEffect
    data class LifecycleApplied(val action:GameLifecycleAction):GameDetailEffect
    data class AttendanceApplied(val status:AttendanceStatus,val promotedCount:Int,val refreshCharges:Boolean):GameDetailEffect
    data class CapacityApplied(val capacity:Int,val promotedCount:Int):GameDetailEffect
    data class ShareAttendanceLink(val url:AttendanceLinkUrl):GameDetailEffect
    data class ShareAttendanceImage(val image:AttendanceShareImageModel):GameDetailEffect
}
