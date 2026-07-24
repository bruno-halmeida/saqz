package br.com.saqz.androidapp

import android.app.Activity
import android.content.Context
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.InviteCodeListener
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import br.com.saqz.androidapp.access.AndroidIntentLinkPort
import br.com.saqz.composeapp.SaqzPlatformDependencies
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Minimal HTTP/1.1 fixture server for instrumented journeys: the real composition
 * (Ktor Android engine, real gateways) talks to 127.0.0.1 while each test scripts
 * backend behavior through [route] and asserts the recorded [requests].
 */
internal class FakeSaqzApi : Closeable {
    data class Recorded(val method: String, val path: String, val body: String)

    data class Reply(
        val status: Int,
        val body: String = "",
        val headers: Map<String, String> = emptyMap(),
    )

    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val baseUrl = "http://127.0.0.1:${server.localPort}"
    val requests = CopyOnWriteArrayList<Recorded>()

    @Volatile
    var route: (Recorded) -> Reply = { Reply(404) }

    init {
        thread(isDaemon = true, name = "fake-saqz-api") {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    return@thread
                }
                thread(isDaemon = true) { socket.use(::handle) }
            }
        }
    }

    fun count(method: String, pathPrefix: String): Int =
        requests.count { it.method == method && it.path.startsWith(pathPrefix) }

    override fun close() {
        server.close()
    }

    private fun handle(socket: Socket) {
        val input = socket.getInputStream()
        val start = readLine(input) ?: return
        val parts = start.split(" ")
        if (parts.size < 2) return
        var contentLength = 0
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator == -1) continue
            if (line.take(separator).trim().equals("Content-Length", ignoreCase = true)) {
                contentLength = line.drop(separator + 1).trim().toIntOrNull() ?: 0
            }
        }
        val body = if (contentLength > 0) readFully(input, contentLength) else ""
        val recorded = Recorded(parts[0], parts[1], body)
        requests += recorded
        val reply = route(recorded)
        val payload = reply.body.toByteArray()
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write("HTTP/1.1 ${reply.status} ${if (reply.status == 200) "OK" else "Error"}\r\n".toByteArray())
        val headers = buildMap {
            put("Content-Length", payload.size.toString())
            if (payload.isNotEmpty()) put("Content-Type", "application/json")
            put("Connection", "close")
            putAll(reply.headers)
        }
        headers.forEach { (name, value) -> output.write("$name: $value\r\n".toByteArray()) }
        output.write("\r\n".toByteArray())
        output.write(payload)
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (buffer.isEmpty()) null else buffer.toString()
            if (byte == '\n'.code) return buffer.toString().removeSuffix("\r")
            buffer.append(byte.toChar())
        }
    }

    private fun readFully(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val chunk = input.read(bytes, read, length - read)
            if (chunk == -1) break
            read += chunk
        }
        return String(bytes, 0, read)
    }
}

internal class JourneyFixture {
    val api = FakeSaqzApi()
    val auth = JourneyAuthPort()
    val links = JourneyLinkPort()
    val local = JourneyLocalState()
    val share = JourneySharePort()
}

/** Overrides the production composition and tears the fixture server down afterwards (V51). */
internal class JourneyInjectionRule(private val fixture: JourneyFixture) : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            MainActivityComposition.factoryOverride = JourneyCompositionFactory(fixture)
            try {
                base.evaluate()
            } finally {
                MainActivityComposition.factoryOverride = null
                fixture.api.close()
            }
        }
    }
}

private class JourneyCompositionFactory(
    private val fixture: JourneyFixture,
) : AndroidAppCompositionFactory {
    override fun create(
        context: Context,
        scope: CoroutineScope,
        activity: () -> Activity,
    ): AndroidAppComposition {
        val dependencies = SaqzPlatformDependencies(
            environment = "dev",
            apiBaseUrl = fixture.api.baseUrl,
            auth = fixture.auth,
            links = fixture.links,
            localState = fixture.local,
            share = fixture.share,
            attendanceShare = LifecycleAttendanceSharePort,
            groupPhotos = lifecycleGroupPhotos,
            groupLinks = fixture.links,
            groupState = fixture.local,
            groupDrafts = LifecycleGroupDraftStore,
            gameDrafts = LifecycleGameDraftStore,
            monthlyChargeDrafts = LifecycleMonthlyChargeDraftStore,
            expenseDrafts = LifecycleExpenseDraftStore,
        )
        return AndroidAppComposition(dependencies, fixture.links)
    }
}

