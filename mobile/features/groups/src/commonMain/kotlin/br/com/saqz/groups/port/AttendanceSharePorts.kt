package br.com.saqz.groups.port

import br.com.saqz.groups.presentation.attendance.share.AttendanceShareImageModel

interface GroupAttendanceSharePort {
    fun shareLink(url: String, done: GroupResultCallback)

    fun shareImage(image: AttendanceShareImageModel, done: GroupResultCallback)
}
