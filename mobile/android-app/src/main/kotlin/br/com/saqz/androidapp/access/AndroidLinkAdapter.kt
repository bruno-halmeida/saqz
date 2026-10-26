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
    fun onNotificationOpen(
        groupId: String?,
        gameId: String?,
        channel: String? = null,
        pickPosition: Boolean = false,
        settleGameId: String? = null,
        settleBillingMonth: String? = null,
        paymentKind: String? = null,
    )
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

    /** Push tap: game pushes open the game, the rest the notification center; no dedup across taps. */
    override fun onNotificationOpen(
        groupId: String?,
        gameId: String?,
        channel: String?,
        pickPosition: Boolean,
        settleGameId: String?,
        settleBillingMonth: String?,
        paymentKind: String?,
    ) {
        val event = GroupLinkEvent.NotificationOpen(groupId, gameId, channel, pickPosition, settleGameId, settleBillingMonth,
            paymentKind)
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
        const val SETTLE_PARAMETER = "saqz_settle"
        const val SETTLE_PATH_SEGMENT = "acerto"

        fun directEvent(url: String?, allowedHosts: Set<String>): GroupLinkEvent? {
            val link = trustedLink(url, allowedHosts) ?: return null
            // Com sinal de acerto, só ele decide: misturado com outro código, nada abre.
            if (link.settlePathCode != null || link.query.any { it.first == SETTLE_PARAMETER }) return settleEvent(link)
            val inviteEntries = link.query.filter { it.first == INVITE_PARAMETER }
            val attendanceEntries = link.query.filter { it.first == ATTENDANCE_PARAMETER }
            val intentEntries = link.query.filter { it.first == INTENT_PARAMETER }
            val intent = if (intentEntries.singleOrNull()?.second == DECLINE_INTENT) {
                AttendanceIntent.Decline
            } else {
                AttendanceIntent.Confirm
            }
            val inviteCodes = inviteEntries.mapNotNull { normalizeInviteCode(it.second) }
            val attendanceCodes = attendanceEntries.map { it.second }.filter(::isValidInviteCode)
            val duplicated = inviteEntries.size > 1 || attendanceEntries.size > 1 || intentEntries.size > 1
            val mixed = link.query.any { it.first == ONBOARDING_PARAMETER || it.first == SETTLE_PARAMETER } ||
                (inviteCodes.isNotEmpty() && attendanceCodes.isNotEmpty())
            return when {
                duplicated || mixed -> null
                inviteCodes.size == 1 -> GroupLinkEvent.Invite(inviteCodes.single())
                attendanceCodes.size == 1 -> GroupLinkEvent.Attendance(attendanceCodes.single(), intent)
                else -> link.attendancePathCode?.let { GroupLinkEvent.Attendance(it, intent) }
            }
        }

        /**
         * Hora do acerto (FIN-013): `/acerto/<código>` ou `?saqz_settle=<código>`, sozinho. Misturado com convite,
         * presença ou onboarding é link adulterado e não abre nada.
         */
        private fun settleEvent(link: TrustedLink): GroupLinkEvent? {
            val queryCodes = link.query.filter { it.first == SETTLE_PARAMETER }.map { it.second }
            val others = link.query.any { it.first in setOf(INVITE_PARAMETER, ATTENDANCE_PARAMETER, ONBOARDING_PARAMETER) }
            val code = when {
                link.settlePathCode != null -> link.settlePathCode.takeIf { queryCodes.isEmpty() && !others }
                queryCodes.isNotEmpty() -> queryCodes.singleOrNull()?.takeIf { !others && link.attendancePathCode == null }
                else -> return null
            } ?: return null
            return GroupLinkEvent.Settle(code).takeIf { isValidSettleCode(code) }
        }

        fun isValidSettleCode(value: String?): Boolean = value?.matches(Regex("[A-Za-z0-9_-]{6,64}")) == true

        fun directOnboardingCode(url: String?, allowedHosts: Set<String>): String? {
            val link = trustedLink(url, allowedHosts) ?: return null
            val onboardingEntries = link.query.filter { it.first == ONBOARDING_PARAMETER }
            if (onboardingEntries.size != 1) return null
            val onboarding = onboardingEntries.map { it.second }.filter(::isValidInviteCode)
            val mixed = link.query.any { it.first == INVITE_PARAMETER || it.first == ATTENDANCE_PARAMETER || it.first == SETTLE_PARAMETER }
            return if (mixed || onboarding.size != 1) null else onboarding.single()
        }

        /** Único ponto que decide se uma URL é um link nosso: https, host permitido, sem userinfo/porta. */
        private fun trustedLink(url: String?, allowedHosts: Set<String>): TrustedLink? = runCatching {
            val uri = URI(url ?: return null)
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.host?.lowercase() !in allowedHosts.map(String::lowercase)) return null
            if (uri.userInfo != null || uri.port != -1) return null
            val query = uri.rawQuery
                ?.split('&')
                ?.mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator < 0) return@mapNotNull null
                    val key = URLDecoder.decode(entry.substring(0, separator), StandardCharsets.UTF_8.name())
                    val value = URLDecoder.decode(entry.substring(separator + 1), StandardCharsets.UTF_8.name())
                    key to value
                }
                .orEmpty()
            val segments = uri.path.trim('/').split('/').filter(String::isNotBlank)
            val attendancePathCode = segments.getOrNull(1)?.takeIf {
                segments.size == 2 && segments[0] == ATTENDANCE_PATH_SEGMENT && isValidInviteCode(it)
            }
            // /attendance/<código> não aceita código concorrente na query.
            val codeParameters = setOf(INVITE_PARAMETER, ATTENDANCE_PARAMETER, ONBOARDING_PARAMETER, SETTLE_PARAMETER)
            if (attendancePathCode != null && query.any { it.first in codeParameters }) return null
            val settlePathCode = segments.getOrNull(1)?.takeIf { segments.size == 2 && segments[0] == SETTLE_PATH_SEGMENT }
            TrustedLink(query, attendancePathCode, settlePathCode)
        }.getOrNull()

        fun isValidInviteCode(value: String?): Boolean =
            value?.matches(Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")) == true

        /**
         * Convite de grupo: o token longo dos links antigos (como está) ou o código curto de 8
         * caracteres (`44DF-8SA8`, aceito em minúscula e com hífen; segue adiante em maiúscula, sem hífen).
         */
        fun normalizeInviteCode(value: String?): String? {
            if (value == null) return null
            if (isValidInviteCode(value)) return value
            val short = value.filterNot { it == '-' || it.isWhitespace() }.uppercase()
            return short.takeIf { it.matches(Regex("[2-9A-HJKMNP-Z]{8}")) }
        }
    }
}

private class TrustedLink(val query: List<Pair<String, String>>, val attendancePathCode: String?, val settlePathCode: String? = null)
