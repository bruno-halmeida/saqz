package br.com.saqz.androidapp.access

import android.app.Activity
import android.net.Uri
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.InviteCodeListener
import br.com.saqz.access.port.NativeLinkPort
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

internal class AndroidLinkAdapter(
    private val branch: AndroidBranchSessionClient,
) : NativeLinkPort {
    private var listener: InviteCodeListener? = null
    private var pendingCode: String? = null
    private var lastAcceptedCode: String? = null

    override fun start(listener: InviteCodeListener): Cancelable {
        this.listener = listener
        pendingCode?.let(listener::onInviteCode)
        pendingCode = null
        return object : Cancelable {
            override fun cancel() {
                if (this@AndroidLinkAdapter.listener === listener) {
                    this@AndroidLinkAdapter.listener = null
                }
            }
        }
    }

    fun onColdStart(url: String?) {
        accept(directInviteCode(url))
        branch.initialize(url, ::acceptBranchParameters)
    }

    fun onWarmIntent(url: String?) {
        accept(directInviteCode(url))
        branch.reinitialize(url, ::acceptBranchParameters)
    }

    private fun acceptBranchParameters(parameters: Map<String, String?>) {
        accept(parameters[INVITE_PARAMETER])
    }

    private fun accept(candidate: String?) {
        val code = candidate ?: return
        if (!isValidInviteCode(code) || code == lastAcceptedCode) return
        lastAcceptedCode = code
        val current = listener
        if (current == null) pendingCode = code else current.onInviteCode(code)
    }

    private companion object {
        const val INVITE_PARAMETER = "saqz_invite"

        fun directInviteCode(url: String?): String? = runCatching {
            val uri = URI(url ?: return null)
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            uri.rawQuery
                ?.split('&')
                ?.mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator < 0) return@mapNotNull null
                    val key = URLDecoder.decode(entry.substring(0, separator), StandardCharsets.UTF_8.name())
                    val value = URLDecoder.decode(entry.substring(separator + 1), StandardCharsets.UTF_8.name())
                    key to value
                }
                ?.lastOrNull { it.first == INVITE_PARAMETER }
                ?.second
        }.getOrNull()

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
    }
}
