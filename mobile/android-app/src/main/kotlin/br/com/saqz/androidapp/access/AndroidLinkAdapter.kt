package br.com.saqz.androidapp.access

import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.AppOnboardingCodeListener
import br.com.saqz.access.domain.port.InviteCodeListener
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.NativeGroupLinkPort
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal interface AndroidIntentLinkPort : NativeLinkPort {
    fun onColdStart(url: String?)
    fun onWarmIntent(url: String?)
    fun onNotificationOpen(groupId: String?)
}

internal class AndroidLinkAdapter(
    private val allowedHosts: Set<String> = setOf("links.saqz.app"),
) : AndroidIntentLinkPort, NativeGroupLinkPort {
    private var accessListener: InviteCodeListener? = null
    private var onboardingListener: AppOnboardingCodeListener? = null
    private val groupListeners = mutableSetOf<GroupLinkEventListener>()
    private var pendingAccessCode: String? = null
    private var pendingOnboardingCode: String? = null
    private var pendingGroupEvent: GroupLinkEvent? = null

    override fun start(listener: InviteCodeListener): Cancelable {
        accessListener = listener
        pendingAccessCode?.let(listener::onInviteCode)
        pendingAccessCode = null
        return object : Cancelable {
            override fun cancel() {
                if (this@AndroidLinkAdapter.accessListener === listener) {
                    this@AndroidLinkAdapter.accessListener = null
                }
            }
        }
    }

    override fun startAppOnboarding(listener: AppOnboardingCodeListener): Cancelable {
        onboardingListener = listener
        pendingOnboardingCode?.let(listener::onAppOnboardingCode)
        pendingOnboardingCode = null
        return object : Cancelable {
            override fun cancel() {
                if (this@AndroidLinkAdapter.onboardingListener === listener) {
                    this@AndroidLinkAdapter.onboardingListener = null
                }
            }
        }
    }

    override fun start(listener: GroupLinkEventListener): GroupCancelable {
        groupListeners += listener
        pendingGroupEvent?.let(listener::onEvent)
        return object : GroupCancelable {
            override fun cancel() {
                groupListeners -= listener
            }
        }
    }

    override fun onColdStart(url: String?) {
        acceptOnboarding(directOnboardingCode(url, allowedHosts))
        accept(directEvent(url, allowedHosts))
    }

    override fun onWarmIntent(url: String?) = onColdStart(url)

    /** Push tap: always routes to the notification center; no dedup across taps. */
    override fun onNotificationOpen(groupId: String?) {
        val event = GroupLinkEvent.NotificationOpen(groupId)
        if (groupListeners.isEmpty()) {
            pendingGroupEvent = event
        } else {
            pendingGroupEvent = null
            groupListeners.forEach { it.onEvent(event) }
        }
    }

    private fun acceptOnboarding(code: String?) {
        val accepted = code ?: return
        val current = onboardingListener
        if (current == null) pendingOnboardingCode = accepted else current.onAppOnboardingCode(accepted)
    }

    private fun accept(event: GroupLinkEvent?) {
        val accepted = event ?: return
        if (accepted is GroupLinkEvent.Invite) {
            val current = accessListener
            if (current == null) pendingAccessCode = accepted.code else current.onInviteCode(accepted.code)
        }
        if (groupListeners.isEmpty()) {
            pendingGroupEvent = accepted
        } else {
            pendingGroupEvent = null
            groupListeners.forEach { it.onEvent(accepted) }
        }
    }

    private companion object {
        const val INVITE_PARAMETER = "saqz_invite"
        const val ATTENDANCE_PARAMETER = "saqz_attendance"
        const val ONBOARDING_PARAMETER = "saqz_onboarding"
        const val INTENT_PARAMETER = "saqz_intent"
        const val DECLINE_INTENT = "decline"
        const val ATTENDANCE_PATH_SEGMENT = "attendance"

        fun directEvent(url: String?, allowedHosts: Set<String>): GroupLinkEvent? = runCatching {
            val uri = URI(url ?: return null)
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.host?.lowercase() !in allowedHosts.map(String::lowercase)) return null
            if (uri.userInfo != null || uri.port != -1) return null
            val queryEntries = uri.rawQuery
                ?.split('&')
                ?.mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator < 0) return@mapNotNull null
                    val key = URLDecoder.decode(entry.substring(0, separator), StandardCharsets.UTF_8.name())
                    val value = URLDecoder.decode(entry.substring(separator + 1), StandardCharsets.UTF_8.name())
                    key to value
                }
                .orEmpty()
            val pathSegments = uri.path.trim('/').split('/').filter(String::isNotBlank)
            val hasAttendancePath = pathSegments.size == 2 && pathSegments[0] == ATTENDANCE_PATH_SEGMENT &&
                isValidInviteCode(pathSegments[1])
            if (hasAttendancePath && queryEntries.any { it.first in setOf(INVITE_PARAMETER, ATTENDANCE_PARAMETER, ONBOARDING_PARAMETER) }) {
                return null
            }
            val inviteEntries = queryEntries.filter { it.first == INVITE_PARAMETER }
            val attendanceEntries = queryEntries.filter { it.first == ATTENDANCE_PARAMETER }
            if (inviteEntries.size > 1 || attendanceEntries.size > 1) return null
            val intentEntries = queryEntries.filter { it.first == INTENT_PARAMETER }
            if (intentEntries.size > 1) return null
            val intent = if (intentEntries.singleOrNull()?.second == DECLINE_INTENT) {
                AttendanceIntent.Decline
            } else {
                AttendanceIntent.Confirm
            }
            val inviteCodes = inviteEntries.map { it.second }.filter(::isValidInviteCode)
            val attendanceCodes = attendanceEntries.map { it.second }.filter(::isValidInviteCode)
            if (queryEntries.any { it.first == ONBOARDING_PARAMETER }) return null
            if (inviteCodes.isNotEmpty() && attendanceCodes.isNotEmpty()) return null
            if (inviteCodes.size == 1) return GroupLinkEvent.Invite(inviteCodes.single())
            if (attendanceCodes.size == 1) return GroupLinkEvent.Attendance(attendanceCodes.single(), intent)
            if (hasAttendancePath) {
                return GroupLinkEvent.Attendance(pathSegments[1], intent)
            }
            null
        }.getOrNull()

        fun directOnboardingCode(url: String?, allowedHosts: Set<String>): String? = runCatching {
            val uri = URI(url ?: return null)
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.host?.lowercase() !in allowedHosts.map(String::lowercase)) return null
            if (uri.userInfo != null || uri.port != -1) return null
            val entries = uri.rawQuery
                ?.split('&')
                ?.mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator < 0) return@mapNotNull null
                    val key = URLDecoder.decode(entry.substring(0, separator), StandardCharsets.UTF_8.name())
                    val value = URLDecoder.decode(entry.substring(separator + 1), StandardCharsets.UTF_8.name())
                    key to value
                }
                .orEmpty()
            val pathSegments = uri.path.trim('/').split('/').filter(String::isNotBlank)
            val hasAttendancePath = pathSegments.size == 2 && pathSegments[0] == ATTENDANCE_PATH_SEGMENT &&
                isValidInviteCode(pathSegments[1])
            if (hasAttendancePath && entries.any { it.first in setOf(INVITE_PARAMETER, ATTENDANCE_PARAMETER, ONBOARDING_PARAMETER) }) {
                return null
            }
            val onboardingEntries = entries.filter { it.first == ONBOARDING_PARAMETER }
            if (onboardingEntries.size != 1) return null
            val onboarding = onboardingEntries.map { it.second }.filter(::isValidInviteCode)
            val invite = entries.any { it.first == INVITE_PARAMETER }
            val attendance = entries.any { it.first == ATTENDANCE_PARAMETER }
            if (invite || attendance || onboarding.size != 1) null else onboarding.single()
        }.getOrNull()

        fun isValidInviteCode(value: String?): Boolean =
            value?.matches(Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")) == true
    }
}
