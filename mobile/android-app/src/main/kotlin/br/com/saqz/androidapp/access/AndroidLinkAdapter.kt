package br.com.saqz.androidapp.access

import android.app.Activity
import android.net.Uri
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.InviteCodeListener
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.NativeGroupLinkPort
import io.branch.referral.Branch
import io.branch.referral.BranchError
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal interface AndroidBranchSessionClient {
    fun initialize(url: String?, callback: (Map<String, String?>) -> Unit)
    fun reinitialize(url: String?, callback: (Map<String, String?>) -> Unit)
}

internal interface AndroidIntentLinkPort : NativeLinkPort {
    fun onColdStart(url: String?)
    fun onWarmIntent(url: String?)
}

internal class AndroidLinkAdapter(
    private val branch: AndroidBranchSessionClient,
) : AndroidIntentLinkPort, NativeGroupLinkPort {
    private var accessListener: InviteCodeListener? = null
    private val groupListeners = mutableSetOf<GroupLinkEventListener>()
    private var pendingAccessCode: String? = null
    private var pendingGroupEvent: GroupLinkEvent? = null
    private var lastAcceptedEventKey: String? = null

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
        accept(directEvent(url))
        branch.initialize(url, ::acceptBranchParameters)
    }

    override fun onWarmIntent(url: String?) {
        accept(directEvent(url))
        branch.reinitialize(url, ::acceptBranchParameters)
    }

    private fun acceptBranchParameters(parameters: Map<String, String?>) {
        accept(branchEvent(parameters))
    }

    private fun accept(event: GroupLinkEvent?) {
        val accepted = event ?: return
        val key = when (accepted) {
            is GroupLinkEvent.Invite -> "invite:${accepted.code}"
            is GroupLinkEvent.Attendance -> "attendance:${accepted.code}"
        }
        if (key == lastAcceptedEventKey) return
        lastAcceptedEventKey = key
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
        const val ATTENDANCE_PATH_SEGMENT = "attendance"

        fun directEvent(url: String?): GroupLinkEvent? = runCatching {
            val uri = URI(url ?: return null)
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
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
            val inviteCodes = queryEntries.filter { it.first == INVITE_PARAMETER }.map { it.second }.filter(::isValidInviteCode).distinct()
            val attendanceCodes = queryEntries.filter { it.first == ATTENDANCE_PARAMETER }.map { it.second }.filter(::isValidInviteCode).distinct()
            if (inviteCodes.isNotEmpty() && attendanceCodes.isNotEmpty()) return null
            if (inviteCodes.size == 1) return GroupLinkEvent.Invite(inviteCodes.single())
            if (attendanceCodes.size == 1) return GroupLinkEvent.Attendance(attendanceCodes.single())
            val pathSegments = uri.path.trim('/').split('/').filter(String::isNotBlank)
            if (pathSegments.size == 2 && pathSegments[0] == ATTENDANCE_PATH_SEGMENT && isValidInviteCode(pathSegments[1])) {
                return GroupLinkEvent.Attendance(pathSegments[1])
            }
            null
        }.getOrNull()

        fun branchEvent(parameters: Map<String, String?>): GroupLinkEvent? {
            val invite = parameters[INVITE_PARAMETER]?.takeIf(::isValidInviteCode)
            val attendance = parameters[ATTENDANCE_PARAMETER]?.takeIf(::isValidInviteCode)
            return when {
                invite != null && attendance != null -> null
                invite != null -> GroupLinkEvent.Invite(invite)
                attendance != null -> GroupLinkEvent.Attendance(attendance)
                else -> null
            }
        }

        fun isValidInviteCode(value: String?): Boolean =
            value?.matches(Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")) == true
    }
}

internal class BranchSdkSessionClient(
    private val activity: Activity,
) : AndroidBranchSessionClient {
    override fun initialize(url: String?, callback: (Map<String, String?>) -> Unit) {
        Branch.sessionBuilder(activity)
            .withCallback(branchCallback(callback))
            .withData(url?.let(Uri::parse))
            .init()
    }

    override fun reinitialize(url: String?, callback: (Map<String, String?>) -> Unit) {
        Branch.sessionBuilder(activity)
            .withCallback(branchCallback(callback))
            .withData(url?.let(Uri::parse))
            .reInit()
    }

    private fun branchCallback(callback: (Map<String, String?>) -> Unit) =
        Branch.BranchReferralInitListener { parameters: JSONObject?, error: BranchError? ->
            if (error != null || parameters == null || !parameters.has(INVITE_PARAMETER)) {
                callback(emptyMap())
            } else {
                callback(mapOf(INVITE_PARAMETER to (parameters.opt(INVITE_PARAMETER) as? String)))
            }
        }

    private companion object {
        const val INVITE_PARAMETER = "saqz_invite"
        const val ATTENDANCE_PARAMETER = "saqz_attendance"
    }
}
