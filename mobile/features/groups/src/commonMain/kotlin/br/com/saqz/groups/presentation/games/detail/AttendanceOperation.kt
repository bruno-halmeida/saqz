package br.com.saqz.groups.presentation.games.detail

import br.com.saqz.groups.data.attendance.AttendanceIntentDto

internal sealed interface AttendanceOperation {
    val key:String
    data class Self(val action:AttendanceAction,override val key:String):AttendanceOperation
    data class Override(val memberId:String,val intent:AttendanceIntentDto,val reason:String,override val key:String):AttendanceOperation
    data class Capacity(val capacity:Int,val etag:String,override val key:String):AttendanceOperation
}
