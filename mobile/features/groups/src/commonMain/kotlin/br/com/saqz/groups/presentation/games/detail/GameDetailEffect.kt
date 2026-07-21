package br.com.saqz.groups.presentation.games.detail

import br.com.saqz.groups.data.attendance.AttendanceStatusDto
import br.com.saqz.groups.presentation.attendance.share.AttendanceShareImageModel

sealed interface GameDetailEffect {
    data class OpenEdit(val groupId:String,val gameId:String):GameDetailEffect
    data class LifecycleApplied(val action:GameLifecycleAction):GameDetailEffect
    data class AttendanceApplied(val status:AttendanceStatusDto,val promotedCount:Int,val refreshCharges:Boolean):GameDetailEffect
    data class CapacityApplied(val capacity:Int,val promotedCount:Int):GameDetailEffect
    data class ShareAttendanceLink(val url:String):GameDetailEffect
    data class ShareAttendanceImage(val image:AttendanceShareImageModel):GameDetailEffect
}