/** Auth fixture whose tokens are valid, so authenticated gateways reach [FakeSaqzApi]. */
internal class JourneyAuthPort : NativeAuthPort {
    private var listener: AuthStateListener? = null

    override fun observe(listener: AuthStateListener): Cancelable {
        this.listener = listener
        listener.onStateChanged(AuthState.SignedOut)
        return object : Cancelable {
            override fun cancel() {
                if (this@JourneyAuthPort.listener === listener) this@JourneyAuthPort.listener = null
            }
        }
    }

    fun emit(state: AuthState) {
        listener?.onStateChanged(state)
    }

    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit

    override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit

    override fun signInWithGoogle(done: AuthCallback) = Unit

    override fun sendVerification(done: ResultCallback) = done.complete(OperationResult.Success)

    override fun reloadUser(done: AuthCallback) = done.complete(
        AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE),
    )

    override fun sendPasswordReset(email: String, done: ResultCallback) =
        done.complete(OperationResult.Success)

    override fun updateDisplayName(name: String, done: AuthCallback) = done.complete(
        AuthResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE),
    )

    override fun idToken(forceRefresh: Boolean, done: TokenCallback) =
        done.complete(TokenResult.Success("journey-fake-id-token"))

    override fun signOut(done: ResultCallback) {
        emit(AuthState.SignedOut)
        done.complete(OperationResult.Success)
    }
}

internal class JourneyLinkPort : AndroidIntentLinkPort, NativeGroupLinkPort {
    private var listener: InviteCodeListener? = null
    private val groupListeners = mutableSetOf<GroupLinkEventListener>()

    override fun start(listener: InviteCodeListener): Cancelable {
        this.listener = listener
        return object : Cancelable {
            override fun cancel() {
                if (this@JourneyLinkPort.listener === listener) this@JourneyLinkPort.listener = null
            }
        }
    }

    override fun start(listener: GroupLinkEventListener): GroupCancelable {
        groupListeners += listener
        return object : GroupCancelable {
            override fun cancel() {
                groupListeners.remove(listener)
            }
        }
    }

    override fun onColdStart(url: String?) = Unit

    override fun onWarmIntent(url: String?) = Unit

    fun emit(code: String) {
        if (groupListeners.isNotEmpty()) groupListeners.forEach { it.onEvent(GroupLinkEvent.Invite(code)) }
        else listener?.onInviteCode(code)
    }
}

internal class JourneyLocalState : LocalAccessStatePort, LocalGroupStatePort {
    var pending: String? = null
    var selected: String? = null

    override fun readSelectedGroupId(done: ValueCallback) = done.complete(ValueResult.Success(selected))

    override fun writeSelectedGroupId(value: String?, done: ResultCallback) {
        selected = value
        done.complete(OperationResult.Success)
    }

    override fun readPendingInvite(done: ValueCallback) = done.complete(ValueResult.Success(pending))

    override fun writePendingInvite(value: String?, done: ResultCallback) {
        pending = value
        done.complete(OperationResult.Success)
    }

    override fun readSelectedGroupId(done: GroupValueCallback) = done.complete(GroupValueResult.Success(selected))

    override fun writeSelectedGroupId(value: String?, done: GroupResultCallback) {
        selected = value
        done.complete(GroupOperationResult.Success)
    }

    override fun readPendingInvite(done: GroupValueCallback) = done.complete(GroupValueResult.Success(pending))

    override fun writePendingInvite(value: String?, done: GroupResultCallback) {
        pending = value
        done.complete(GroupOperationResult.Success)
    }

    override fun readPendingAttendanceLink(done: GroupValueCallback) {
        done.complete(GroupValueResult.Success(null))
    }

    override fun writePendingAttendanceLink(value: String?, done: GroupResultCallback) {
        done.complete(GroupOperationResult.Success)
    }
}

internal class JourneySharePort : NativeSharePort {
    override fun share(text: String, done: ResultCallback) = done.complete(OperationResult.Success)
}
