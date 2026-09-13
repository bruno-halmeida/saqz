package br.com.saqz.bootstrap.operations

import br.com.saqz.receivables.application.CredentialRecoveryCommand
import br.com.saqz.receivables.application.CredentialRecoveryResult
import br.com.saqz.receivables.application.RecoverFinancialCredential
import br.com.saqz.receivables.application.RecoveryCredential
import java.io.InputStream
import java.util.UUID

const val CREDENTIAL_RECOVERY_COMMAND = "recover-receivables-credential"

/** Deployment-operator command. No key is accepted in argv, environment or a web request. */
fun runCredentialRecoveryCommand(
    args: List<String>,
    readSecret: () -> CharArray? = ::readRecoverySecret,
    output: (String) -> Unit = ::println,
    execute: (CredentialRecoveryCommand, RecoveryCredential) -> CredentialRecoveryResult = { command, secret ->
        credentialRecoveryContext().use { context ->
            context.getBean(RecoverFinancialCredential::class.java).recover(command, secret)
        }
    },
): Int {
    val command = parseRecoveryCommand(args)
    if (command == null) {
        output("INVALID_ARGUMENTS: use --request-id --account-id --owner-id --creation-operation-id --operator-id --provider-account-id --wallet-id; secret via stdin/console only")
        return 2
    }
    val secret = try { readSecret() } catch (_: Exception) { null }
    if (secret == null) { output("SECRET_REQUIRED"); return 2 }
    return try {
        if (secret.isEmpty() || secret.size > MAX_SECRET_LENGTH || secret.any { it.isWhitespace() || it.isISOControl() }) {
            output("INVALID_SECRET")
            2
        } else {
            val result = execute(command, RecoveryCredential(String(secret)))
            output(result.name)
            if (result in setOf(CredentialRecoveryResult.RECOVERED, CredentialRecoveryResult.ALREADY_RECOVERED)) 0 else 1
        }
    } catch (_: Exception) {
        output("UNAVAILABLE")
        1
    } finally {
        secret.fill('\u0000')
    }
}

internal fun parseRecoveryCommand(args: List<String>): CredentialRecoveryCommand? = runCatching {
    require(args.size == REQUIRED_OPTIONS.size * 2)
    val pairs = args.chunked(2)
    require(pairs.map { it[0] }.toSet() == REQUIRED_OPTIONS)
    val values = pairs.associate { it[0] to it[1] }
    fun uuid(option: String): UUID = UUID.fromString(values.getValue(option)).also {
        require(it.toString().equals(values.getValue(option), ignoreCase = true))
    }
    val provider = values.getValue("--provider-account-id")
    val wallet = values.getValue("--wallet-id")
    require(provider.matches(REFERENCE) && wallet.matches(REFERENCE))
    CredentialRecoveryCommand(uuid("--request-id"), uuid("--account-id"), uuid("--owner-id"),
        uuid("--creation-operation-id"), uuid("--operator-id"), provider, wallet)
}.getOrNull()

private fun readRecoverySecret(): CharArray? = System.console()?.readPassword("Chave da subconta: ")
    ?: readRecoverySecret(System.`in`)

internal fun readRecoverySecret(input: InputStream): CharArray? {
    val buffer = CharArray(MAX_SECRET_LENGTH + 1)
    var size = 0
    try {
        while (size < buffer.size) {
            val next = input.read()
            if (next < 0 || next == '\n'.code) break
            if (next !in 33..126) return null
            buffer[size++] = next.toChar()
        }
        return if (size in 1..MAX_SECRET_LENGTH) buffer.copyOf(size) else null
    } finally {
        buffer.fill('\u0000')
    }
}

private const val MAX_SECRET_LENGTH = 8192
private val REFERENCE = Regex("[A-Za-z0-9_-]{1,128}")
private val REQUIRED_OPTIONS = setOf("--request-id", "--account-id", "--owner-id", "--creation-operation-id",
    "--operator-id", "--provider-account-id", "--wallet-id")
